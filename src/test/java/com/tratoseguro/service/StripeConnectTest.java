package com.tratoseguro.service;

import com.stripe.exception.InvalidRequestException;
import com.tratoseguro.model.MetodoPago;
import com.tratoseguro.model.Movimiento;
import com.tratoseguro.model.TipoMovimiento;
import com.tratoseguro.model.Transaccion;
import com.tratoseguro.model.Usuario;
import com.tratoseguro.repository.TransaccionRepository;
import com.tratoseguro.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BeanPropertyBindingResult;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Stripe Connect: al liberar un trato pagado con Stripe, el neto se transfiere a la cuenta del vendedor.
 * Stripe se reemplaza por un doble de prueba (no hay red ni claves en las pruebas): se comprueba
 * la lógica de la plataforma, no la de Stripe.
 */
@SpringBootTest
@Transactional
class StripeConnectTest {

    @MockitoBean private PagoService pagoService;

    @Autowired private TransaccionService transaccionService;
    @Autowired private CobroVendedorService cobroVendedorService;
    @Autowired private LiquidacionService liquidacionService;
    @Autowired private TransaccionRepository transaccionRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    private Usuario carlos;
    private Usuario ana;

    @BeforeEach
    void preparar() {
        carlos = usuarioRepository.findByUsername("carlos").orElseThrow();
        ana = usuarioRepository.findByUsername("anatorres").orElseThrow();
        carlos.setStripeCuentaId(null);
        carlos.setStripeCobrosActivos(false);
        usuarioRepository.save(carlos);
        when(pagoService.isStripeActivo()).thenReturn(true);
    }

    // Trato pagado "con Stripe" y enviado (el pago se registra simulado y luego se marca como de Stripe)
    private Transaccion tratoEnviadoConStripe(String monto) {
        Transaccion datos = new Transaccion();
        datos.setProducto("Tablet de prueba");
        datos.setMonto(new BigDecimal(monto));
        datos.setDiasEntrega(5);
        Transaccion t = transaccionService.guardar(datos, ana.getId(), carlos, new BeanPropertyBindingResult(datos, "transaccion"));
        transaccionService.registrarPagoSimulado(t.getId(), ana);
        t = transaccionService.buscar(t.getId());
        t.setMetodoPago(MetodoPago.STRIPE);
        t.setStripePaymentIntent("pi_prueba_" + t.getId());
        transaccionRepository.save(t);
        transaccionService.registrarEnvio(t.getId(), carlos, "Servientrega", "SVT-0049381726");
        return t;
    }

    private Movimiento liberacion(Long id) {
        return liquidacionService.movimientos(id).stream()
                .filter(m -> m.getTipo() == TipoMovimiento.LIBERACION).findFirst().orElseThrow();
    }

    private void activarCuenta() {
        carlos.setStripeCuentaId("acct_prueba");
        carlos.setStripeCobrosActivos(true);
        usuarioRepository.save(carlos);
    }

    @Test
    void conCuentaActivaElNetoSeTransfiereAlVendedor() throws Exception {
        activarCuenta();
        Transaccion t = tratoEnviadoConStripe("200.00");
        when(pagoService.transferirAVendedor(any(), eq(new BigDecimal("194.00")), eq("acct_prueba"))).thenReturn("tr_prueba_1");

        transaccionService.confirmarRecepcion(t.getId(), ana);

        assertEquals("tr_prueba_1", liberacion(t.getId()).getReferencia());
    }

    @Test
    void sinCuentaLaTransferenciaQuedaPendienteYSePagaAlActivarla() throws Exception {
        Transaccion t = tratoEnviadoConStripe("100.00");
        transaccionService.confirmarRecepcion(t.getId(), ana);

        // Sin cuenta conectada: la liberación se registra, pero la transferencia queda pendiente
        Movimiento movimiento = liberacion(t.getId());
        assertTrue(movimiento.isTransferenciaPendiente());
        assertTrue(cobroVendedorService.pendientes().stream().anyMatch(m -> m.getId().equals(movimiento.getId())));
        verify(pagoService, never()).transferirAVendedor(any(), any(), any());

        // El vendedor termina su registro en Stripe: se le paga lo pendiente
        carlos.setStripeCuentaId("acct_prueba");
        usuarioRepository.save(carlos);
        when(pagoService.cuentaPuedeRecibir("acct_prueba")).thenReturn(true);
        when(pagoService.transferirAVendedor(any(), eq(new BigDecimal("97.00")), eq("acct_prueba"))).thenReturn("tr_prueba_2");

        assertTrue(cobroVendedorService.actualizarEstadoCuenta(carlos));
        assertEquals("tr_prueba_2", liberacion(t.getId()).getReferencia());
        assertTrue(cobroVendedorService.pendientes().isEmpty());
    }

    @Test
    void siStripeRechazaLaTransferenciaLaLiberacionNoSeBloquea() throws Exception {
        activarCuenta();
        Transaccion t = tratoEnviadoConStripe("50.00");
        when(pagoService.transferirAVendedor(any(), any(), any()))
                .thenThrow(new InvalidRequestException("Fondos insuficientes", null, null, null, 400, null));

        transaccionService.confirmarRecepcion(t.getId(), ana);

        assertTrue(liberacion(t.getId()).isTransferenciaPendiente());
        // Finanzas puede reintentarla después
        // doReturn: no llama al doble (que todavía lanzaría la excepción) al cambiar la respuesta
        doReturn("tr_prueba_3").when(pagoService).transferirAVendedor(any(), any(), any());
        assertEquals(1, cobroVendedorService.reintentarPendientes(null));
        assertEquals("tr_prueba_3", liberacion(t.getId()).getReferencia());
    }

    @Test
    void unTratoConPagoSimuladoNoUsaStripeConnect() throws Exception {
        activarCuenta();
        Transaccion datos = new Transaccion();
        datos.setProducto("Mouse de prueba");
        datos.setMonto(new BigDecimal("30.00"));
        datos.setDiasEntrega(5);
        Transaccion t = transaccionService.guardar(datos, ana.getId(), carlos, new BeanPropertyBindingResult(datos, "transaccion"));
        transaccionService.registrarPagoSimulado(t.getId(), ana);
        transaccionService.registrarEnvio(t.getId(), carlos, "Servientrega", "SVT-0049381726");
        transaccionService.confirmarRecepcion(t.getId(), ana);

        assertEquals(PagoService.REFERENCIA_SIMULADA, liberacion(t.getId()).getReferencia());
        verify(pagoService, never()).transferirAVendedor(any(), any(), any());
    }
}
