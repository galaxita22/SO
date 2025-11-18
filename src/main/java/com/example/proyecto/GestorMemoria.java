package com.example.proyecto;

import java.util.*;

/**
 * Gestor de memoria mejorado con soporte para: - Partición Dinámica (First-Fit)
 * - Paginación Simple - Políticas de reemplazo: FIFO y LRU
 */
public class GestorMemoria {

    // Configuración de paginación
    private static final int TAMANO_PAGINA = 4; // 4 MB por página
    private final int numPaginasTotal;

    // Estructuras para partición dinámica
    static class BloqueMemoria {

        int id;
        int inicio;
        int tamano;
        Integer pidProceso;

        BloqueMemoria(int id, int inicio, int tamano, Integer pidProceso) {
            this.id = id;
            this.inicio = inicio;
            this.tamano = tamano;
            this.pidProceso = pidProceso;
        }

        public boolean isOcupado() {
            return pidProceso != null;
        }

        public int getInicio() {
            return inicio;
        }

        public int getTamano() {
            return tamano;
        }

        public void setTamano(int tamano) {
            this.tamano = tamano;
        }

        @Override
        public String toString() {
            return String.format("Bloque %d (Inicio: %d, Tam: %dMB, PID: %s)",
                    id, inicio, tamano, isOcupado() ? pidProceso : "Libre");
        }
    }

    // Estructuras para paginación
    static class Pagina {

        int numeroPagina;
        Integer pidProceso; // null si está libre
        long ultimoAcceso; // Para LRU
        long tiempoAsignacion; // Para FIFO

        Pagina(int numeroPagina) {
            this.numeroPagina = numeroPagina;
            this.pidProceso = null;
            this.ultimoAcceso = 0;
            this.tiempoAsignacion = 0;
        }

        public boolean isLibre() {
            return pidProceso == null;
        }
    }

    // Atributos principales
    final int tamanoTotal;
    private List<BloqueMemoria> bloquesLibres;
    private List<BloqueMemoria> bloquesOcupados;
    private int proximoIdBloque = 0;

    // Sistema de paginación
    private Pagina[] tablaPaginas;
    private Map<Integer, List<Integer>> tablaPaginasPorProceso; // PID -> Lista de números de página
    private long contadorTiempo = 0; // Para FIFO y LRU

    // Modo de operación
    public enum ModoMemoria {
        PARTICION_DINAMICA,
        PAGINACION
    }
    private ModoMemoria modo = ModoMemoria.PARTICION_DINAMICA;

    // Política de reemplazo
    public enum PoliticaReemplazo {
        FIFO,
        LRU
    }
    private PoliticaReemplazo politicaReemplazo = PoliticaReemplazo.FIFO;

    /**
     * Constructor principal
     */
    public GestorMemoria(int tamanoTotalMB) {
        this.tamanoTotal = tamanoTotalMB;
        this.bloquesOcupados = new ArrayList<>();
        this.bloquesLibres = new ArrayList<>();
        this.bloquesLibres.add(new BloqueMemoria(proximoIdBloque++, 0, tamanoTotalMB, null));

        // Inicializar sistema de paginación
        this.numPaginasTotal = tamanoTotalMB / TAMANO_PAGINA;
        this.tablaPaginas = new Pagina[numPaginasTotal];
        this.tablaPaginasPorProceso = new HashMap<>();

        for (int i = 0; i < numPaginasTotal; i++) {
            tablaPaginas[i] = new Pagina(i);
        }

        System.out.println("Memoria inicializada: " + tamanoTotalMB + " MB");
        System.out.println("Páginas totales: " + numPaginasTotal + " (" + TAMANO_PAGINA + " MB c/u)");
    }

    /**
     * Cambia el modo de gestión de memoria
     */
    public void setModo(ModoMemoria modo) {
        this.modo = modo;
        System.out.println("Modo de memoria cambiado a: " + modo);
    }

    /**
     * Cambia la política de reemplazo
     */
    public void setPoliticaReemplazo(PoliticaReemplazo politica) {
        this.politicaReemplazo = politica;
        System.out.println("Política de reemplazo cambiada a: " + politica);
    }

    /**
     * Asigna memoria a un proceso según el modo configurado
     */
    public boolean asignarMemoria(Proceso proceso) {
        if (modo == ModoMemoria.PAGINACION) {
            return asignarMemoriaPaginacion(proceso);
        } else {
            return asignarMemoriaParticionDinamica(proceso);
        }
    }

