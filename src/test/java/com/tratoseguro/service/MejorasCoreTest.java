package com.tratoseguro.service;

import com.tratoseguro.model.ArchivoInfo;
import com.tratoseguro.model.Disputa;
import com.tratoseguro.model.EstadoTransaccion;
import com.tratoseguro.model.Reputacion;
import com.tratoseguro.model.ResumenMensual;
import com.tratoseguro.model.Rol;
import com.tratoseguro.model.Transaccion;
import com.tratoseguro.model.Usuario;
import com.tratoseguro.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BeanPropertyBindingResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mejoras del core: evidencia con archivos, calificaciones y reputación,
 * comprobante PDF, gráficos y exportación de finanzas (pagos simulados).
 */
@SpringBootTest
@Transactional
class MejorasCoreTest {

    @Autowired private TransaccionService transaccionService;
    @Autowired private DisputaService disputaService;
    @Autowired private ArchivoEvidenciaService archivoEvidenciaService;
    @Autowired private CalificacionService calificacionService;
    @Autowired private ComprobanteService comprobanteService;
    @Autowired private FinanzasService finanzasService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Usuario carlos;
    private Usuario ana;

    @BeforeEach
    void cargarUsuarios() {
        carlos = usuarioRepository.findByUsername("carlos").orElseThrow();
        ana = usuarioRepository.findByUsername("anatorres").orElseThrow();
    }

    private Transaccion tratoPagado(String monto) {
        Transaccion datos = new Transaccion();
        datos.setProducto("Producto de prueba");
        datos.setMonto(new BigDecimal(monto));
        datos.setDiasEntrega(5);
        Transaccion t = transaccionService.guardar(datos, ana.getId(), carlos, new BeanPropertyBindingResult(datos, "transaccion"));
        transaccionService.registrarPagoSimulado(t.getId(), ana);
        return t;
    }

    private Transaccion tratoLiberado(String monto) {
        Transaccion t = tratoPagado(monto);
        transaccionService.registrarEnvio(t.getId(), carlos, "Servientrega", "SVT-0049381726");
        transaccionService.confirmarRecepcion(t.getId(), ana);
        return transaccionService.buscar(t.getId());
    }

    // ---------- Evidencia con archivos ----------

    @Test
    void laDisputaGuardaLaFotoConSuHuella() {
        Transaccion t = tratoPagado("150.00");
        MockMultipartFile foto = new MockMultipartFile("archivo", "paquete roto.png", "image/png", ArchivoEvidenciaServiceTest.PNG);
        Disputa d = disputaService.abrir(t.getId(), ana, "El paquete llegó roto y mojado", "Foto del paquete", null, foto);

        Map<Long, ArchivoInfo> archivos = archivoEvidenciaService.deDisputa(d.getId());
        assertEquals(1, archivos.size());
        ArchivoInfo info = archivos.values().iterator().next();
        assertEquals("image/png", info.tipoContenido());
        assertEquals("paquete roto.png", info.nombre());
        assertEquals(ArchivoEvidenciaService.sha256(ArchivoEvidenciaServiceTest.PNG), info.sha256());

        // El vendedor aporta un PDF y el mediador puede verlo; un usuario ajeno no
        MockMultipartFile pdf = new MockMultipartFile("archivo", "guia.pdf", "application/pdf", ArchivoEvidenciaServiceTest.PDF);
        disputaService.agregarEvidencia(t.getId(), carlos, "Guía de Servientrega firmada", null, pdf);
        assertEquals(2, archivoEvidenciaService.deDisputa(d.getId()).size());

        Usuario ajeno = usuarioRepository.save(new Usuario("ajeno2", "ajeno2@correo.ec", passwordEncoder.encode("clave123"),
                "Otra Persona", "0602345671", Rol.COMPRADOR, null));
        assertThrows(AccessDeniedException.class, () -> archivoEvidenciaService.obtenerParaVer(info.evidenciaId(), ajeno));
        Usuario paula = usuarioRepository.findByUsername("paula").orElseThrow();
        assertNotNull(archivoEvidenciaService.obtenerParaVer(info.evidenciaId(), paula));
    }

    @Test
    void unArchivoFalsoNoAbreLaDisputa() {
        Transaccion t = tratoPagado("150.00");
        MockMultipartFile falso = new MockMultipartFile("archivo", "foto.jpg", "image/jpeg", ArchivoEvidenciaServiceTest.EXE);
        assertThrows(ReglaNegocioException.class, () ->
                disputaService.abrir(t.getId(), ana, "El paquete llegó roto y mojado", "Foto", null, falso));
        // El estado no cambió: la validación ocurre antes de abrir la disputa
        assertEquals(EstadoTransaccion.PAGADA, transaccionService.buscar(t.getId()).getEstado());
    }

