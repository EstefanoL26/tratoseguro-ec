package com.tratoseguro.repository;

import com.tratoseguro.model.Movimiento;
import com.tratoseguro.model.TipoMovimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface MovimientoRepository extends JpaRepository<Movimiento, Long> {

    List<Movimiento> findByTransaccionIdOrderByIdAsc(Long transaccionId);

    List<Movimiento> findTop50ByOrderByIdDesc();

    // Suma de todos los movimientos de un tipo (para el panel de finanzas)
    @Query("select coalesce(sum(m.monto), 0) from Movimiento m where m.tipo = :tipo")
    BigDecimal sumarPorTipo(@Param("tipo") TipoMovimiento tipo);

    // Movimientos desde una fecha (para los gráficos mensuales de finanzas)
    List<Movimiento> findByFechaGreaterThanEqualOrderByFechaAsc(LocalDateTime desde);

    // Todos los movimientos, del más antiguo al más reciente (exportación CSV)
    List<Movimiento> findAllByOrderByIdAsc();

    // Liberaciones con la transferencia al vendedor pendiente
    List<Movimiento> findByTipoAndReferenciaOrderByIdAsc(TipoMovimiento tipo, String referencia);

    // Liberaciones de un vendedor (pantalla "Mis cobros")
    @Query("select m from Movimiento m where m.tipo = com.tratoseguro.model.TipoMovimiento.LIBERACION "
            + "and m.transaccion.vendedor.id = :vendedorId order by m.id desc")
    List<Movimiento> liberacionesDeVendedor(@Param("vendedorId") Long vendedorId);
}
