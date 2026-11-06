package com.tratoseguro.service;

/**
 * Se lanza cuando una operación viola una regla del negocio
 * (por ejemplo, una transición de estado que no está permitida).
 * El mensaje está en español y se muestra tal cual al usuario.
 */
public class ReglaNegocioException extends RuntimeException {

    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
