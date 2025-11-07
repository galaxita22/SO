package com.example.proyecto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;

/*
 * Esta clase maneja la memoria RAM de 2GB.
 * Decide dónde poner un proceso (asignación) y qué hacer cuando se va (liberación).
 * Utiliza una estrategia de Partición Dinámica con el algoritmo First-Fit.
 */
public class GestorMemoria {

    /*
     * Clase interna que representa un "pedazo" de memoria.
     * Puede estar libre (pidProceso = null) u ocupado (pidProceso = ID).
     */
    static class BloqueMemoria {
        int id; // ID único del bloque (pal debug)
        int inicio; // Dónde empieza (en MB)
        int tamano; // Cuántos MB ocupa
        Integer pidProceso; // null si está libre, o el PID del proceso que lo ocupa

        BloqueMemoria(int id, int inicio, int tamano, Integer pidProceso) {
            this.id = id;
            this.inicio = inicio;
            this.tamano = tamano;
            this.pidProceso = pidProceso;
        }

        public boolean isOcupado() {
            return pidProceso != null;
        }

        // Getters/Setters necesarios para la lógica de fusión
        public int getInicio() { return inicio; }
        public int getTamano() { return tamano; }
        public void setTamano(int tamano) { this.tamano = tamano; }

        @Override
        public String toString() {
            return String.format("Bloque %d (Inicio: %d, Tam: %dMB, PID: %s)",
                    id, inicio, tamano, isOcupado() ? pidProceso : "Libre");
        }
    }

    // --- Atributos del GestorMemoria ---
    final int tamanoTotal; // en MB
    private List<BloqueMemoria> bloquesLibres;
    private List<BloqueMemoria> bloquesOcupados;
    private int proximoIdBloque = 0; // Contador para los IDs de bloques

    public GestorMemoria(int tamanoTotalMB) {
        this.tamanoTotal = tamanoTotalMB;
        this.bloquesOcupados = new ArrayList<>();
        this.bloquesLibres = new ArrayList<>();

        // La memoria empieza como un único, gigantesco bloque libre.
        this.bloquesLibres.add(new BloqueMemoria(proximoIdBloque++, 0, tamanoTotalMB, null));
        System.out.println("Memoria inicializada: " + tamanoTotalMB + " MB");
    }

    /*
     * Intenta asignar memoria a un proceso usando la estrategia First-Fit.
     * First-Fit: Busca en la lista de bloques libres y usa el *primero* que encuentre
     * donde quepa el proceso.
     */
    public boolean asignarMemoria(Proceso proceso) {
        int tamanoRequerido = proceso.getTamanoMemoria();
        System.out.println("Intentando asignar " + tamanoRequerido + "MB al proceso " + proceso.getPid());

        // Usamos ListIterator porque nos permite modificar la lista (eliminar/actualizar bloques)
        // mientras la recorremos. Es más seguro que un 'for-each'.
        ListIterator<BloqueMemoria> iter = bloquesLibres.listIterator();

        while (iter.hasNext()) {
            BloqueMemoria bloqueLibre = iter.next();

            // ¿Cabe el proceso en este bloque libre?
            if (bloqueLibre.getTamano() >= tamanoRequerido) {
                System.out.println("Bloque encontrado: " + bloqueLibre.getTamano() + "MB en posición " + bloqueLibre.getInicio());

                //Crear el nuevo bloque que SÍ estará ocupado
                BloqueMemoria nuevoBloqueOcupado = new BloqueMemoria(
                        proximoIdBloque++,
                        bloqueLibre.getInicio(), // Empieza donde empezaba el libre
                        tamanoRequerido,         // Con el tamaño que necesita el proceso
                        proceso.getPid()         // Asociado a este PID
                );
                bloquesOcupados.add(nuevoBloqueOcupado);

                //Achicar o eliminar el bloque libre original
                if (bloqueLibre.getTamano() == tamanoRequerido) {
                    // Si cabía justo, el bloque libre desaparece.
                    System.out.println("Bloque usado completamente, eliminando de libres");
                    iter.remove(); // iter.remove() elimina el bloque 'bloqueLibre'
                } else {
                    // Si el bloque libre era más grande, lo achicamos.
                    // Esto crea "fragmentación interna" (en el bloque, aunque aquí es externa)
                    System.out.println("Fragmentando bloque: quedan " + (bloqueLibre.getTamano() - tamanoRequerido) + "MB libres");
                    bloqueLibre.inicio += tamanoRequerido; // El inicio se "corre"
                    bloqueLibre.tamano -= tamanoRequerido; // El tamaño se reduce
                }

                System.out.println("Memoria asignada exitosamente al proceso " + proceso.getPid());
                return true;
            }
        }

        // Si salimos del bucle sin retornar 'true', es que no encontramos espacio.
        System.out.println("No hay memoria suficiente (contigua) para el proceso " + proceso.getPid());
        return false;
    }

