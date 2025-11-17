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
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;


import javafx.scene.chart.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.scene.Scene;

import java.util.*;

/**
 * Controlador mejorado con soporte para múltiples algoritmos y políticas de reemplazo
 */
public class SimuladorController {

    // --- Enlaces a la Vista ---
    @FXML private ComboBox<String> comboModoMemoria;
    @FXML private Button btnComparar;
    @FXML private TextField txtLlegada;
    @FXML private TextField txtDuracion;
    @FXML private TextField txtMemoria;
    @FXML private Button btnCrearProceso;
    @FXML private ComboBox<String> comboAlgoritmo;
    @FXML private TextField txtQuantum;
    @FXML private ComboBox<String> comboPoliticaReemplazo;
    @FXML private Button btnIniciar;
    @FXML private Button btnDetener;
    @FXML private Label lblReloj;
    @FXML private TableView<Proceso> tablaListos;
    @FXML private TableView<Proceso> tablaNuevos;
    @FXML private TableView<Proceso> tablaEsperando;  
    @FXML private TextArea txtCPU;
    @FXML private Canvas canvasMemoria;
    @FXML private TableView<Proceso> tablaTerminados;
    @FXML private Label lblMemoriaUsada;
    @FXML private Label lblFragmentacion;

    // --- Atributos de Simulación ---
    private long reloj = 0;
    private int pidCounter = 1;
    private Timeline timeline;

    private GestorMemoria gestorMemoria;
    private List<Proceso> colaNuevos = new ArrayList<>();
    private List<Proceso> colaListos = new ArrayList<>();
    private List<Proceso> colaTerminados = new ArrayList<>();
    private Queue<Proceso> colaSwap = new LinkedList<>(); // Para swapping
    private List<Proceso> colaEsperando = new ArrayList<>(); // Para I/O

    // --- Multinúcleo ---
    private final int numNucleos = 2;
    private Proceso[] nucleos;
    private long[] tiempoOciosoNucleos;
    private int[] quantumRestanteNucleos;
    
    // --- I/O y Eventos ---
    private Map<Proceso, Long> tiemposIO = new HashMap<>(); // Proceso -> tiempo restante de I/O
    private Random random = new Random(42); // Para generar eventos I/O aleatorios

    // --- Estadísticas adicionales ---
    private long totalCambiosContexto = 0;
    private long totalSwapsRealizados = 0;

    /**
     * Inicialización del controlador
     */
    @FXML
public void initialize() {
    gestorMemoria = new GestorMemoria(2048); // 2GB = 2048 MB
    
    this.nucleos = new Proceso[numNucleos];
    this.tiempoOciosoNucleos = new long[numNucleos];
    this.quantumRestanteNucleos = new int[numNucleos];

    // Configurar ComboBox de algoritmos
    comboAlgoritmo.setItems(FXCollections.observableArrayList("SJF", "Round Robin"));
    comboAlgoritmo.setValue("SJF");
    
    // Configurar ComboBox de políticas de reemplazo
    comboPoliticaReemplazo.setItems(FXCollections.observableArrayList("FIFO", "LRU"));
    comboPoliticaReemplazo.setValue("FIFO");

    // ✅ CORRECCIÓN: Configurar ComboBox de Modo de Memoria
    comboModoMemoria.setItems(FXCollections.observableArrayList(
        "Partición Dinámica", "Paginación"));
    comboModoMemoria.setValue("Partición Dinámica");

    // Listener para cambiar modo de memoria
    comboModoMemoria.valueProperty().addListener((obs, oldVal, newVal) -> {
        if ("Paginación".equals(newVal)) {
            gestorMemoria.setModo(GestorMemoria.ModoMemoria.PAGINACION);
            mostrarInfo("Modo de Memoria", "Cambiado a: Paginación (4 MB por página)");
        } else {
            gestorMemoria.setModo(GestorMemoria.ModoMemoria.PARTICION_DINAMICA);
            mostrarInfo("Modo de Memoria", "Cambiado a: Partición Dinámica (First-Fit)");
        }
    });
    
    // Conectar política de reemplazo con el gestor
    comboPoliticaReemplazo.valueProperty().addListener((obs, oldVal, newVal) -> {
        if ("LRU".equals(newVal)) {
            gestorMemoria.setPoliticaReemplazo(GestorMemoria.PoliticaReemplazo.LRU);
            System.out.println("Política de reemplazo cambiada a: LRU");
        } else {
            gestorMemoria.setPoliticaReemplazo(GestorMemoria.PoliticaReemplazo.FIFO);
            System.out.println("Política de reemplazo cambiada a: FIFO");
        }
    });

    // Listener para habilitar/deshabilitar quantum según algoritmo
    comboAlgoritmo.valueProperty().addListener((obs, oldVal, newVal) -> {
        txtQuantum.setDisable(!"Round Robin".equals(newVal));
    });

    txtQuantum.setDisable(true);

    // Configurar columnas de tablas
    configurarTablas();
}
    /**
     * Configura las columnas de las TableView
     */
    private void configurarTablas() {
        // Tabla de Nuevos
        if (tablaNuevos != null) {
            configurarColumnasTabla(tablaNuevos);
        }
        
        // Tabla de Listos
        configurarColumnasTabla(tablaListos);
        
        // Tabla de Terminados
        configurarColumnasTablaTerminados(tablaTerminados);

        // Tabla de Esperando (I/O)
        configurarColumnasTabla(tablaEsperando);
    }

