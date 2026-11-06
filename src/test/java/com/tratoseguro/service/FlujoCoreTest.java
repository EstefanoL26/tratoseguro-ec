package com.tratoseguro.service;

import com.tratoseguro.model.Decision;
import com.tratoseguro.model.Disputa;
import com.tratoseguro.model.EstadoTransaccion;
import com.tratoseguro.model.Movimiento;
import com.tratoseguro.model.Rol;
import com.tratoseguro.model.TipoMovimiento;
import com.tratoseguro.model.Transaccion;
import com.tratoseguro.model.Usuario;
import com.tratoseguro.repository.TransaccionRepository;
import com.tratoseguro.repository.UsuarioRepository;
import com.tratoseguro.util.Reloj;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BeanPropertyBindingResult;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flujos completos del core contra la base (cada prueba se deshace al terminar).
 * Sin claves de Stripe: los pagos son simulados.
 */
@SpringBootTest
@Transactional
class FlujoCoreTest {

    @Autowired private TransaccionService transaccionService;
    @Autowired private DisputaService disputaService;
    @Autowired private LiquidacionService liquidacionService;
    @Autowired private PlazoService plazoService;
    @Autowired private NotificacionService notificacionService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private TransaccionRepository transaccionRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Usuario carlos;
    private Usuario ana;
    private Usuario paula;

    @BeforeEach
    void cargarUsuarios() {
        carlos = usuarioRepository.findByUsername("carlos").orElseThrow();
        ana = usuarioRepository.findByUsername("anatorres").orElseThrow();
        paula = usuarioRepository.findByUsername("paula").orElseThrow();
    }

    private Transaccion nuevoTrato(String monto) {
        Transaccion datos = new Transaccion();
        datos.setProducto("Bicicleta de prueba");
        datos.setMonto(new BigDecimal(monto));
        datos.setDiasEntrega(5);
        Transaccion t = transaccionService.guardar(datos, ana.getId(), carlos, new BeanPropertyBindingResult(datos, "transaccion"));
        assertNotNull(t);
        return t;
    }

    private List<TipoMovimiento> tipos(Long id) {
        return liquidacionService.movimientos(id).stream().map(Movimiento::getTipo).toList();
    }

    @Test
    void flujoFelizRetieneYLiberaConComision() {
        Transaccion t = nuevoTrato("200.00");
        assertEquals(EstadoTransaccion.CREADA, t.getEstado());

        // Pago simulado (no hay claves de Stripe en las pruebas): devuelve null y retiene el dinero
        assertNull(transaccionService.iniciarPago(t.getId(), ana, "http://localhost"));
        t = transaccionService.buscar(t.getId());
        assertEquals(EstadoTransaccion.PAGADA, t.getEstado());
        assertNotNull(t.getFechaLimiteEnvio());
        assertEquals(List.of(TipoMovimiento.RETENCION), tipos(t.getId()));

        transaccionService.registrarEnvio(t.getId(), carlos, "Servientrega", "SVT-0049381726");
        t = transaccionService.buscar(t.getId());
        assertEquals(EstadoTransaccion.ENVIADA, t.getEstado());
        assertNotNull(t.getFechaLimiteRevision());

        transaccionService.confirmarRecepcion(t.getId(), ana);
        t = transaccionService.buscar(t.getId());
        assertEquals(EstadoTransaccion.LIBERADA, t.getEstado());
        assertEquals(new BigDecimal("6.00"), t.getComision());
        assertEquals(new BigDecimal("194.00"), t.getMontoVendedor());
        assertEquals(List.of(TipoMovimiento.RETENCION, TipoMovimiento.COMISION, TipoMovimiento.LIBERACION), tipos(t.getId()));

        // Historial: creado, pagado, enviado, liberado
        assertEquals(4, transaccionService.historial(t.getId()).size());
    }

    @Test
    void rechazaTransicionesFueraDeLaTabla() {
        Transaccion t = nuevoTrato("100.00");
        // No se puede confirmar la recepción de un trato que ni siquiera está pagado
        assertThrows(ReglaNegocioException.class, () -> transaccionService.confirmarRecepcion(t.getId(), ana));
        // No se puede registrar el envío antes del pago
        assertThrows(ReglaNegocioException.class, () -> transaccionService.registrarEnvio(t.getId(), carlos, "Servientrega", "SVT-123456"));
        assertEquals(EstadoTransaccion.CREADA, transaccionService.buscar(t.getId()).getEstado());
    }

    @Test
    void cadaRolSoloHaceLoSuyo() {
        Transaccion t = nuevoTrato("100.00");
        // El vendedor no puede pagar y el comprador no puede registrar el envío
        assertThrows(AccessDeniedException.class, () -> transaccionService.iniciarPago(t.getId(), carlos, "http://localhost"));
        transaccionService.iniciarPago(t.getId(), ana, "http://localhost");
        assertThrows(AccessDeniedException.class, () -> transaccionService.registrarEnvio(t.getId(), ana, "Servientrega", "SVT-123456"));
    }