    /*
     * Libera la memoria que estaba usando un proceso cuando termina.
     * Busca todos los bloques asociados a ese PID y los convierte en bloques libres.
     */
    public void liberarMemoria(Proceso proceso) {
        System.out.println("Liberando memoria del proceso " + proceso.getPid());
        List<BloqueMemoria> bloquesRecienLiberados = new ArrayList<>();

        //Encontrar los bloques del proceso en la lista de OCUPADOS
        ListIterator<BloqueMemoria> iter = bloquesOcupados.listIterator();
        while (iter.hasNext()) {
            BloqueMemoria bloqueOcupado = iter.next();
            if (bloqueOcupado.pidProceso == proceso.getPid()) {
                // Lo encontramos. Lo sacamos de "Ocupados"...
                iter.remove();
                //lo guardamos para convertirlo en "Libre"
                bloquesRecienLiberados.add(bloqueOcupado);
            }
        }

        //Convertir esos bloques a "libres"
        for (BloqueMemoria bloque : bloquesRecienLiberados) {
            System.out.println("Liberando bloque: " + bloque.getTamano() + "MB en posición " + bloque.getInicio());
            // Lo añadimos a la lista de libres, marcando el PID como null
            bloquesLibres.add(new BloqueMemoria(
                    bloque.id, // Reusamos el ID (o podríamos crear uno nuevo)
                    bloque.getInicio(),
                    bloque.getTamano(),
                    null // ¡Libre!
            ));
        }

        // Fusionar bloques libres que quedaron juntos
        // Si liberamos [A] y [B] y estaban [Libre][A][B][Libre], ahora tenemos
        // [Libre][Libre][Libre][Libre]. Hay que unirlos.
        System.out.println("Fusionando bloques libres adyacentes...");
        this.fusionarBloquesLibres();
    }

    /*
     * Limpia la lista de bloques libres juntando "huecos" adyacentes.
     * [Libre 10MB] [Libre 20MB] -> [Libre 30MB]
     */
    private void fusionarBloquesLibres() {
        if (bloquesLibres.size() <= 1) {
            return; // No hay nada que fusionar
        }

        //Ordenar SÍ O SÍ por la dirección de inicio.
        // Si no, no podemos saber si son adyacentes.
        bloquesLibres.sort(Comparator.comparingInt(BloqueMemoria::getInicio));

        List<BloqueMemoria> bloquesFusionados = new ArrayList<>();
        // Empezamos con el primer bloque como nuestro "bloque actual" a comparar
        BloqueMemoria bloqueActual = bloquesLibres.get(0);

        // Recorremos a partir del segundo
        for (int i = 1; i < bloquesLibres.size(); i++) {
            BloqueMemoria siguienteBloque = bloquesLibres.get(i);

            // La condición mágica: ¿El final de 'actual' es el inicio de 'siguiente'?
            if (bloqueActual.getInicio() + bloqueActual.getTamano() == siguienteBloque.getInicio()) {
                // ¡Son adyacentes!
                System.out.println("Fusionando bloques: " + bloqueActual.getTamano() + "MB + " + siguienteBloque.getTamano() + "MB");
                // Hacemos 'actual' más grande para que incluya a 'siguiente'
                bloqueActual.setTamano(bloqueActual.getTamano() + siguienteBloque.getTamano());
                // NO añadimos 'actual' a la lista nueva todavía, porque podría
                // fusionarse también con el que viene después.
            } else {
                // No son adyacentes. 'bloqueActual' está "terminado".
                // Lo añadimos a la lista de resultado...
                bloquesFusionados.add(bloqueActual);
                // ...y el 'siguiente' se convierte en el nuevo 'actual' para comparar.
                bloqueActual = siguienteBloque;
            }
        }

        // Al final del bucle, el último 'bloqueActual' (sea fusionado o no)
        // nunca se añadió a la lista. Lo añadimos ahora.
        bloquesFusionados.add(bloqueActual);

        // Reemplazamos la lista antigua por la nueva lista fusionada.
        this.bloquesLibres = bloquesFusionados;
        System.out.println("Fusión completada, " + this.bloquesLibres.size() + " bloques libres resultantes");
    }

    /*
     * Metodo extra para la GUI.
     * Devuelve una lista con todos los bloques (libres y ocupados)
     * para que el Canvas (la barrita de memoria) pueda dibujarlos en orden.
     */
    public List<BloqueMemoria> getTodosLosBloques() {
        List<BloqueMemoria> todos = new ArrayList<>(bloquesOcupados);
        todos.addAll(bloquesLibres);
        // Los ordenamos por inicio para que el Canvas los dibuje en orden (de izq a der)
        todos.sort(Comparator.comparingInt(BloqueMemoria::getInicio));
        return todos;
    }
}