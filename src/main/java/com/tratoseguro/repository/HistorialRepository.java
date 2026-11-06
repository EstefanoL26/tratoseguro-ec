package com.tratoseguro.repository;

import com.tratoseguro.model.Historial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistorialRepository extends JpaRepository<Historial, Long> {

    List<Historial> findByTransaccionIdOrderByIdAsc(Long transaccionId);
}
