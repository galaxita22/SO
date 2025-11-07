package com.example.proyecto;

/*
 * Esta clase es el "cerebro" de cada proceso.
 * Contiene toda la información que define a un proceso, desde su creación
 * hasta las estadísticas que generó al morir.
 */
public class Proceso {

    //Parámetros de creación (los define el usuario)
    private int pid;
    private long tiempoLlegada;  // Cuándo aparece en el sistema (tick de reloj)
    private long duracionCPU;     // Cuánto tiempo de CPU necesita en TOTAL (CPU Burst)
    private int tamanoMemoria;   // Cuánta memoria (MB) necesita

    //Parámetros de estado (los gestiona el simulador)
    private EstadoProceso estado;
    private long tiempoCPUrestante; // Cuánto tiempo de CPU le falta (disminuye con la ejecución)

    // Parámetros para estadísticas finales
    //Se inicializan en -1 para saber si ya han sido seteados o no.

    // Cuándo el proceso se movió a EJECUTANDO por PRIMERA VEZ
    private long tiempoInicioEjecucion = -1;

    // Cuándo el proceso llegó a TERMINADO */
    private long tiempoFinalizacion = -1;

    // Cuánto tiempo pasó en la cola de LISTOS (sin estar en CPU)
    private long tiempoEspera = 0;

    // Cuánto tiempo ha estado en la CPU (debería ser igual a duracionCPU al final)
    private long tiempoEnCPU = 0;

    // Tiempo de Retorno (Turnaround): Finalización - Llegada
    private long tiempoRetorno = 0;

    // Tiempo de Respuesta (Response): InicioEjecucion - Llegada
    private long tiempoRespuesta = -1;


    public Proceso(int pid, long tiempoLlegada, long duracionCPU, int tamanoMemoria) {
        this.pid = pid;
        this.tiempoLlegada = tiempoLlegada;
        this.duracionCPU = duracionCPU;
        this.tamanoMemoria = tamanoMemoria;
        this.estado = EstadoProceso.NUEVO; // Todos nacen "Nuevos"
        this.tiempoCPUrestante = duracionCPU; // Al inicio, lo que falta es el total
    }

    //Getters y Setters

    public int getPid() {
        return pid;
    }

    public void setPid(int pid) {
        this.pid = pid;
    }

    public long getTiempoLlegada() {
        return tiempoLlegada;
    }

    public void setTiempoLlegada(long tiempoLlegada) {
        this.tiempoLlegada = tiempoLlegada;
    }

    public long getDuracionCPU() {
        return duracionCPU;
    }

    public void setDuracionCPU(long duracionCPU) {
        this.duracionCPU = duracionCPU;
    }

    public int getTamanoMemoria() {
        return tamanoMemoria;
    }

    public void setTamanoMemoria(int tamanoMemoria) {
        this.tamanoMemoria = tamanoMemoria;
    }

    public long getTiempoEspera() {
        return tiempoEspera;
    }

    public void setTiempoEspera(long tiempoEspera) {
        this.tiempoEspera = tiempoEspera;
    }

    public long getTiempoRetorno() {
        return tiempoRetorno;
    }

    public void setTiempoRetorno(long tiempoRetorno) {
        this.tiempoRetorno = tiempoRetorno;
    }

    public long getTiempoRespuesta() {
        return tiempoRespuesta;
    }

    public void setTiempoRespuesta(long tiempoRespuesta) {
        this.tiempoRespuesta = tiempoRespuesta;
    }

    public long getTiempoCPUrestante() {
        return tiempoCPUrestante;
    }

    public void setTiempoCPUrestante(long tiempoCPUrestante) {
        this.tiempoCPUrestante = tiempoCPUrestante;
    }

    public EstadoProceso getEstado() {
        return estado;
    }
    public void setEstado(EstadoProceso estado) {
        this.estado = estado;
    }
    public long getTiempoFinalizacion() {
        return tiempoFinalizacion;
    }

    public void setTiempoFinalizacion(long tiempoFinalizacion) {
        this.tiempoFinalizacion = tiempoFinalizacion;
    }

    public long getTiempoInicioEjecucion() {
        return tiempoInicioEjecucion;
    }

    public void setTiempoInicioEjecucion(long tiempoInicioEjecucion) {
        this.tiempoInicioEjecucion = tiempoInicioEjecucion;
    }

    public long getTiempoEnCPU() {
        return tiempoEnCPU;
    }

    public void setTiempoEnCPU(long tiempoEnCPU) {
        this.tiempoEnCPU = tiempoEnCPU;
    }


    //Métodos lógica del proceso

    /*
     * Simula un "tick" de reloj mientras el proceso está en la CPU.
     * Reduce el tiempo que le falta y aumenta el tiempo que ha usado.
     */
    public void avanzarTiempoCPU() {
        if (this.tiempoCPUrestante > 0) {
            this.tiempoCPUrestante--;
            this.tiempoEnCPU++;
        }
    }

    /*
     * Simula un "tick" de reloj mientras el proceso está en la cola de Listos.
     * (Se llama desde el controlador cada vez que el proceso está en colaListos).
     */
    public void incrementarTiempoEspera() {
        this.tiempoEspera++;
    }

    @Override
    public String toString() {
        // Un resumen rápido para debugging o para mostrar en el campo de texto de la CPU
        return String.format("PID[%d] (Est: %s, Dur: %d, Rest: %d, Mem: %dMB)",
                pid, estado.toString(), duracionCPU, tiempoCPUrestante, tamanoMemoria);
    }
}