package com.tratoseguro.repository;

import com.tratoseguro.model.EstadoTransaccion;
import com.tratoseguro.model.Transaccion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TransaccionRepository extends JpaRepository<Transaccion, Long> {

    // Tratos donde el usuario participa (como vendedor o como comprador)
    @Query("select t from Transaccion t where t.vendedor.id = :usuarioId or t.comprador.id = :usuarioId order by t.id desc")
    List<Transaccion> buscarDeUsuario(@Param("usuarioId") Long usuarioId);

    List<Transaccion> findAllByOrderByIdDesc();

    // Plazos vencidos: el límite ya pasó y el trato sigue esperando
    List<Transaccion> findByEstadoAndFechaLimiteEnvioBefore(EstadoTransaccion estado, LocalDate hoy);

    List<Transaccion> findByEstadoAndFechaLimiteRevisionBefore(EstadoTransaccion estado, LocalDate hoy);

    Optional<Transaccion> findByStripeSessionId(String stripeSessionId);

    long countByEstado(EstadoTransaccion estado);

    // Reputación: tratos cerrados del vendedor y cuántos de ellos pasaron por una disputa
    @Query("select count(t) from Transaccion t where t.vendedor.id = :vendedorId "
            + "and t.estado in (com.tratoseguro.model.EstadoTransaccion.LIBERADA, com.tratoseguro.model.EstadoTransaccion.REEMBOLSADA)")
    long contarCerradosDeVendedor(@Param("vendedorId") Long vendedorId);

    @Query("select count(d) from Disputa d where d.transaccion.vendedor.id = :vendedorId and d.decision is not null")
    long contarDisputasResueltasDeVendedor(@Param("vendedorId") Long vendedorId);
}
