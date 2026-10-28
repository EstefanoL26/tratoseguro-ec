package com.tratoseguro.repository;

import com.tratoseguro.model.Transaccion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransaccionRepository extends JpaRepository<Transaccion, Long> {

    List<Transaccion> findByProductoContainingIgnoreCase(String producto);
}
