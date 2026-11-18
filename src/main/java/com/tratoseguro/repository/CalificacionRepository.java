package com.tratoseguro.repository;

import com.tratoseguro.model.Calificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CalificacionRepository extends JpaRepository<Calificacion, Long> {

    Optional<Calificacion> findByTransaccionId(Long transaccionId);

    List<Calificacion> findByVendedorIdOrderByFechaDesc(Long vendedorId);

    long countByVendedorId(Long vendedorId);

    @Query("select avg(c.puntaje) from Calificacion c where c.vendedor.id = :vendedorId")
    Double promedioDeVendedor(@Param("vendedorId") Long vendedorId);
}
