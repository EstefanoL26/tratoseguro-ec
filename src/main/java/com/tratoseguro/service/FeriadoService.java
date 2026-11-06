package com.tratoseguro.service;

import com.tratoseguro.model.Feriado;
import com.tratoseguro.repository.FeriadoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.Errors;

import java.util.List;

/**
 * Administración de feriados: alimentan el cálculo de días hábiles del core.
 */
@Service
public class FeriadoService {

    private final FeriadoRepository feriadoRepository;

    public FeriadoService(FeriadoRepository feriadoRepository) {
        this.feriadoRepository = feriadoRepository;
    }

    public List<Feriado> listar() {
        return feriadoRepository.findAllByOrderByFechaAsc();
    }

    // Devuelve false si hay errores (por ejemplo, la fecha ya está registrada)
    @Transactional
    public boolean guardar(Feriado feriado, Errors errores) {
        if (feriado.getFecha() != null && feriadoRepository.existsByFecha(feriado.getFecha())) {
            errores.rejectValue("fecha", "duplicado", "Esa fecha ya está registrada como feriado.");
        }
        if (errores.hasErrors()) {
            return false;
        }
        feriado.setNombre(feriado.getNombre().trim());
        feriadoRepository.save(feriado);
        return true;
    }

    @Transactional
    public boolean eliminar(Long id) {
        if (!feriadoRepository.existsById(id)) {
            return false;
        }
        feriadoRepository.deleteById(id);
        return true;
    }
}