    /**
     * Asignación con partición dinámica (First-Fit)
     */
    private boolean asignarMemoriaParticionDinamica(Proceso proceso) {
        int tamanoRequerido = proceso.getTamanoMemoria();
        System.out.println("Asignando " + tamanoRequerido + "MB al proceso " + proceso.getPid() + " (Partición Dinámica)");

        ListIterator<BloqueMemoria> iter = bloquesLibres.listIterator();

        while (iter.hasNext()) {
            BloqueMemoria bloqueLibre = iter.next();

            if (bloqueLibre.getTamano() >= tamanoRequerido) {
                BloqueMemoria nuevoBloqueOcupado = new BloqueMemoria(
                        proximoIdBloque++,
                        bloqueLibre.getInicio(),
                        tamanoRequerido,
                        proceso.getPid()
                );
                bloquesOcupados.add(nuevoBloqueOcupado);

                if (bloqueLibre.getTamano() == tamanoRequerido) {
                    iter.remove();
                } else {
                    bloqueLibre.inicio += tamanoRequerido;
                    bloqueLibre.tamano -= tamanoRequerido;
                }

                System.out.println("Memoria asignada exitosamente");
                return true;
            }
        }

        System.out.println("No hay memoria contigua suficiente");
        return false;
    }

    /**
     * Asignación con paginación
     */
    private boolean asignarMemoriaPaginacion(Proceso proceso) {
        int tamanoRequerido = proceso.getTamanoMemoria();
        int paginasNecesarias = (int) Math.ceil(tamanoRequerido / (double) TAMANO_PAGINA);

        System.out.println("Asignando " + tamanoRequerido + "MB al proceso " + proceso.getPid()
                + " (Paginación: " + paginasNecesarias + " páginas)");

        // Buscar páginas libres
        List<Integer> paginasLibres = new ArrayList<>();
        for (int i = 0; i < tablaPaginas.length; i++) {
            if (tablaPaginas[i].isLibre()) {
                paginasLibres.add(i);
                if (paginasLibres.size() == paginasNecesarias) {
                    break;
                }
            }
        }

        // Si no hay suficientes páginas libres, aplicar política de reemplazo
        if (paginasLibres.size() < paginasNecesarias) {
            int paginasALiberar = paginasNecesarias - paginasLibres.size();
            List<Integer> paginasReemplazadas = aplicarPoliticaReemplazo(paginasALiberar);

            if (paginasReemplazadas != null) {
                paginasLibres.addAll(paginasReemplazadas);
                System.out.println("Reemplazo aplicado: " + paginasALiberar + " páginas liberadas");
            } else {
                System.out.println("No se pudo aplicar reemplazo");
                return false;
            }
        }

        // Asignar las páginas al proceso
        List<Integer> paginasAsignadas = new ArrayList<>();
        for (int i = 0; i < paginasNecesarias; i++) {
            int numPagina = paginasLibres.get(i);
            tablaPaginas[numPagina].pidProceso = proceso.getPid();
            tablaPaginas[numPagina].tiempoAsignacion = contadorTiempo++;
            tablaPaginas[numPagina].ultimoAcceso = contadorTiempo;
            paginasAsignadas.add(numPagina);
        }

        tablaPaginasPorProceso.put(proceso.getPid(), paginasAsignadas);
        System.out.println("Páginas asignadas: " + paginasAsignadas);
        return true;
    }

    /**
     * Aplica la política de reemplazo seleccionada
     */
    private List<Integer> aplicarPoliticaReemplazo(int cantidad) {
        if (politicaReemplazo == PoliticaReemplazo.FIFO) {
            return aplicarFIFO(cantidad);
        } else {
            return aplicarLRU(cantidad);
        }
    }

    /**
     * Política FIFO: Reemplaza las páginas más antiguas
     */
    private List<Integer> aplicarFIFO(int cantidad) {
        System.out.println("Aplicando FIFO para liberar " + cantidad + " páginas...");

        List<Pagina> paginasOcupadas = new ArrayList<>();
        for (Pagina p : tablaPaginas) {
            if (!p.isLibre()) {
                paginasOcupadas.add(p);
            }
        }

        if (paginasOcupadas.size() < cantidad) {
            return null; // No hay suficientes páginas para reemplazar
        }

        // Ordenar por tiempo de asignación (más antiguas primero)
        paginasOcupadas.sort(Comparator.comparingLong(p -> p.tiempoAsignacion));

        List<Integer> paginasLiberadas = new ArrayList<>();
        for (int i = 0; i < cantidad; i++) {
            Pagina p = paginasOcupadas.get(i);
            int pid = p.pidProceso;

            // Remover de la tabla del proceso
            tablaPaginasPorProceso.get(pid).remove(Integer.valueOf(p.numeroPagina));

            // Liberar la página
            p.pidProceso = null;
            paginasLiberadas.add(p.numeroPagina);

            System.out.println("FIFO: Página " + p.numeroPagina + " (PID " + pid + ") reemplazada");
        }

        return paginasLiberadas;
    }

