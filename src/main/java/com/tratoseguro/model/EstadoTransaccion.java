package com.tratoseguro.model;

import java.util.Set;

/**
 * Estados del trato y las transiciones permitidas (máquina de estados del core).
 * Cualquier cambio que no esté en esta tabla se rechaza en el servidor.
 *
 *   CREADA     -> PAGADA (pagar) | CANCELADA (cancelar)
 *   PAGADA     -> ENVIADA (registrar envío) | EN_DISPUTA (abrir disputa) | REEMBOLSADA (vence el límite de envío)
 *   ENVIADA    -> LIBERADA (confirmar recepción o vence la revisión) | EN_DISPUTA (abrir disputa)
 *   EN_DISPUTA -> LIBERADA (pago al vendedor) | REEMBOLSADA (reembolso total, parcial o vía rápida)
 *   LIBERADA, REEMBOLSADA y CANCELADA son estados finales.
 */
public enum EstadoTransaccion {
    CREADA("Creada"),
    PAGADA("Pagada"),
    ENVIADA("Enviada"),
    LIBERADA("Liberada"),
    EN_DISPUTA("En disputa"),
    REEMBOLSADA("Reembolsada"),
    CANCELADA("Cancelada");

    private final String descripcion;

    EstadoTransaccion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public boolean esFinal() {
        return this == LIBERADA || this == REEMBOLSADA || this == CANCELADA;
    }

    public boolean puedeCambiarA(EstadoTransaccion nuevo) {
        return siguientesPermitidos().contains(nuevo);
    }

    public Set<EstadoTransaccion> siguientesPermitidos() {
        switch (this) {
            case CREADA:
                return Set.of(PAGADA, CANCELADA);
            case PAGADA:
                return Set.of(ENVIADA, EN_DISPUTA, REEMBOLSADA);
            case ENVIADA:
                return Set.of(LIBERADA, EN_DISPUTA);
            case EN_DISPUTA:
                return Set.of(LIBERADA, REEMBOLSADA);
            default:
                return Set.of();
        }
    }
}
