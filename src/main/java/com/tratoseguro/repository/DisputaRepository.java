package com.tratoseguro.repository;

import com.tratoseguro.model.Disputa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DisputaRepository extends JpaRepository<Disputa, Long> {

    Optional<Disputa> findByTransaccionId(Long transaccionId);

    // Pendientes primero (sin decisión), luego las resueltas
    List<Disputa> findByDecisionIsNullOrderByFechaAperturaAsc();

    List<Disputa> findByDecisionIsNotNullOrderByFechaResolucionDesc();
}