    /**
     * Política LRU: Reemplaza las páginas menos recientemente usadas
     */
    private List<Integer> aplicarLRU(int cantidad) {
        System.out.println("Aplicando LRU para liberar " + cantidad + " páginas...");

        List<Pagina> paginasOcupadas = new ArrayList<>();
        for (Pagina p : tablaPaginas) {
            if (!p.isLibre()) {
                paginasOcupadas.add(p);
            }
        }

        if (paginasOcupadas.size() < cantidad) {
            return null;
        }

        // Ordenar por último acceso (menos recientes primero)
        paginasOcupadas.sort(Comparator.comparingLong(p -> p.ultimoAcceso));

        List<Integer> paginasLiberadas = new ArrayList<>();
        for (int i = 0; i < cantidad; i++) {
            Pagina p = paginasOcupadas.get(i);
            int pid = p.pidProceso;

            tablaPaginasPorProceso.get(pid).remove(Integer.valueOf(p.numeroPagina));
            p.pidProceso = null;
            paginasLiberadas.add(p.numeroPagina);

            System.out.println("LRU: Página " + p.numeroPagina + " (PID " + pid + ") reemplazada");
        }

        return paginasLiberadas;
    }

    /**
     * Simula un acceso a memoria (actualiza LRU)
     */
    public void accederMemoria(Proceso proceso) {
        if (modo == ModoMemoria.PAGINACION && tablaPaginasPorProceso.containsKey(proceso.getPid())) {
            List<Integer> paginas = tablaPaginasPorProceso.get(proceso.getPid());
            for (Integer numPagina : paginas) {
                tablaPaginas[numPagina].ultimoAcceso = contadorTiempo++;
            }
        }
    }

    /**
     * Retorna todos los bloques de memoria para visualización
     */
    public List<BloqueMemoria> getTodosLosBloques() {
        if (modo == ModoMemoria.PAGINACION) {
            List<BloqueMemoria> bloques = new ArrayList<>();
            int i = 0;

            for (Pagina paginaActual : tablaPaginas) {
                // Crea un bloque visual individual para CADA página de 4MB
                BloqueMemoria bloque = new BloqueMemoria(
                        i, // id
                        i * TAMANO_PAGINA, // inicio
                        TAMANO_PAGINA, // tamaño (por ahora 4MB fijo)
                        //TODO: dejar que el usuario decida el tamaño de página al seleccionar modo paginación
                        paginaActual.pidProceso // pidProceso (puede ser null si está libre)
                );
                bloques.add(bloque);
                i++;
            }

            // System.out.println("Bloques visuales generados (Paginación): " + bloques.size());
            return bloques;

        } else {
            // Combinar bloques libres y ocupados ordenados
            List<BloqueMemoria> todos = new ArrayList<>();
            todos.addAll(bloquesOcupados);
            todos.addAll(bloquesLibres);
            todos.sort(Comparator.comparingInt(b -> b.inicio));

            System.out.println("Bloques (Partición Dinámica): " + todos.size());
            return todos;
        }
    }

    /**
     * Libera la memoria de un proceso
     */
    public void liberarMemoria(Proceso proceso) {
        if (modo == ModoMemoria.PAGINACION) {
            liberarMemoriaPaginacion(proceso);
        } else {
            liberarMemoriaParticionDinamica(proceso);
        }
    }

    private void liberarMemoriaParticionDinamica(Proceso proceso) {
        System.out.println("Liberando memoria del proceso " + proceso.getPid());
        List<BloqueMemoria> bloquesRecienLiberados = new ArrayList<>();

        ListIterator<BloqueMemoria> iter = bloquesOcupados.listIterator();
        while (iter.hasNext()) {
            BloqueMemoria bloqueOcupado = iter.next();
            if (bloqueOcupado.pidProceso == proceso.getPid()) {
                iter.remove();
                bloquesRecienLiberados.add(bloqueOcupado);
            }
        }

        for (BloqueMemoria bloque : bloquesRecienLiberados) {
            bloquesLibres.add(new BloqueMemoria(
                    bloque.id,
                    bloque.getInicio(),
                    bloque.getTamano(),
                    null
            ));
        }

        fusionarBloquesLibres();
    }

