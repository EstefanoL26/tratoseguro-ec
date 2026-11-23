package com.tratoseguro.repository;

import com.tratoseguro.model.PushSuscripcion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PushSuscripcionRepository extends JpaRepository<PushSuscripcion, Long> {

    List<PushSuscripcion> findByUsuarioId(Long usuarioId);

    Optional<PushSuscripcion> findByEndpoint(String endpoint);

    long countByUsuarioId(Long usuarioId);
}