    private void configurarColumnasTabla(TableView<Proceso> tabla) {
        tabla.getColumns().clear();
        
        TableColumn<Proceso, Integer> colPid = new TableColumn<>("PID");
        colPid.setCellValueFactory(data -> 
            new javafx.beans.property.SimpleIntegerProperty(data.getValue().getPid()).asObject());
        
        TableColumn<Proceso, String> colEstado = new TableColumn<>("Estado");
        colEstado.setCellValueFactory(data -> 
            new javafx.beans.property.SimpleStringProperty(data.getValue().getEstado().toString()));
        
        TableColumn<Proceso, Long> colLlegada = new TableColumn<>("Llegada");
        colLlegada.setCellValueFactory(data -> 
            new javafx.beans.property.SimpleLongProperty(data.getValue().getTiempoLlegada()).asObject());
        
        TableColumn<Proceso, Long> colDuracion = new TableColumn<>("CPU Burst");
        colDuracion.setCellValueFactory(data -> 
            new javafx.beans.property.SimpleLongProperty(data.getValue().getDuracionCPU()).asObject());
        
        TableColumn<Proceso, Long> colRestante = new TableColumn<>("Restante");
        colRestante.setCellValueFactory(data -> 
            new javafx.beans.property.SimpleLongProperty(data.getValue().getTiempoCPUrestante()).asObject());
        
        TableColumn<Proceso, Integer> colMemoria = new TableColumn<>("Memoria (MB)");
        colMemoria.setCellValueFactory(data -> 
            new javafx.beans.property.SimpleIntegerProperty(data.getValue().getTamanoMemoria()).asObject());
        
        tabla.getColumns().addAll(colPid, colEstado, colLlegada, colDuracion, colRestante, colMemoria);
    }

    private void configurarColumnasTablaTerminados(TableView<Proceso> tabla) {
        tabla.getColumns().clear();
        
        TableColumn<Proceso, Integer> colPid = new TableColumn<>("PID");
        colPid.setCellValueFactory(data -> 
            new javafx.beans.property.SimpleIntegerProperty(data.getValue().getPid()).asObject());
        
        TableColumn<Proceso, Long> colEspera = new TableColumn<>("T. Espera");
        colEspera.setCellValueFactory(data -> 
            new javafx.beans.property.SimpleLongProperty(data.getValue().getTiempoEspera()).asObject());
        
        TableColumn<Proceso, Long> colRespuesta = new TableColumn<>("T. Respuesta");
        colRespuesta.setCellValueFactory(data -> {
            Proceso p = data.getValue();
            long respuesta = p.getTiempoInicioEjecucion() - p.getTiempoLlegada();
            return new javafx.beans.property.SimpleLongProperty(respuesta).asObject();
        });
        
        TableColumn<Proceso, Long> colRetorno = new TableColumn<>("T. Retorno");
        colRetorno.setCellValueFactory(data -> {
            Proceso p = data.getValue();
            long retorno = p.getTiempoFinalizacion() - p.getTiempoLlegada();
            return new javafx.beans.property.SimpleLongProperty(retorno).asObject();
        });
        
        tabla.getColumns().addAll(colPid, colEspera, colRespuesta, colRetorno);
    }

