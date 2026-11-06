package com.tratoseguro.repository;

import com.tratoseguro.model.Notificacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    List<Notificacion> findTop50ByUsuarioIdOrderByIdDesc(Long usuarioId);

    List<Notificacion> findByUsuarioIdAndLeidaFalse(Long usuarioId);

    long countByUsuarioIdAndLeidaFalse(Long usuarioId);

    List<Notificacion> findByTransaccionId(Long transaccionId);
}
