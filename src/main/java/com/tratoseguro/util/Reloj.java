package com.tratoseguro.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Fecha y hora en la zona de Ecuador. El servidor de Render trabaja en UTC,
 * así que sin esto los plazos podrían cambiar de día a las 7 de la noche.
 */
public final class Reloj {

    public static final ZoneId ZONA_ECUADOR = ZoneId.of("America/Guayaquil");

    private Reloj() {
    }

    public static LocalDateTime ahora() {
        return LocalDateTime.now(ZONA_ECUADOR);
    }

    public static LocalDate hoy() {
        return LocalDate.now(ZONA_ECUADOR);
    }
}
