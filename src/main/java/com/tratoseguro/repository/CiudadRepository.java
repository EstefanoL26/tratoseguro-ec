package com.tratoseguro.repository;

import com.tratoseguro.model.Ciudad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CiudadRepository extends JpaRepository<Ciudad, Long> {

    // Ciudades de una provincia, para llenar el dropdown dependiente
    List<Ciudad> findByProvinciaIdOrderByNombreAsc(Long provinciaId);
}
