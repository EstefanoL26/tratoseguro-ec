package com.tratoseguro.repository;

import com.tratoseguro.model.Envio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EnvioRepository extends JpaRepository<Envio, Long> {

    Optional<Envio> findByTransaccionId(Long transaccionId);
}