    @Test
    void unArchivoSinDescripcionSeRechaza() {
        Transaccion t = tratoPagado("150.00");
        MockMultipartFile foto = new MockMultipartFile("archivo", "foto.png", "image/png", ArchivoEvidenciaServiceTest.PNG);
        assertThrows(ReglaNegocioException.class, () ->
                disputaService.abrir(t.getId(), ana, "El paquete llegó roto y mojado", "", null, foto));
    }

    // ---------- Calificaciones y reputación ----------

    @Test
    void elCompradorCalificaUnaSolaVezUnTratoCerrado() {
        Transaccion abierto = tratoPagado("100.00");
        assertThrows(ReglaNegocioException.class, () -> calificacionService.calificar(abierto.getId(), ana, 5, null));

        Transaccion cerrado = tratoLiberado("100.00");
        assertThrows(AccessDeniedException.class, () -> calificacionService.calificar(cerrado.getId(), carlos, 5, null));
        assertThrows(ReglaNegocioException.class, () -> calificacionService.calificar(cerrado.getId(), ana, 6, null));
        assertThrows(ReglaNegocioException.class, () -> calificacionService.calificar(cerrado.getId(), ana, 0, null));

        calificacionService.calificar(cerrado.getId(), ana, 4, "  Todo bien  ");
        assertEquals("Todo bien", calificacionService.deTransaccion(cerrado.getId()).getComentario());
        assertThrows(ReglaNegocioException.class, () -> calificacionService.calificar(cerrado.getId(), ana, 5, null));
    }

    @Test
    void laReputacionPromediaLasCalificaciones() {
        Reputacion antes = calificacionService.reputacion(carlos);
        Transaccion uno = tratoLiberado("100.00");
        Transaccion dos = tratoLiberado("80.00");
        calificacionService.calificar(uno.getId(), ana, 5, null);
        calificacionService.calificar(dos.getId(), ana, 2, null);

        Reputacion despues = calificacionService.reputacion(carlos);
        assertEquals(antes.calificaciones() + 2, despues.calificaciones());
        assertEquals(antes.tratosCerrados() + 2, despues.tratosCerrados());
        assertNotNull(despues.promedio());
        assertEquals(1, despues.promedio().scale());
    }

    // ---------- Comprobante PDF ----------

    @Test
    void elComprobanteEsUnPdfSoloCuandoElTratoSeLiquido() {
        Transaccion pagado = tratoPagado("200.00");
        assertThrows(ReglaNegocioException.class, () -> comprobanteService.generar(pagado.getId(), ana));

        Transaccion liberado = tratoLiberado("200.00");
        byte[] pdf = comprobanteService.generar(liberado.getId(), ana);
        assertTrue(new String(pdf, 0, 5, StandardCharsets.US_ASCII).startsWith("%PDF-"));
        assertTrue(pdf.length > 1000);

        Usuario ajeno = usuarioRepository.save(new Usuario("ajeno3", "ajeno3@correo.ec", passwordEncoder.encode("clave123"),
                "Persona Ajena", "0702345675", Rol.COMPRADOR, null));
        assertThrows(AccessDeniedException.class, () -> comprobanteService.generar(liberado.getId(), ajeno));
    }

    @Test
    void elCodigoDeVerificacionCambiaSiCambiaUnaCifra() {
        Transaccion liberado = tratoLiberado("200.00");
        String codigo = ComprobanteService.codigoVerificacion(liberado);
        assertTrue(codigo.matches("[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}"));
        assertEquals(codigo, ComprobanteService.codigoVerificacion(liberado));

        liberado.setMontoVendedor(new BigDecimal("195.00"));
        assertTrue(!codigo.equals(ComprobanteService.codigoVerificacion(liberado)));
        assertNull(ComprobanteService.codigoVerificacion(tratoPagado("10.00")));
    }

    // ---------- Finanzas: gráficos y CSV ----------

    @Test
    void elResumenMensualTraeSeisMesesConElActual() {
        tratoPagado("300.00");
        List<ResumenMensual> meses = finanzasService.resumenMensual(6);
        assertEquals(6, meses.size());
        assertTrue(meses.get(5).retenido().compareTo(new BigDecimal("300.00")) >= 0);
    }

    @Test
    void elCsvTieneCabeceraYSeProtegeDeFormulas() {
        String csv = finanzasService.movimientosCsv();
        assertTrue(csv.startsWith("id;fecha;trato;producto;vendedor;comprador;tipo;monto;referencia\n"));
        assertTrue(csv.contains(";RETENCION;"));
        // Un texto que empieza con = se exporta con apóstrofo para que Excel no lo ejecute como fórmula
        assertEquals("'=SUM(A1:A9)", FinanzasService.campoCsv("=SUM(A1:A9)"));
        assertEquals("\"'=HYPERLINK(\"\"x\"\")\"", FinanzasService.campoCsv("=HYPERLINK(\"x\")"));
        assertEquals("\"a;b\"", FinanzasService.campoCsv("a;b"));
        assertEquals("Bicicleta", FinanzasService.campoCsv("Bicicleta"));
    }
}
