package com.example.proyecto;

/*
 * Define los posibles estados de un proceso, tal como en el libro deSistemas Operativos.
 * Usamos un 'display' para que en la GUI se vea "Listo" en vez de "LISTO".
 */
public enum EstadoProceso {
    NUEVO("Nuevo"),
    LISTO("Listo"),
    EJECUTANDO("Ejecutando"),
    ESPERANDO("Esperando"), //Este estado no se usa en la lógica actual, pero lo pide el profe en el pdf
    TERMINADO("Terminado");

    private final String display; // El texto bonito para mostrar

    EstadoProceso(String display) {
        this.display = display;
    }

    @Override
    public String toString() {
        return this.display; // JavaFX usará esto para mostrarlo en las tablas
    }
}