    @FXML
    private void handleCrearProceso() {
        try {
            long llegada = Long.parseLong(txtLlegada.getText());
            long duracion = Long.parseLong(txtDuracion.getText());
            int memoria = Integer.parseInt(txtMemoria.getText());

            if (llegada < 0 || duracion <= 0 || memoria <= 0) {
                mostrarError("Datos inválidos", "Los valores deben ser positivos");
                return;
            }

            if (memoria > gestorMemoria.tamanoTotal) {
                mostrarError("Memoria excesiva", 
                    "El proceso requiere más memoria que el total disponible (" + gestorMemoria.tamanoTotal + " MB)");
                return;
            }

            Proceso p = new Proceso(pidCounter++, llegada, duracion, memoria);
            colaNuevos.add(p);

            if (tablaNuevos != null) {
            tablaNuevos.setItems(FXCollections.observableArrayList(colaNuevos));
            tablaNuevos.refresh();
        }

            System.out.println("✓ Proceso creado: " + p);
            txtLlegada.clear();
            txtDuracion.clear();
            txtMemoria.clear();

        } catch (NumberFormatException e) {
            mostrarError("Formato inválido", "Por favor ingrese solo números");
        }
    }

    @FXML
    private void handleIniciarSimulacion() {
        if (colaNuevos.isEmpty()) {
            mostrarError("Sin procesos", "Debe crear al menos un proceso antes de iniciar");
            return;
        }

        timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> pasoSimulacion()));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();

        btnIniciar.setDisable(true);
        btnDetener.setDisable(false);
        btnCrearProceso.setDisable(true);
    }

    @FXML
    private void handleDetenerSimulacion() {
        if (timeline != null) {
            timeline.stop();
        }
        btnIniciar.setDisable(false);
        btnDetener.setDisable(true);
        btnCrearProceso.setDisable(false);

        mostrarEstadisticasFinales();
    }




