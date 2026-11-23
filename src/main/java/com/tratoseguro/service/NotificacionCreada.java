package com.tratoseguro.service;

/**
 * Evento que se publica cada vez que se guarda una notificación interna.
 * Con él se mandan los avisos fuera de la app (correo y push) cuando la base ya guardó el cambio.
 */
public record NotificacionCreada(Long usuarioId, String nombre, String email, String mensaje,
                                 Long transaccionId, String producto) {
}
