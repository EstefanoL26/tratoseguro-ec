package com.tratoseguro.model;

/**
 * Cómo se pagó el trato. Los dos son de prueba: no se cobra dinero real.
 * SIMULADO: la plataforma registra el pago sin pasarela.
 * STRIPE: el comprador paga en Stripe Checkout con tarjetas de prueba (modo test).
 */
public enum MetodoPago {
    SIMULADO("Pago simulado"),
    STRIPE("Stripe (modo de prueba)");

    private final String descripcion;

    MetodoPago(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