// Método para mostrar comparación de algoritmos
@FXML
private void handleComparar() {
    if (colaTerminados.isEmpty()) {
        mostrarError("Sin Datos", "No hay procesos terminados para comparar.");
        return;
    }
    
    Stage ventanaGraficas = new Stage();
    ventanaGraficas.setTitle("Comparación de Algoritmos");
    
    // Crear gráfico de barras
    CategoryAxis xAxis = new CategoryAxis();
    NumberAxis yAxis = new NumberAxis();
    BarChart<String, Number> barChart = new BarChart<>(xAxis, yAxis);
    barChart.setTitle("Comparación de Tiempos por Proceso");
    xAxis.setLabel("Proceso");
    yAxis.setLabel("Tiempo (ticks)");
    
    // Series de datos
    XYChart.Series<String, Number> serieEspera = new XYChart.Series<>();
    serieEspera.setName("Tiempo de Espera");
    
    XYChart.Series<String, Number> serieRetorno = new XYChart.Series<>();
    serieRetorno.setName("Tiempo de Retorno");
    
    XYChart.Series<String, Number> serieRespuesta = new XYChart.Series<>();
    serieRespuesta.setName("Tiempo de Respuesta");
    
    for (Proceso p : colaTerminados) {
        String pid = "P" + p.getPid();
        serieEspera.getData().add(new XYChart.Data<>(pid, p.getTiempoEspera()));
        serieRetorno.getData().add(new XYChart.Data<>(pid, p.getTiempoRetorno()));
        serieRespuesta.getData().add(new XYChart.Data<>(pid, p.getTiempoRespuesta()));
    }
    
    barChart.getData().addAll(serieEspera, serieRetorno, serieRespuesta);
    
    // Calcular promedios
    double avgEspera = colaTerminados.stream()
        .mapToLong(Proceso::getTiempoEspera).average().orElse(0);
    double avgRetorno = colaTerminados.stream()
        .mapToLong(Proceso::getTiempoRetorno).average().orElse(0);
    double avgRespuesta = colaTerminados.stream()
        .mapToLong(Proceso::getTiempoRespuesta).average().orElse(0);
    
    Label lblPromedios = new Label(String.format(
        "Promedios - Espera: %.2f | Retorno: %.2f | Respuesta: %.2f",
        avgEspera, avgRetorno, avgRespuesta));
    lblPromedios.setStyle("-fx-font-size: 14px; -fx-padding: 10;");
    
    VBox vbox = new VBox(10, barChart, lblPromedios);
    Scene scene = new Scene(vbox, 800, 600);
    ventanaGraficas.setScene(scene);
    ventanaGraficas.show();
}

    /**
     * Paso principal de simulación
     */
    private void pasoSimulacion() {
        System.out.println("\n========== TICK " + reloj + " ==========");

        // FASE 1: Intentar mover procesos de NUEVO -> LISTO
        procesarNuevosLlegados();

        // FASE 2: Swapping si es necesario
        procesarSwapping();

        // FASE 3: Incrementar tiempo de espera
        for (Proceso p : colaListos) {
            p.incrementarTiempoEspera();
        }

        // Simular eventos de I/O
        simularEventosIO();

        // FASE 4: Asignar procesos a núcleos libres
        asignarProcesosANucleos();

        // FASE 5: Ejecutar procesos en CPU
        ejecutarProcesosEnCPU();

        // FASE 6: Verificar finalizaciones y desalojos
        verificarFinalizacionesYDesalojos();

        // FASE 7: Actualizar GUI
        actualizarVistasGUI();

        reloj++;
    }

    private void procesarNuevosLlegados() {
    ListIterator<Proceso> iter = colaNuevos.listIterator();
    while (iter.hasNext()) {
        Proceso p = iter.next();

        if (p.getTiempoLlegada() <= reloj) {
            System.out.println("  → Intentando asignar " + p.getTamanoMemoria() + " MB al proceso " + p.getPid());
            
            if (gestorMemoria.asignarMemoria(p)) {
                p.setEstado(EstadoProceso.LISTO);
                colaListos.add(p);
                iter.remove();
                animarAsignacionMemoria();
                
                
                int memoriaUsada = gestorMemoria.calcularMemoriaUsada();
                System.out.println("  ✓ Proceso " + p.getPid() + " movido a LISTO");
                System.out.println("  📊 Memoria usada: " + memoriaUsada + " / " + gestorMemoria.tamanoTotal + " MB");
            } else {
                System.out.println("  ⚠ Proceso " + p.getPid() + " sin memoria disponible, queda en NUEVO");
            }
        }
    }
}

    private void procesarSwapping() {
        // Implementación básica de swapping
        // Si hay procesos en swap y hay memoria libre, intentar traerlos de vuelta
        if (!colaSwap.isEmpty()) {
            Iterator<Proceso> swapIter = colaSwap.iterator();
            while (swapIter.hasNext()) {
                Proceso p = swapIter.next();
                if (gestorMemoria.asignarMemoria(p)) {
                    p.setEstado(EstadoProceso.LISTO);
                    colaListos.add(p);
                    swapIter.remove();
                    totalSwapsRealizados++;
                    System.out.println("  ↺ Swap-in: Proceso " + p.getPid() + " regresa de swap");
                    break; // Solo uno por tick para no saturar
                }
            }
        }

        // Si hay procesos nuevos que no caben y la cola de listos está muy llena,
        // podríamos hacer swap-out (esto es opcional y más avanzado)
    }

    private void asignarProcesosANucleos() {
        for (int i = 0; i < numNucleos; i++) {
            if (nucleos[i] == null && !colaListos.isEmpty()) {
                Proceso procesoSeleccionado = seleccionarProcesoSegunAlgoritmo();

                if (procesoSeleccionado != null) {
                    nucleos[i] = procesoSeleccionado;
                    procesoSeleccionado.setEstado(EstadoProceso.EJECUTANDO);

                    if ("Round Robin".equals(comboAlgoritmo.getValue())) {
                        try {
                            quantumRestanteNucleos[i] = Integer.parseInt(txtQuantum.getText());
                        } catch (NumberFormatException e) {
                            quantumRestanteNucleos[i] = 3;
                        }
                    }

                    if (procesoSeleccionado.getTiempoInicioEjecucion() == -1) {
                        procesoSeleccionado.setTiempoInicioEjecucion(reloj);
                    }

                    totalCambiosContexto++;
                    animarCambioContexto();
                    System.out.println("  ▶ Núcleo " + i + ": Inicia PID " + procesoSeleccionado.getPid());
                }
            }
        }
    }

    private Proceso seleccionarProcesoSegunAlgoritmo() {
        String algoritmo = comboAlgoritmo.getValue();
        
        switch (algoritmo) {
            case "SJF":
                return planificadorSJF();
            case "Round Robin":
                return planificadorRR();
            default:
                return planificadorRR();
        }
    }

    private Proceso planificadorSJF() {
        if (colaListos.isEmpty()) return null;

        Proceso masCorto = colaListos.get(0);
        for (Proceso p : colaListos) {
            if (p.getTiempoCPUrestante() < masCorto.getTiempoCPUrestante()) {
                masCorto = p;
            }
        }
        colaListos.remove(masCorto);
        return masCorto;
    }

    private Proceso planificadorRR() {
        if (colaListos.isEmpty()) return null;
        return colaListos.remove(0);
    }

    private void ejecutarProcesosEnCPU() {
        for (int i = 0; i < numNucleos; i++) {
            if (nucleos[i] != null) {
                nucleos[i].avanzarTiempoCPU();
                
                if ("Round Robin".equals(comboAlgoritmo.getValue())) {
                    quantumRestanteNucleos[i]--;
                }
                
                System.out.println("  ⚙ Núcleo " + i + ": PID " + nucleos[i].getPid() + 
                                 " (restante: " + nucleos[i].getTiempoCPUrestante() + ")");
            } else {
                tiempoOciosoNucleos[i]++;
            }
        }
    }

    private void verificarFinalizacionesYDesalojos() {
        for (int i = 0; i < numNucleos; i++) {
            Proceso p = nucleos[i];
            if (p == null) continue;

            // Proceso terminado
            if (p.getTiempoCPUrestante() <= 0) {
                System.out.println("  ✓ Proceso " + p.getPid() + " TERMINADO");
                p.setTiempoFinalizacion(reloj);
                p.setEstado(EstadoProceso.TERMINADO);
                gestorMemoria.liberarMemoria(p);
                colaTerminados.add(p);
                nucleos[i] = null;
                totalCambiosContexto++;
            }
            // Quantum agotado (solo RR)
            else if ("Round Robin".equals(comboAlgoritmo.getValue()) && quantumRestanteNucleos[i] <= 0) {
                System.out.println("  ⏰ Quantum agotado para PID " + p.getPid());
                p.setEstado(EstadoProceso.LISTO);
                colaListos.add(p);
                nucleos[i] = null;
                totalCambiosContexto++;
            }
        }
    }

    private void mostrarEstadisticasFinales() {
        if (colaTerminados.isEmpty()) {
            mostrarInfo("Simulación Detenida", "No hay procesos terminados para mostrar estadísticas");
            return;
        }

        double totalRetorno = 0, totalEspera = 0, totalRespuesta = 0;

        for (Proceso p : colaTerminados) {
            long retorno = p.getTiempoFinalizacion() - p.getTiempoLlegada();
            long respuesta = p.getTiempoInicioEjecucion() - p.getTiempoLlegada();
            
            totalRetorno += retorno;
            totalEspera += p.getTiempoEspera();
            totalRespuesta += respuesta;
        }

        int n = colaTerminados.size();
        double avgRetorno = totalRetorno / n;
        double avgEspera = totalEspera / n;
        double avgRespuesta = totalRespuesta / n;

        // Calcular utilización de CPU
        double tiempoTotalCPU = 0;
        for (int i = 0; i < numNucleos; i++) {
            tiempoTotalCPU += (reloj - tiempoOciosoNucleos[i]);
        }
        double utilizacionCPU = (tiempoTotalCPU / (reloj * numNucleos)) * 100;

        String stats = String.format(
            "═══════════════════════════════════════════\n" +
            "      ESTADÍSTICAS DE SIMULACIÓN\n" +
            "═══════════════════════════════════════════\n\n" +
            "Algoritmo: %s\n" +
            "Política de Reemplazo: %s\n" +
            "Tiempo Total: %d ticks\n" +
            "Procesos Terminados: %d\n\n" +
            "─── TIEMPOS PROMEDIO ───\n" +
            "• Tiempo de Retorno:    %.2f ticks\n" +
            "• Tiempo de Espera:     %.2f ticks\n" +
            "• Tiempo de Respuesta:  %.2f ticks\n\n" +
            "─── RENDIMIENTO DEL SISTEMA ───\n" +
            "• Utilización de CPU:   %.2f%%\n" +
            "• Cambios de Contexto:  %d\n" +
            "• Swaps Realizados:     %d\n" +
            "• Núcleos:              %d\n",
            comboAlgoritmo.getValue(),
            comboPoliticaReemplazo.getValue(),
            reloj,
            n,
            avgRetorno,
            avgEspera,
            avgRespuesta,
            utilizacionCPU,
            totalCambiosContexto,
            totalSwapsRealizados,
            numNucleos
        );

        Alert alerta = new Alert(Alert.AlertType.INFORMATION);
        alerta.setTitle("Simulación Completada");
        alerta.setHeaderText("Resultados Finales");
        alerta.setContentText(stats);
        alerta.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        alerta.getDialogPane().setMinWidth(500);
        alerta.showAndWait();

        System.out.println("\n" + stats);
    }

   private void actualizarVistasGUI() {
    lblReloj.setText("Reloj: " + reloj);

    // Actualizar tablas
    ObservableList<Proceso> listaEsperando = FXCollections.observableArrayList(colaEsperando);
    tablaEsperando.setItems(listaEsperando);
    tablaEsperando.refresh();

    if (tablaNuevos != null) {
        tablaNuevos.getItems().setAll(colaNuevos);
    }
    tablaListos.getItems().setAll(colaListos);
    tablaTerminados.getItems().setAll(colaTerminados);

    // Actualizar información de CPU
    if (txtCPU != null) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < numNucleos; i++) {
            if (nucleos[i] != null) {
                sb.append(String.format("Núcleo %d: PID %d (Restante: %d)\n", 
                    i, nucleos[i].getPid(), nucleos[i].getTiempoCPUrestante()));
            } else {
                sb.append(String.format("Núcleo %d: OCIOSO\n", i));
            }
        }
        txtCPU.setText(sb.toString());
    }

    
    int memoriaUsada = gestorMemoria.calcularMemoriaUsada();
    int fragExterna = gestorMemoria.calcularFragmentacionExterna();
    int fragInterna = gestorMemoria.calcularFragmentacionInterna();

    // Actualizar label de memoria usada con porcentaje
    if (lblMemoriaUsada != null) {
        lblMemoriaUsada.setText(String.format("Memoria: %d / %d MB (%.1f%%)", 
            memoriaUsada, 
            gestorMemoria.tamanoTotal, 
            (memoriaUsada * 100.0) / gestorMemoria.tamanoTotal));
    }

    // Actualizar label de fragmentación según el modo
    if (lblFragmentacion != null) {
        if (comboModoMemoria != null && "Paginación".equals(comboModoMemoria.getValue())) {
            lblFragmentacion.setText(String.format(
                "Fragmentación - Externa: %d MB | Interna: %d MB", 
                fragExterna, fragInterna));
        } else {
            lblFragmentacion.setText(String.format(
                "Fragmentación Externa: %d MB", fragExterna));
        }
    }

    dibujarMemoria();
}

    private void dibujarMemoria() {
        GraphicsContext gc = canvasMemoria.getGraphicsContext2D();
        gc.clearRect(0, 0, canvasMemoria.getWidth(), canvasMemoria.getHeight());

        List<GestorMemoria.BloqueMemoria> bloques = gestorMemoria.getTodosLosBloques();
        double canvasWidth = canvasMemoria.getWidth();
        double canvasHeight = canvasMemoria.getHeight();

        if (bloques.isEmpty()) return;

        double posX = 0;
        for (GestorMemoria.BloqueMemoria bloque : bloques) {
            double ancho = (bloque.getTamano() / (double) gestorMemoria.tamanoTotal) * canvasWidth;

            if (bloque.isOcupado()) {
                gc.setFill(Color.web("#4CAF50")); // Verde para ocupado
                gc.fillRect(posX, 0, ancho, canvasHeight);
                gc.setStroke(Color.web("#2E7D32"));
                gc.strokeRect(posX, 0, ancho, canvasHeight);
                
                gc.setFill(Color.WHITE);
                gc.fillText("P" + bloque.pidProceso, posX + 5, canvasHeight / 2);
            } else {
                gc.setFill(Color.web("#E0E0E0")); // Gris para libre
                gc.fillRect(posX, 0, ancho, canvasHeight);
                gc.setStroke(Color.web("#9E9E9E"));
                gc.strokeRect(posX, 0, ancho, canvasHeight);
                
                gc.setFill(Color.web("#424242"));
                if (ancho > 30) {
                    gc.fillText("Libre", posX + 5, canvasHeight / 2);
                }
            }
            posX += ancho;
        }
    }

    private void mostrarError(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }

    private void mostrarInfo(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }

    private void animarCambioContexto() {
    FadeTransition fade = new FadeTransition(Duration.millis(200), txtCPU);
    fade.setFromValue(1.0);
    fade.setToValue(0.3);
    fade.setCycleCount(2);
    fade.setAutoReverse(true);
    fade.play();
}