    private void liberarMemoriaPaginacion(Proceso proceso) {
        System.out.println("Liberando páginas del proceso " + proceso.getPid());

        if (!tablaPaginasPorProceso.containsKey(proceso.getPid())) {
            return;
        }

        List<Integer> paginas = tablaPaginasPorProceso.get(proceso.getPid());
        for (Integer numPagina : paginas) {
            tablaPaginas[numPagina].pidProceso = null;
        }

        tablaPaginasPorProceso.remove(proceso.getPid());
        System.out.println(paginas.size() + " páginas liberadas");
    }

    private void fusionarBloquesLibres() {
        if (bloquesLibres.size() <= 1) {
            return;
        }

        bloquesLibres.sort(Comparator.comparingInt(BloqueMemoria::getInicio));

        List<BloqueMemoria> bloquesFusionados = new ArrayList<>();
        BloqueMemoria bloqueActual = bloquesLibres.get(0);

        for (int i = 1; i < bloquesLibres.size(); i++) {
            BloqueMemoria siguienteBloque = bloquesLibres.get(i);

            if (bloqueActual.getInicio() + bloqueActual.getTamano() == siguienteBloque.getInicio()) {
                bloqueActual.setTamano(bloqueActual.getTamano() + siguienteBloque.getTamano());
            } else {
                bloquesFusionados.add(bloqueActual);
                bloqueActual = siguienteBloque;
            }
        }

        bloquesFusionados.add(bloqueActual);
        this.bloquesLibres = bloquesFusionados;
    }

    /**
     * Métodos de información y estadísticas
     */
    // En GestorMemoria.java
    public int calcularMemoriaUsada() {
        if (modo == ModoMemoria.PAGINACION) {
            // Contar páginas ocupadas usando !isLibre()
            int paginasOcupadas = 0;
            for (Pagina p : tablaPaginas) {
                if (!p.isLibre()) {  // Usar isLibre() en lugar de p.ocupada
                    paginasOcupadas++;
                }
            }
            return paginasOcupadas * TAMANO_PAGINA;
        } else {
            // Sumar bloques ocupados
            int suma = 0;
            for (BloqueMemoria b : bloquesOcupados) {
                suma += b.getTamano();
            }
            return suma;
        }
    }

    public int calcularFragmentacionExterna() {
        if (modo == ModoMemoria.PAGINACION) {
            return 0; // La paginación elimina la fragmentación externa
        }

        int totalLibre = bloquesLibres.stream()
                .mapToInt(BloqueMemoria::getTamano)
                .sum();

        int bloqueLibreMasGrande = bloquesLibres.stream()
                .mapToInt(BloqueMemoria::getTamano)
                .max()
                .orElse(0);

        return totalLibre - bloqueLibreMasGrande;
    }

    /**
     * Calcula la fragmentación interna (solo para paginación) La fragmentación
     * interna ocurre en la última página de cada proceso
     */
    public int calcularFragmentacionInterna() {
        if (modo != ModoMemoria.PAGINACION) {
            return 0; // No hay fragmentación interna en partición dinámica
        }

        int fragmentacionTotal = 0;

        for (Map.Entry<Integer, List<Integer>> entry : tablaPaginasPorProceso.entrySet()) {
            int pid = entry.getKey();
            List<Integer> paginas = entry.getValue();

            if (paginas.isEmpty()) {
                continue;
            }

            // Calcular cuánta memoria realmente usa el proceso
            // (esto requeriría almacenar el tamaño original, por simplicidad asumimos peor caso)
            // La última página probablemente tiene fragmentación
            // Fragmentación máxima por proceso = TAMANO_PAGINA - 1 (en el peor caso)
            // Para ser más preciso, necesitaríamos el tamaño exacto del proceso
            // Estimación conservadora: 50% del tamaño de página por proceso
            fragmentacionTotal += (TAMANO_PAGINA / 2);
        }

        return fragmentacionTotal;
    }

    public Map<String, Object> getEstadisticas() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("modo", modo);
        stats.put("politica", politicaReemplazo);
        stats.put("memoriaTotal", tamanoTotal);
        stats.put("memoriaUsada", calcularMemoriaUsada());
        stats.put("memoriaLibre", tamanoTotal - calcularMemoriaUsada());
        stats.put("fragmentacionExterna", calcularFragmentacionExterna());
        stats.put("fragmentacionInterna", calcularFragmentacionInterna());

        if (modo == ModoMemoria.PAGINACION) {
            int paginasLibres = 0;
            for (Pagina p : tablaPaginas) {
                if (p.isLibre()) {
                    paginasLibres++;
                }
            }
            stats.put("paginasLibres", paginasLibres);
            stats.put("paginasUsadas", numPaginasTotal - paginasLibres);
            stats.put("tamanoPagina", TAMANO_PAGINA);
        }

        return stats;
    }
}
