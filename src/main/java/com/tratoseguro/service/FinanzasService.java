package com.tratoseguro.service;

import com.tratoseguro.model.EstadoTransaccion;
import com.tratoseguro.model.Movimiento;
import com.tratoseguro.model.ResumenMensual;
import com.tratoseguro.model.TipoMovimiento;
import com.tratoseguro.model.Transaccion;
import com.tratoseguro.repository.MovimientoRepository;
import com.tratoseguro.repository.TransaccionRepository;
import com.tratoseguro.util.Reloj;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

    /**
     * Totales por mes de los últimos "meses" meses (incluido el actual), para el gráfico de barras.
     */
    @Transactional(readOnly = true)
    public List<ResumenMensual> resumenMensual(int meses) {
        YearMonth actual = YearMonth.from(Reloj.hoy());
        YearMonth primero = actual.minusMonths(meses - 1L);

        Map<YearMonth, Map<TipoMovimiento, BigDecimal>> totales = new LinkedHashMap<>();
        for (int i = 0; i < meses; i++) {
            Map<TipoMovimiento, BigDecimal> vacio = new EnumMap<>(TipoMovimiento.class);
            for (TipoMovimiento tipo : TipoMovimiento.values()) {
                vacio.put(tipo, BigDecimal.ZERO);
            }
            totales.put(primero.plusMonths(i), vacio);
        }
        for (Movimiento m : movimientoRepository.findByFechaGreaterThanEqualOrderByFechaAsc(primero.atDay(1).atStartOfDay())) {
            Map<TipoMovimiento, BigDecimal> mes = totales.get(YearMonth.from(m.getFecha()));
            if (mes != null) {
                mes.merge(m.getTipo(), m.getMonto(), BigDecimal::add);
            }
        }

        DateTimeFormatter formato = DateTimeFormatter.ofPattern("MMM yyyy", Locale.forLanguageTag("es-EC"));
        List<ResumenMensual> resultado = new ArrayList<>();
        totales.forEach((mes, t) -> resultado.add(new ResumenMensual(mes.format(formato).replace(".", ""),
                t.get(TipoMovimiento.RETENCION), t.get(TipoMovimiento.LIBERACION),
                t.get(TipoMovimiento.REEMBOLSO), t.get(TipoMovimiento.COMISION))));
        return resultado;
    }

    /**
     * Todos los movimientos en CSV (separado por punto y coma, como lo abre Excel en español).
     */
    @Transactional(readOnly = true)
    public String movimientosCsv() {
        StringBuilder csv = new StringBuilder("id;fecha;trato;producto;vendedor;comprador;tipo;monto;referencia\n");
        DateTimeFormatter fecha = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (Movimiento m : movimientoRepository.findAllByOrderByIdAsc()) {
            Transaccion t = m.getTransaccion();
            csv.append(m.getId()).append(';')
                    .append(m.getFecha().format(fecha)).append(';')
                    .append(t.getId()).append(';')
                    .append(campoCsv(t.getProducto())).append(';')
                    .append(campoCsv(t.getVendedor().getNombre())).append(';')
                    .append(campoCsv(t.getComprador().getNombre())).append(';')
                    .append(m.getTipo().name()).append(';')
                    .append(m.getMonto().toPlainString().replace('.', ',')).append(';')
                    .append(campoCsv(m.getReferencia() == null ? "" : m.getReferencia())).append('\n');
        }
        return csv.toString();
    }

    // Comillas para textos con ; o comillas, y protección contra fórmulas de Excel (=, +, -, @)
    static String campoCsv(String valor) {
        String v = valor;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.contains(";") || v.contains("\"") || v.contains("\n")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