// Método para animar asignación de memoria
private void animarAsignacionMemoria() {
    ScaleTransition scale = new ScaleTransition(Duration.millis(300), canvasMemoria);
    scale.setFromX(0.98);
    scale.setFromY(0.98);
    scale.setToX(1.0);
    scale.setToY(1.0);
    scale.setCycleCount(1);
    scale.play();
}

    private void simularEventosIO() {
    // 1. Generar eventos de I/O aleatorios (10% probabilidad)
    for (int i = 0; i < numNucleos; i++) {
        if (nucleos[i] != null && random.nextDouble() < 0.1) {
            Proceso p = nucleos[i];
            // Solo si ha ejecutado al menos 2 ticks
            if (p.getTiempoEnCPU() >= 2) {
                p.setEstado(EstadoProceso.ESPERANDO);
                long tiempoIO = (long)(random.nextInt(5) + 3); // 3-7 ticks
                tiemposIO.put(p, tiempoIO);
                colaEsperando.add(p);
                nucleos[i] = null;
                totalCambiosContexto++;
                System.out.println(String.format(
                    "  ⏸ [CPU %d] Proceso P%d -> I/O (%d ticks)", 
                    i, p.getPid(), tiempoIO));
            }
        }
    }
    
    // 2. Procesar finalización de operaciones I/O
    Iterator<Proceso> iter = colaEsperando.iterator();
    while (iter.hasNext()) {
        Proceso p = iter.next();
        long tiempoRestante = tiemposIO.get(p) - 1;
        
        if (tiempoRestante <= 0) {
            // I/O completado
            p.setEstado(EstadoProceso.LISTO);
            colaListos.add(p);
            iter.remove();
            tiemposIO.remove(p);
            System.out.println(String.format(
                "  ▶ Proceso P%d retorna de I/O -> Cola de Listos", 
                p.getPid()));
        } else {
            tiemposIO.put(p, tiempoRestante);
        }
    }
}

}