package com.tratoseguro.service;

import com.tratoseguro.model.Feriado;
import com.tratoseguro.repository.FeriadoRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Calcula plazos en días hábiles: se saltan sábados, domingos y los feriados registrados.
 *
 * Ejemplo del documento de análisis:
 *   pago el jueves 01/10/2026 + 5 días hábiles  = 08/10/2026 (límite de envío)
 *   envío el 05/10/2026 + (5 + 3) días hábiles  = 16/10/2026 (se salta el feriado del 9 de octubre)
 */
@Service
public class DiasHabilesService {

    private final FeriadoRepository feriadoRepository;

    public DiasHabilesService(FeriadoRepository feriadoRepository) {
        this.feriadoRepository = feriadoRepository;
    }

    // Cuenta "dias" días hábiles a partir del día siguiente a "inicio"
    public LocalDate sumarDiasHabiles(LocalDate inicio, int dias) {
        Set<LocalDate> feriados = fechasFeriado();
        LocalDate fecha = inicio;
        int contados = 0;
        while (contados < dias) {
            fecha = fecha.plusDays(1);
            if (esHabil(fecha, feriados)) {
                contados++;
            }
        }
        return fecha;
    }

    public boolean esHabil(LocalDate fecha) {
        return esHabil(fecha, fechasFeriado());
    }

    private boolean esHabil(LocalDate fecha, Set<LocalDate> feriados) {
        DayOfWeek dia = fecha.getDayOfWeek();
        return dia != DayOfWeek.SATURDAY && dia != DayOfWeek.SUNDAY && !feriados.contains(fecha);
    }

    private Set<LocalDate> fechasFeriado() {
        return feriadoRepository.findAll().stream()
                .map(Feriado::getFecha)
                .collect(Collectors.toSet());
    }
}
