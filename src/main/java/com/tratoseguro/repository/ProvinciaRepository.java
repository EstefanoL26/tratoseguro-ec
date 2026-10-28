package com.tratoseguro.repository;

import com.tratoseguro.model.Provincia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProvinciaRepository extends JpaRepository<Provincia, Long> {

    List<Provincia> findAllByOrderByNombreAsc();
}
