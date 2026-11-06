package com.tratoseguro.repository;

import com.tratoseguro.model.Feriado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface FeriadoRepository extends JpaRepository<Feriado, Long> {

    List<Feriado> findAllByOrderByFechaAsc();

    boolean existsByFecha(LocalDate fecha);
}
