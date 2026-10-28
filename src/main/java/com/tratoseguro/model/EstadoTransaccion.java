package com.tratoseguro.model;

/**
 * Estados del trato según la máquina de estados del core.
 * Por ahora solo se usan CREADA y PAGADA; el resto se implementa con el core.
 */
public enum EstadoTransaccion {
    CREADA,
    PAGADA,
    ENVIADA,
    LIBERADA,
    EN_DISPUTA,
    REEMBOLSADA,
    CANCELADA
}
