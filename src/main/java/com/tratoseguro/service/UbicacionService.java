package com.tratoseguro.service;

import com.tratoseguro.model.Ciudad;
import com.tratoseguro.model.Provincia;
import com.tratoseguro.repository.CiudadRepository;
import com.tratoseguro.repository.ProvinciaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Consulta los catálogos de provincias y ciudades que usan los dropdowns.
 */
@Service
public class UbicacionService {

    private final ProvinciaRepository provinciaRepository;
    private final CiudadRepository ciudadRepository;

    public UbicacionService(ProvinciaRepository provinciaRepository, CiudadRepository ciudadRepository) {
        this.provinciaRepository = provinciaRepository;
        this.ciudadRepository = ciudadRepository;
    }

    public List<Provincia> listarProvincias() {
        return provinciaRepository.findAllByOrderByNombreAsc();
    }

    public List<Ciudad> listarCiudades(Long provinciaId) {
        return ciudadRepository.findByProvinciaIdOrderByNombreAsc(provinciaId);
    }
}
