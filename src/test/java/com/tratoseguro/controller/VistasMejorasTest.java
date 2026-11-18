package com.tratoseguro.controller;

import com.tratoseguro.model.Evidencia;
import com.tratoseguro.model.Rol;
import com.tratoseguro.model.Transaccion;
import com.tratoseguro.model.Usuario;
import com.tratoseguro.repository.EvidenciaRepository;
import com.tratoseguro.repository.TransaccionRepository;
import com.tratoseguro.repository.UsuarioRepository;
import com.tratoseguro.service.DisputaService;
import com.tratoseguro.service.TransaccionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BeanPropertyBindingResult;

import java.math.BigDecimal;
import java.util.Comparator;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pantallas, descargas y API REST de las mejoras del core. Cada prueba se deshace al terminar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class VistasMejorasTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};

    @Autowired private MockMvc mockMvc;
    @Autowired private TransaccionRepository transaccionRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private EvidenciaRepository evidenciaRepository;
    @Autowired private TransaccionService transaccionService;
    @Autowired private DisputaService disputaService;
    @Autowired private PasswordEncoder passwordEncoder;

    private Transaccion trato(String producto) {
        return transaccionRepository.findAll().stream()
                .filter(t -> t.getProducto().equals(producto)).findFirst().orElseThrow();
    }

    private Usuario usuario(String username) {
        return usuarioRepository.findByUsername(username).orElseThrow();
    }

    private Transaccion nuevoTratoEnviado() {
        Usuario carlos = usuario("carlos");
        Usuario ana = usuario("anatorres");
        Transaccion datos = new Transaccion();
        datos.setProducto("Parlante de prueba");
        datos.setMonto(new BigDecimal("120.00"));
        datos.setDiasEntrega(4);
        Transaccion t = transaccionService.guardar(datos, ana.getId(), carlos, new BeanPropertyBindingResult(datos, "transaccion"));
        transaccionService.registrarPagoSimulado(t.getId(), ana);
        transaccionService.registrarEnvio(t.getId(), carlos, "Servientrega", "SVT-0049381726");
        return t;
    }

    // ---------- Evidencia con archivos ----------

    @Test
    void subirUnaFotoComoEvidenciaYVerlaComoMediador() throws Exception {
        Transaccion t = nuevoTratoEnviado();
        disputaService.abrir(t.getId(), usuario("anatorres"), "El parlante no enciende al conectarlo", null, null);

        MockMultipartFile foto = new MockMultipartFile("archivo", "parlante.png", "image/png", PNG);
        mockMvc.perform(multipart("/transacciones/" + t.getId() + "/evidencia").file(foto)
                        .param("descripcion", "Foto del parlante apagado")
                        .with(user("anatorres").roles("COMPRADOR")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        Evidencia evidencia = evidenciaRepository.findAll().stream()
                .filter(e -> e.getDisputa().getTransaccion().getId().equals(t.getId()))
                .max(Comparator.comparing(Evidencia::getId)).orElseThrow();

        // El mediador ve el archivo con el tipo detectado y sin que el navegador "adivine" otro
        mockMvc.perform(get("/evidencias/" + evidencia.getId() + "/archivo").with(user("paula").roles("MEDIADOR")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(PNG));

        // La pantalla del mediador muestra el archivo
        Long disputaId = evidencia.getDisputa().getId();
        mockMvc.perform(get("/disputas/" + disputaId + "/resolver").with(user("paula").roles("MEDIADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("parlante.png")));

        // Un usuario ajeno no puede descargarlo
        usuarioRepository.save(new Usuario("ajeno4", "ajeno4@correo.ec", passwordEncoder.encode("clave123"),
                "Persona Ajena", "0802345679", Rol.COMPRADOR, null));
        mockMvc.perform(get("/evidencias/" + evidencia.getId() + "/archivo").with(user("ajeno4").roles("COMPRADOR")))
                .andExpect(status().isForbidden());
    }

    // ---------- Calificación, comprobante y perfil ----------

    @Test
    void calificarYDescargarElComprobanteDeUnTratoLiberado() throws Exception {
        Transaccion t = nuevoTratoEnviado();
        transaccionService.confirmarRecepcion(t.getId(), usuario("anatorres"));

        mockMvc.perform(get("/transacciones/" + t.getId()).with(user("anatorres").roles("COMPRADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Calificar")))
                .andExpect(content().string(containsString("Código de verificación")));

        mockMvc.perform(post("/transacciones/" + t.getId() + "/calificar").param("puntaje", "5").param("comentario", "Excelente")
                        .with(user("anatorres").roles("COMPRADOR")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/transacciones/" + t.getId() + "/comprobante.pdf").with(user("carlos").roles("VENDEDOR")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        mockMvc.perform(get("/vendedores/" + usuario("carlos").getId()).with(user("anatorres").roles("COMPRADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Excelente")));
    }

    @Test
    void elCompradorVeLaReputacionAntesDePagar() throws Exception {
        Transaccion bici = trato("Bicicleta aro 29");
        mockMvc.perform(get("/transacciones/" + bici.getId() + "/pago").with(user("anatorres").roles("COMPRADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("tratos cerrados")));
    }

    // ---------- Cobros (Stripe Connect) y finanzas ----------

    @Test
    void pantallaDeCobrosSoloParaVendedores() throws Exception {
        mockMvc.perform(get("/cuenta/cobros").with(user("carlos").roles("VENDEDOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Pagos simulados")));
        mockMvc.perform(get("/cuenta/cobros").with(user("anatorres").roles("COMPRADOR")))
                .andExpect(status().isForbidden());
        // Sin Stripe configurado no se puede conectar una cuenta
        mockMvc.perform(post("/cuenta/cobros/conectar").with(user("carlos").roles("VENDEDOR")).with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void finanzasMuestraGraficosYExportaCsv() throws Exception {
        mockMvc.perform(get("/finanzas").with(user("finanzas").roles("FINANZAS")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("graficoMeses")))
                .andExpect(content().string(containsString("chart.js@4.4.1")));
        mockMvc.perform(get("/finanzas/movimientos.csv").with(user("finanzas").roles("FINANZAS")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", startsWith("attachment")))
                .andExpect(content().string(containsString("RETENCION")));
        mockMvc.perform(get("/finanzas/movimientos.csv").with(user("carlos").roles("VENDEDOR")))
                .andExpect(status().isForbidden());
    }

    // ---------- API REST ----------

    @Test
    void laApiExigeCredencialesYSoloMuestraLoPropio() throws Exception {
        mockMvc.perform(get("/api/v1/transacciones")).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/transacciones").with(httpBasic("anatorres", "clave123")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("Bicicleta aro 29")))
                // Nunca se exponen contraseñas ni cédulas
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("password"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("cedula"))));

        Transaccion audifonos = trato("Audífonos Sony WH-1000XM5");
        mockMvc.perform(get("/api/v1/transacciones/" + audifonos.getId()).with(httpBasic("carlos", "clave123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liquidacion.netoVendedor").value(194.00))
                .andExpect(jsonPath("$.liquidacion.cuadra").value(true))
                .andExpect(jsonPath("$.codigoVerificacion").isString());

        usuarioRepository.save(new Usuario("ajeno5", "ajeno5@correo.ec", passwordEncoder.encode("clave123"),
                "Persona Ajena", "0902345677", Rol.COMPRADOR, null));
        mockMvc.perform(get("/api/v1/transacciones/" + audifonos.getId()).with(httpBasic("ajeno5", "clave123")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.estado").value(403));
    }

    @Test
    void laApiSimulaLiquidacionesYAplicaLasReglas() throws Exception {
        mockMvc.perform(get("/api/v1/liquidacion/simular").param("monto", "200.00").param("reembolso", "50.00")
                        .with(httpBasic("anatorres", "clave123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comision").value(4.50))
                .andExpect(jsonPath("$.netoVendedor").value(145.50))
                .andExpect(jsonPath("$.cuadra").value(true));

        mockMvc.perform(get("/api/v1/liquidacion/simular").param("monto", "200.00").param("reembolso", "250.00")
                        .with(httpBasic("anatorres", "clave123")))
                .andExpect(status().isUnprocessableContent());

        // El vendedor no puede confirmar la recepción; el comprador sí
        Transaccion t = nuevoTratoEnviado();
        mockMvc.perform(post("/api/v1/transacciones/" + t.getId() + "/confirmar-recepcion").with(httpBasic("carlos", "clave123")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/transacciones/" + t.getId() + "/confirmar-recepcion").with(httpBasic("anatorres", "clave123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("LIBERADA"));

        mockMvc.perform(get("/api/v1/vendedores/" + usuario("carlos").getId() + "/reputacion").with(httpBasic("anatorres", "clave123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vendedor.nombre").value("Carlos Mena"));
    }

    @Test
    void laDocumentacionOpenApiEsPublica() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/v1/transacciones")));
    }
}
