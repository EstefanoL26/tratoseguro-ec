package com.tratoseguro.repository;

import com.tratoseguro.model.Movimiento;
import com.tratoseguro.model.TipoMovimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface MovimientoRepository extends JpaRepository<Movimiento, Long> {

    List<Movimiento> findByTransaccionIdOrderByIdAsc(Long transaccionId);

    List<Movimiento> findTop50ByOrderByIdDesc();

    // Suma de todos los movimientos de un tipo (para el panel de finanzas)
    @Query("select coalesce(sum(m.monto), 0) from Movimiento m where m.tipo = :tipo")
    BigDecimal sumarPorTipo(@Param("tipo") TipoMovimiento tipo);
}
