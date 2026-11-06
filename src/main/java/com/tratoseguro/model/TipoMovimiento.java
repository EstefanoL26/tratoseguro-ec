package com.tratoseguro.model;

/**
 * Tipos de movimiento de dinero de un trato.
 */
public enum TipoMovimiento {
    RETENCION("Retención"),
    LIBERACION("Liberación al vendedor"),
    COMISION("Comisión de la plataforma"),
    REEMBOLSO("Reembolso al comprador");

    private final String descripcion;

    TipoMovimiento(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
