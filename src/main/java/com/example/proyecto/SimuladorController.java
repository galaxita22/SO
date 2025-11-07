package com.example.proyecto;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/**
 * El Controlador. Esta clase es el "director de orquesta".
 * Conecta los botones de la vista (FXML) con la lógica de simulación
 * y los modelos de datos (Proceso, GestorMemoria).
 */
public class SimuladorController {

    // --- Enlaces a la Vista (elementos del hello-view.fxml) ---
    // JavaFX "inyecta" los componentes aquí gracias a la anotación @FXML
    @FXML private TextField txtLlegada;
    @FXML private TextField txtDuracion;
    @FXML private TextField txtMemoria;
    @FXML private Button btnCrearProceso;
    @FXML private RadioButton radioSJF;
    @FXML private RadioButton radioRR;
    @FXML private ToggleGroup algoritmoGroup; // Para agrupar los RadioButtons
    @FXML private TextField txtQuantum;
    @FXML private Button btnIniciar;
    @FXML private Button btnDetener;
    @FXML private Label lblReloj;
    @FXML private TableView<Proceso> tablaListos;
    @FXML private TextField txtCPU; // Debería ser un TextArea si son varios núcleos
    @FXML private Canvas canvasMemoria; // La barra gráfica de memoria
    @FXML private TableView<Proceso> tablaTerminados;

    // --- Atributos de Simulación ---
    private long reloj = 0; // El "tick" global del sistema
    private int pidCounter = 1; // Para asignar PIDs únicos (1, 2, 3...)
    private Timeline timeline; // El "corazón" que llama a pasoSimulacion() cada segundo

    private GestorMemoria gestorMemoria;
    private List<Proceso> colaNuevos = new ArrayList<>(); // Procesos creados que no han llegado
    private List<Proceso> colaListos = new ArrayList<>(); // Procesos que llegaron, tienen memoria y esperan CPU
    private List<Proceso> colaTerminados = new ArrayList<>(); // Procesos que ya finalizaron

    //--- Lógicas de multiprocesamiento (Multinúcleo) ---
    // El PDF pide 2 o más núcleos [cite: 63]
    private final int numNucleos = 2;
    /** Un array que representa los núcleos. 'nucleos[i] = null' significa núcleo ocioso. */
    private Proceso[] nucleos;
    /** Para estadísticas: cuánto tiempo ha estado ocioso cada núcleo */
    private long[] tiempoOciosoNucleos;
    /** Solo para RR: cuánto 'tiempo de ráfaga' le queda al proceso en el núcleo i */
    private int[] quantumRestanteNucleos;

    /**
     * Se llama automáticamente DESPUÉS de cargar el FXML.
     * Es el lugar perfecto para inicializar todo.
     */
    @FXML
    public void initialize() {
        // Inicializamos la memoria con 2GB (2048 MB) [cite: 63]
        gestorMemoria = new GestorMemoria(2048);

        // Inicializamos los arrays de núcleos
        this.nucleos = new Proceso[numNucleos]; // Array de 2 (o más) 'nulls'
        this.tiempoOciosoNucleos = new long[numNucleos]; // Array de 2 (o más) 'ceros'
        this.quantumRestanteNucleos = new int[numNucleos];

        // --- ¡¡IMPORTANTE!! ---
        // --- AQUÍ SE DEBEN CONFIGURAR LAS COLUMNAS DE LAS TableView ---
        // Si no haces esto, las tablas 'tablaListos' y 'tablaTerminados'
        // NUNCA mostrarán nada, aunque les pases datos.
        // Tienes que crear TableColumn y usar PropertyValueFactory
        // para enlazar "PID" con el método getPid() de la clase Proceso.
        // (Esto es de JavaFX, no de S.O.)
    }

