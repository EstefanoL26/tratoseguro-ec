package com.tratoseguro.service;

import com.tratoseguro.model.Liquidacion;
import com.tratoseguro.model.Movimiento;
import com.tratoseguro.model.TipoMovimiento;
import com.tratoseguro.model.Transaccion;
import com.tratoseguro.repository.MovimientoRepository;
import com.tratoseguro.util.Reloj;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Reparte el dinero retenido de un trato.
 *
 *   M = monto retenido, R = reembolso (0 <= R <= M), comisión de la plataforma = 3 %
 *   base     = M - R
 *   comisión = base x 0.03   (2 decimales, HALF_UP)
 *   neto     = base - comisión
 *
 * Regla de cuadre: R + comisión + neto == M. Si no se cumple, se rechaza la operación
 * y el trato no cambia de estado.
 */
@Service
public class LiquidacionService {

    public static final BigDecimal PORCENTAJE_COMISION = new BigDecimal("0.03");

    private final MovimientoRepository movimientoRepository;

    public LiquidacionService(MovimientoRepository movimientoRepository) {
        this.movimientoRepository = movimientoRepository;
    }

    // Solo calcula (no guarda nada). La usa también la vista previa del mediador.
    public Liquidacion calcular(BigDecimal monto, BigDecimal reembolso) {
        BigDecimal m = monto.setScale(2, RoundingMode.HALF_UP);
        BigDecimal r = (reembolso == null ? BigDecimal.ZERO : reembolso).setScale(2, RoundingMode.HALF_UP);

        if (r.compareTo(BigDecimal.ZERO) < 0 || r.compareTo(m) > 0) {
            throw new ReglaNegocioException("El reembolso debe estar entre $ 0,00 y el monto retenido ($ " + m + ").");
        }

        BigDecimal base = m.subtract(r);
        BigDecimal comision = base.multiply(PORCENTAJE_COMISION).setScale(2, RoundingMode.HALF_UP);
        BigDecimal neto = base.subtract(comision);

        Liquidacion liquidacion = new Liquidacion(m, r, comision, neto);
        if (!liquidacion.isCuadra()) {
            throw new ReglaNegocioException("La liquidación no cuadra: " + liquidacion.getSuma() + " no es igual a " + m + ".");
        }
        return liquidacion;
    }

    // Calcula, guarda el resultado en el trato y registra los movimientos de dinero
    @Transactional
    public Liquidacion liquidar(Transaccion transaccion, BigDecimal reembolso, String referenciaReembolso) {
        Liquidacion liquidacion = calcular(transaccion.getMonto(), reembolso);
        LocalDateTime ahora = Reloj.ahora();

        transaccion.setReembolso(liquidacion.getReembolso());
        transaccion.setComision(liquidacion.getComision());
        transaccion.setMontoVendedor(liquidacion.getNetoVendedor());

        if (liquidacion.getReembolso().signum() > 0) {
            registrarMovimiento(transaccion, TipoMovimiento.REEMBOLSO, liquidacion.getReembolso(), ahora, referenciaReembolso);
        }
        if (liquidacion.getComision().signum() > 0) {
            registrarMovimiento(transaccion, TipoMovimiento.COMISION, liquidacion.getComision(), ahora, null);
        }
        if (liquidacion.getNetoVendedor().signum() > 0) {
            registrarMovimiento(transaccion, TipoMovimiento.LIBERACION, liquidacion.getNetoVendedor(), ahora, null);
        }
        return liquidacion;
    }

    public List<Movimiento> movimientos(Long transaccionId) {
        return movimientoRepository.findByTransaccionIdOrderByIdAsc(transaccionId);
    }

    @Transactional
    public void registrarMovimiento(Transaccion transaccion, TipoMovimiento tipo, BigDecimal monto,
                                    LocalDateTime fecha, String referencia) {
        movimientoRepository.save(new Movimiento(transaccion, tipo, monto, fecha, referencia));
    }
}