    @Test
    void unUsuarioAjenoNoPuedeVerElTrato() {
        Usuario intruso = usuarioRepository.save(new Usuario("intruso", "intruso@correo.ec", passwordEncoder.encode("clave123"),
                "Otro Comprador", "1709876542", Rol.COMPRADOR, null));
        Transaccion t = nuevoTrato("100.00");
        assertThrows(AccessDeniedException.class, () -> transaccionService.obtenerParaVer(t.getId(), intruso));
        assertTrue(transaccionService.listarPara(intruso, null, null).isEmpty());
    }

    @Test
    void disputaConReembolsoParcialCuadra() {
        Transaccion t = nuevoTrato("200.00");
        transaccionService.iniciarPago(t.getId(), ana, "http://localhost");
        transaccionService.registrarEnvio(t.getId(), carlos, "Servientrega", "SVT-0049381726");
        Disputa d = disputaService.abrir(t.getId(), ana, "La bicicleta llegó con el freno dañado", "Foto del freno", null);
        assertEquals(EstadoTransaccion.EN_DISPUTA, transaccionService.buscar(t.getId()).getEstado());

        disputaService.agregarEvidencia(t.getId(), carlos, "Video del freno funcionando antes del envío", "https://drive.google.com/x");
        assertEquals(2, disputaService.evidencias(d.getId()).size());

        disputaService.resolver(d.getId(), paula, Decision.REEMBOLSO_PARCIAL, new BigDecimal("50.00"), false,
                "El daño es menor y el freno se puede ajustar.");
        t = transaccionService.buscar(t.getId());
        assertEquals(EstadoTransaccion.REEMBOLSADA, t.getEstado());
        assertEquals(new BigDecimal("50.00"), t.getReembolso());
        assertEquals(new BigDecimal("4.50"), t.getComision());
        assertEquals(new BigDecimal("145.50"), t.getMontoVendedor());
        assertEquals(List.of(TipoMovimiento.RETENCION, TipoMovimiento.REEMBOLSO, TipoMovimiento.COMISION, TipoMovimiento.LIBERACION),
                tipos(t.getId()));
    }

    @Test
    void viaRapidaReembolsaTodoAunqueSeEscojaOtraDecision() {
        Transaccion t = nuevoTrato("300.00");
        transaccionService.iniciarPago(t.getId(), ana, "http://localhost");
        Disputa d = disputaService.abrir(t.getId(), ana, "El vendedor nunca existió, es una estafa", null, null);

        disputaService.resolver(d.getId(), paula, Decision.PAGO_AL_VENDEDOR, null, true,
                "Fraude confirmado: la guía y la cédula del vendedor son falsas.");
        t = transaccionService.buscar(t.getId());
        assertEquals(EstadoTransaccion.REEMBOLSADA, t.getEstado());
        assertEquals(new BigDecimal("300.00"), t.getReembolso());
        assertEquals(new BigDecimal("0.00"), t.getComision());
    }

    @Test
    void reembolsoParcialInvalidoNoCambiaElEstado() {
        Transaccion t = nuevoTrato("200.00");
        transaccionService.iniciarPago(t.getId(), ana, "http://localhost");
        Disputa d = disputaService.abrir(t.getId(), ana, "El producto no coincide con la descripción", null, null);
        assertThrows(ReglaNegocioException.class, () -> disputaService.resolver(d.getId(), paula,
                Decision.REEMBOLSO_PARCIAL, new BigDecimal("250.00"), false, "Reembolso mayor al monto retenido."));
        assertEquals(EstadoTransaccion.EN_DISPUTA, transaccionService.buscar(t.getId()).getEstado());
    }

    @Test
    void plazosVencidosReembolsanOLiberanYLasDisputasQuedanPausadas() {
        Transaccion sinEnviar = nuevoTrato("120.00");
        transaccionService.iniciarPago(sinEnviar.getId(), ana, "http://localhost");

        Transaccion enDisputa = nuevoTrato("90.00");
        transaccionService.iniciarPago(enDisputa.getId(), ana, "http://localhost");
        disputaService.abrir(enDisputa.getId(), ana, "Quiero revisar el estado del producto", null, null);

        // Se adelanta el calendario 60 días: el límite de envío de los dos ya pasó
        plazoService.revisar(Reloj.hoy().plusDays(60));

        assertEquals(EstadoTransaccion.REEMBOLSADA, transaccionService.buscar(sinEnviar.getId()).getEstado());
        assertEquals(new BigDecimal("120.00"), transaccionService.buscar(sinEnviar.getId()).getReembolso());
        assertEquals(EstadoTransaccion.EN_DISPUTA, transaccionService.buscar(enDisputa.getId()).getEstado());
    }

    @Test
    void cadaCambioNotificaALasPartes() {
        long antes = notificacionService.contarNoLeidas(carlos);
        Transaccion t = nuevoTrato("100.00");
        transaccionService.iniciarPago(t.getId(), ana, "http://localhost");
        assertTrue(notificacionService.contarNoLeidas(carlos) > antes);
    }

    @Test
    void elVersionadoDetectaCambiosSimultaneos() {
        Transaccion t = nuevoTrato("100.00");
        Long versionInicial = transaccionRepository.findById(t.getId()).orElseThrow().getVersion();
        transaccionService.iniciarPago(t.getId(), ana, "http://localhost");
        transaccionRepository.flush();
        assertTrue(transaccionRepository.findById(t.getId()).orElseThrow().getVersion() > versionInicial);
    }
}
