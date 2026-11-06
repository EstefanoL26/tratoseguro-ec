package com.tratoseguro.model;

/**
 * Decisiones que puede tomar el mediador al resolver una disputa.
 */
public enum Decision {
    REEMBOLSO_TOTAL("Reembolso total al comprador"),
    REEMBOLSO_PARCIAL("Reembolso parcial al comprador"),
    PAGO_AL_VENDEDOR("Pago al vendedor");

    private final String descripcion;

    Decision(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