    /**
     * Se llama cuando el usuario hace clic en el botón "Crear Proceso".
     * Lee los campos de texto y añade el proceso a la cola de Nuevos.
     */
    @FXML
    private void handleCrearProceso() {
        try {
            long llegada = Long.parseLong(txtLlegada.getText());
            long duracion = Long.parseLong(txtDuracion.getText());
            int memoria = Integer.parseInt(txtMemoria.getText());

            if (llegada < 0 || duracion <= 0 || memoria <= 0) {
                // Validar que no pongan tonterías
                System.err.println("Datos de entrada inválidos (negativos o cero).");
                return;
            }

            Proceso p = new Proceso(pidCounter++, llegada, duracion, memoria);
            colaNuevos.add(p); // Lo añadimos a la "sala de espera" inicial

            System.out.println("Proceso creado: " + p);
            // Limpiar campos para el siguiente
            txtLlegada.clear();
            txtDuracion.clear();
            txtMemoria.clear();

        } catch (NumberFormatException e) {
            // Si el usuario escribe "hola" en vez de "5"
            System.err.println("Datos de entrada inválidos. Use solo números.");
            // (Idealmente, mostrar una alerta bonita al usuario)
        }
    }

    /**
     * Se llama al pulsar "Iniciar Simulación".
     * Configura el 'Timeline' (bucle principal) para que llame
     * a `pasoSimulacion()` cada segundo.
     */
    @FXML
    private void handleIniciarSimulacion() {
        // Se ejecutará `pasoSimulacion()` cada 1 segundo (1000 ms)
        timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> pasoSimulacion()));
        timeline.setCycleCount(Timeline.INDEFINITE); // Para que se repita infinitamente
        timeline.play();

        // Deshabilitamos botones para evitar caos
        btnIniciar.setDisable(true);
        btnDetener.setDisable(false);
    }

    /**
     * Se llama al pulsar "Detener Simulación".
     * Detiene el bucle 'Timeline' y muestra las estadísticas finales.
     */
    @FXML
    private void handleDetenerSimulacion() {
        if (timeline != null) {
            timeline.stop();
        }
        btnIniciar.setDisable(false);
        btnDetener.setDisable(true);

        // --- HORA DE MOSTRAR LOS RESULTADOS ---
        mostrarEstadisticasFinales();
    }

    /**
     * Calcula las estadísticas PROMEDIO pedidas en el PDF
     * y las muestra en una ventana emergente.
     */
    private void mostrarEstadisticasFinales() {
        if (colaTerminados.isEmpty()) {
            System.out.println("Simulación detenida. No terminaron procesos.");
            return;
        }

        // 1. Calcular Tiempos Totales
        double totalTiempoRetorno = 0;
        double totalTiempoEspera = 0;
        double totalTiempoRespuesta = 0;

        // Recorremos los procesos que SÍ terminaron
        for (Proceso p : colaTerminados) {
            // Estos cálculos son la definición de las métricas
            long tiempoRetorno = p.getTiempoFinalizacion() - p.getTiempoLlegada();
            long tiempoRespuesta = p.getTiempoInicioEjecucion() - p.getTiempoLlegada();

            // (El tiempo de espera ya lo calculamos en pasoSimulacion)
            totalTiempoRetorno += tiempoRetorno;
            totalTiempoEspera += p.getTiempoEspera();
            totalTiempoRespuesta += tiempoRespuesta;
        }

        // 2. Calcular Promedios
        int numTerminados = colaTerminados.size();
        double avgTiempoRetorno = totalTiempoRetorno / numTerminados;
        double avgTiempoEspera = totalTiempoEspera / numTerminados;
        double avgTiempoRespuesta = totalTiempoRespuesta / numTerminados;

        // 3. Mostrar en un Pop-up (Alerta de JavaFX)
        String stats = String.format(
                "Simulación Completada (Algoritmo: %s)\n\n" +
                        "Procesos Terminados: %d\n" +
                        "Tiempo Total: %d ticks\n\n" +
                        "--- Promedios ---\n" +
                        "Tiempo de Retorno (Turnaround): %.2f ticks\n" +
                        "Tiempo de Espera (Wait): %.2f ticks\n" +
                        "Tiempo de Respuesta (Response): %.2f ticks",
                radioSJF.isSelected() ? "SJF" : "RR",
                numTerminados,
                reloj,
                avgTiempoRetorno,
                avgTiempoEspera,
                avgTiempoRespuesta
        );

        Alert alerta = new Alert(Alert.AlertType.INFORMATION);
        alerta.setTitle("Estadísticas de Simulación");
        alerta.setHeaderText("Resultados de la Ejecución");
        alerta.setContentText(stats);
        // (Ajustar para que el texto quepa bien)
        alerta.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        alerta.showAndWait();

        System.out.println(stats); // También en consola, por si acaso
    }

    /**
     * Es UN "tick" del reloj. Esta es la función MÁS IMPORTANTE.
     * Mueve procesos entre colas, ejecuta la CPU y actualiza la GUI.
     */
    private void pasoSimulacion() {
        System.out.println("------------------ Reloj: " + reloj + " ------------------");

        // --- FASE 1: Transición de NUEVO -> LISTO ---
        // Revisamos la cola de procesos 'Nuevos' (los recién creados)
        ListIterator<Proceso> iter = colaNuevos.listIterator();
        while (iter.hasNext()) {
            Proceso p = iter.next();

            // ¿Ya es hora de que este proceso "llegue" al sistema?
            if (p.getTiempoLlegada() <= reloj) {
                // Sí. Ahora, intentamos asignarle memoria.
                if (gestorMemoria.asignarMemoria(p)) {
                    // ¡Hay memoria! El proceso pasa a la cola de Listos.
                    p.setEstado(EstadoProceso.LISTO);
                    colaListos.add(p);
                    iter.remove(); // Lo sacamos de la cola de Nuevos
                    System.out.println("Proceso " + p.getPid() + " movido a cola de listos (Memoria OK)");
                } else {
                    // No hay memoria. El proceso debe esperar en la cola de Nuevos.
                    // Volverá a intentarlo en el siguiente tick.
                    System.out.println("No hay memoria suficiente para el proceso " + p.getPid() + ". Sigue en 'Nuevos'.");
                }
            }
        }

        // Todos los procesos que están en la cola de Listos envejecen un tick.
        // (Esto es para calcular el Tiempo de Espera)
        for (Proceso p : colaListos) {
            p.incrementarTiempoEspera();
        }

        // Transición de LISTO -> EJECUTANDO
        // Llenamos todos los núcleos que estén libres.
        for (int i = 0; i < numNucleos; i++) {
            if (nucleos[i] == null && !colaListos.isEmpty()) {
                Proceso procesoSeleccionado;

                //Aplicar algoritmo de planificación seleccionado
                if (radioSJF.isSelected()) {
                    procesoSeleccionado = planificadorSJF();
                } else {
                    procesoSeleccionado = planificadorRR();
                }

                if (procesoSeleccionado != null) {
                    //Asignar proceso al núcleo 'i'
                    nucleos[i] = procesoSeleccionado;
                    procesoSeleccionado.setEstado(EstadoProceso.EJECUTANDO);

                    // Si es Round Robin, se resetea el quantum
                    if (radioRR.isSelected()) {
                        try {
                            quantumRestanteNucleos[i] = Integer.parseInt(txtQuantum.getText());
                        } catch (NumberFormatException e) {
                            quantumRestanteNucleos[i] = 3; // Valor por defecto si el quantum es inválido
                        }
                    }

                    // Si es la PRIMERA vez que se ejecuta, se guarda el tick
                    if (procesoSeleccionado.getTiempoInicioEjecucion() == -1) {
                        procesoSeleccionado.setTiempoInicioEjecucion(reloj);
                    }
                }
            }
        }

        //Ejecución en los núcleos
        for (int i = 0; i < numNucleos; i++) {
            if (nucleos[i] != null) {
                // Si hay un proceso, avanza su tiempo de CPU
                nucleos[i].avanzarTiempoCPU();

                // Si es RR consume quantum
                if (radioRR.isSelected()) {
                    quantumRestanteNucleos[i]--;
                }
                System.out.println(String.format("Núcleo %d: Ejecutando PID %d (Restante: %d)", i, nucleos[i].getPid(), nucleos[i].getTiempoCPUrestante()));
            } else {
                // Si no hay proceso, el núcleo está ocioso
                tiempoOciosoNucleos[i]++;
                System.out.println(String.format("Núcleo %d: Ocioso", i));
            }
        }

        // Revisamos si algún proceso terminó o fue desalojado (por Quantum)
        for (int i = 0; i < numNucleos; i++) {
            Proceso p = nucleos[i];

            if (p == null) {
                continue; // Núcleo vacío, nada que hacer aquí
            }

            //El proceso terminó (tiempoCPUrestante = 0)
            if (p.getTiempoCPUrestante() <= 0) {
                System.out.println("Proceso " + p.getPid() + " TERMINADO.");
                p.setTiempoFinalizacion(reloj); // Guardamos cuándo terminó
                p.setEstado(EstadoProceso.TERMINADO);
                gestorMemoria.liberarMemoria(p); // ¡Devuelve la memoria!
                colaTerminados.add(p);
                nucleos[i] = null; // El núcleo queda libre

                //El proceso NO terminó, pero se le acabó el quantum
            } else if (radioRR.isSelected() && quantumRestanteNucleos[i] <= 0) {
                System.out.println("QUANTUM AGOTADO para PID: " + p.getPid());
                p.setEstado(EstadoProceso.LISTO);
                colaListos.add(p); // Regresa al final de la cola de Listos
                nucleos[i] = null; // Libera el núcleo
            }
        }

        actualizarVistasGUI();

        reloj++;
    }

    /*
     * Planificador SJF (Shortest Job First) - NO apropiativo.
     * Busca en TODA la cola de listos y devuelve el que tenga
     * el 'tiempoCPUrestante' más corto.
     */
    private Proceso planificadorSJF() {
        if (colaListos.isEmpty()) {
            return null;
        }

        Proceso masCorto = colaListos.get(0);
        for (Proceso p : colaListos) {
            // Comparamos por el tiempo RESTANTE, no el total (eso sería SRTF)
            if (p.getTiempoCPUrestante() < masCorto.getTiempoCPUrestante()) {
                masCorto = p;
            }
        }
        colaListos.remove(masCorto); // Lo sacamos de la cola
        return masCorto;
    }

    /*
     * Planificador RR (Round Robin).
     * Es el más simple: solo toma el PRIMERO de la cola (FIFO).
     */
    private Proceso planificadorRR() {
        if (colaListos.isEmpty()) {
            return null;
        }
        // .remove(0) o .removeFirst() quita el primer elemento y lo devuelve
        return colaListos.removeFirst();
    }

    /*
     * Actualiza TODOS los componentes visuales de la GUI
     * para reflejar el estado actual de la simulación.
     */
    private void actualizarVistasGUI() {
        lblReloj.setText("Reloj Global: " + reloj);

        //TO DO: Optimizar esto usando ObservableList
        tablaListos.getItems().setAll(colaListos);
        tablaTerminados.getItems().setAll(colaTerminados);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < numNucleos; i++) {
            if (nucleos[i] != null) {
                // Mostramos el proceso que está en el núcleo 'i'
                sb.append(String.format("Núcleo %d: %s\n", i, nucleos[i]));
            } else {
                sb.append(String.format("Núcleo %d: Ocioso\n", i));
            }
        }
        txtCPU.setText(sb.toString()); // (Si txtCPU es un TextField, solo mostrará la primera línea. CAMBIAR A TextArea)

        GraphicsContext gc = canvasMemoria.getGraphicsContext2D();
        // Borramos el dibujo anterior
        gc.clearRect(0, 0, canvasMemoria.getWidth(), canvasMemoria.getHeight());

        //Obtiene la lista de bloques de memoria
        List<GestorMemoria.BloqueMemoria> bloques = gestorMemoria.getTodosLosBloques();
        double canvasWidth = canvasMemoria.getWidth();
        double canvasHeight = canvasMemoria.getHeight();

        if (bloques.isEmpty()) return; // Nada que dibujar

        // Posición X actual para dibujar el siguiente bloque
        double posXActual = 0;
        for (GestorMemoria.BloqueMemoria bloque : bloques) {
            // Calcula el porcentaje total que representa este bloque para escalarlo al ancho del canvas
            double tamanoMB = bloque.getTamano();
            double anchoBloque = (tamanoMB / gestorMemoria.tamanoTotal) * canvasWidth;

            if (bloque.isOcupado()) {
                gc.setFill(Color.LIGHTBLUE); // Ocupado
                gc.fillRect(posXActual, 0, anchoBloque, canvasHeight);
                gc.setFill(Color.BLACK);
                gc.fillText("PID " + bloque.pidProceso, posXActual + 5, canvasHeight / 2);
            } else {
                gc.setFill(Color.LIGHTGRAY); // Libre
                gc.fillRect(posXActual, 0, anchoBloque, canvasHeight);
                gc.setFill(Color.BLACK);
                gc.fillText("Libre", posXActual + 5, canvasHeight / 2);
            }
            posXActual += anchoBloque; // Mueve el cursor X para el siguiente bloque
        }
    }
}