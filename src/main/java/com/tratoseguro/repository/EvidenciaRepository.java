package com.tratoseguro.repository;

import com.tratoseguro.model.Evidencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvidenciaRepository extends JpaRepository<Evidencia, Long> {

    List<Evidencia> findByDisputaIdOrderByIdAsc(Long disputaId);
}
