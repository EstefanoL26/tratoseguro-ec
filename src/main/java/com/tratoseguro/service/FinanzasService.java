package com.tratoseguro.service;

import com.tratoseguro.model.EstadoTransaccion;
import com.tratoseguro.model.Movimiento;
import com.tratoseguro.model.TipoMovimiento;
import com.tratoseguro.model.Transaccion;
import com.tratoseguro.repository.MovimientoRepository;
import com.tratoseguro.repository.TransaccionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Panel de finanzas: cuánto dinero está retenido y cuánto se ha liberado, cobrado o reembolsado.
 */
@Service
public class FinanzasService {

    private final TransaccionRepository transaccionRepository;
    private final MovimientoRepository movimientoRepository;

    public FinanzasService(TransaccionRepository transaccionRepository, MovimientoRepository movimientoRepository) {
        this.transaccionRepository = transaccionRepository;
        this.movimientoRepository = movimientoRepository;
    }

    // Dinero que la plataforma tiene en custodia ahora mismo (pagado y todavía no liquidado)
    @Transactional(readOnly = true)
    public BigDecimal totalEnCustodia() {
        BigDecimal total = BigDecimal.ZERO;
        for (Transaccion t : transaccionRepository.findAll()) {
            EstadoTransaccion e = t.getEstado();
            if (e == EstadoTransaccion.PAGADA || e == EstadoTransaccion.ENVIADA || e == EstadoTransaccion.EN_DISPUTA) {
                total = total.add(t.getMonto());
            }
        }
        return total;
    }

    public BigDecimal totalPorTipo(TipoMovimiento tipo) {
        return movimientoRepository.sumarPorTipo(tipo);
    }

    // Cantidad de tratos en cada estado, en el orden del enum
    public Map<EstadoTransaccion, Long> tratosPorEstado() {
        Map<EstadoTransaccion, Long> conteo = new LinkedHashMap<>();
        for (EstadoTransaccion estado : EstadoTransaccion.values()) {
            conteo.put(estado, transaccionRepository.countByEstado(estado));
        }
        return conteo;
    }

    @Transactional(readOnly = true)
    public List<Movimiento> movimientosRecientes() {
        return movimientoRepository.findTop50ByOrderByIdDesc();
    }
}
