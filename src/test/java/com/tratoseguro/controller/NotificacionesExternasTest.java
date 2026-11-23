package com.tratoseguro.controller;

import com.tratoseguro.model.Usuario;
import com.tratoseguro.repository.NotificacionRepository;
import com.tratoseguro.repository.PushSuscripcionRepository;
import com.tratoseguro.repository.UsuarioRepository;
import com.tratoseguro.util.Json;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Locale;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Avisos fuera de la app: suscripción de un dispositivo a las notificaciones push y aviso de prueba.
 * Las claves VAPID de abajo son solo para estas pruebas (no se usan en ningún servidor).
 * El correo queda apagado (sin BREVO_API_KEY), así que ninguna prueba manda correos reales.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "push.vapid-public-key=BCHZnpkZq4kVQPuajWcDxrZCScqM4yB5e4bgeCsot-rpBzbISEWc4n9qjy2pO8DEsHTQlSnZ8aAzcxtV_eRiW8k",
        "push.vapid-private-key=_th2PW5wjreooIKBUJOsz9i7rpflyn8zfmfZ7OQp69Y"
})
class NotificacionesExternasTest {

    private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/prueba-tratoseguro-123";
    private static final String SUSCRIPCION = "{\"endpoint\":\"" + ENDPOINT + "\","
            + "\"keys\":{\"p256dh\":\"BNcRdreALRFXTkOOUHK1EtK2wtaz5Ry4YfYCA_0QTpQtUbVlUls0VJXg7A8u-Ts1XbjhazAkj7I99e8QcYP7DkM\",\"auth\":\"tBHItJI5svbpez7KI4CCXg\"}}";

    @Autowired private MockMvc mockMvc;
    @Autowired private PushSuscripcionRepository suscripcionRepository;
    @Autowired private NotificacionRepository notificacionRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ITemplateEngine plantillas;

    @Test
    void laPaginaMuestraLosCanalesYLaClavePublicaParaElNavegador() throws Exception {
        mockMvc.perform(get("/notificaciones").with(user("anatorres").roles("COMPRADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Avisos fuera de la app")))
                .andExpect(content().string(containsString("data-vapid=\"BCHZnpkZq4kVQPuajWcDxrZCScqM4yB5e4bgeCsot-rpBzbISEWc4n9qjy2pO8DEsHTQlSnZ8aAzcxtV_eRiW8k\"")))
                .andExpect(content().string(containsString("No configurado")));
    }

    @Test
    void unDispositivoSeSuscribeYLuegoSeQuita() throws Exception {
        Usuario ana = usuarioRepository.findByUsername("anatorres").orElseThrow();

        mockMvc.perform(post("/notificaciones/push/suscribir").with(user("anatorres").roles("COMPRADOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(SUSCRIPCION))
                .andExpect(status().isOk());
        assertEquals(1, suscripcionRepository.countByUsuarioId(ana.getId()));

        // Otro usuario no puede quitar el dispositivo de Ana
        mockMvc.perform(post("/notificaciones/push/cancelar").with(user("carlos").roles("VENDEDOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(SUSCRIPCION))
                .andExpect(status().isOk());
        assertEquals(1, suscripcionRepository.countByUsuarioId(ana.getId()));

        mockMvc.perform(post("/notificaciones/push/cancelar").with(user("anatorres").roles("COMPRADOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(SUSCRIPCION))
                .andExpect(status().isOk());
        assertEquals(0, suscripcionRepository.countByUsuarioId(ana.getId()));
    }

    @Test
    void seRechazaUnaSuscripcionQueNoEsHttps() throws Exception {
        mockMvc.perform(post("/notificaciones/push/suscribir").with(user("anatorres").roles("COMPRADOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"http://inseguro.test/x\",\"keys\":{\"p256dh\":\"abc\",\"auth\":\"def\"}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sinTokenCsrfNoSePuedeSuscribir() throws Exception {
        mockMvc.perform(post("/notificaciones/push/suscribir").with(user("anatorres").roles("COMPRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(SUSCRIPCION))
                .andExpect(status().isForbidden());
    }

    @Test
    void elAvisoDePruebaQuedaEnLaCampana() throws Exception {
        Usuario paula = usuarioRepository.findByUsername("paula").orElseThrow();
        long antes = notificacionRepository.countByUsuarioIdAndLeidaFalse(paula.getId());

        mockMvc.perform(post("/notificaciones/prueba").with(user("paula").roles("MEDIADOR")).with(csrf()))
                .andExpect(redirectedUrl("/notificaciones"));

        assertEquals(antes + 1, notificacionRepository.countByUsuarioIdAndLeidaFalse(paula.getId()));
    }

    @Test
    void elJsonEscapaComillasYSaltosDeLinea() {
        String texto = Json.texto("Dijo \"hola\"\ny se fue");
        assertEquals("\"Dijo \\\"hola\\\"\\ny se fue\"", texto);
        assertTrue(Json.texto(null).equals("null"));
    }

    @Test
    void laPlantillaDelCorreoEscapaElMensajeYLlevaElEnlaceAlTrato() {
        Context contexto = new Context(Locale.forLanguageTag("es-EC"));
        contexto.setVariable("nombre", "Ana Torres");
        contexto.setVariable("mensaje", "\"Bicicleta\" pasó a Pagada <script>");
        contexto.setVariable("producto", "Bicicleta aro 29");
        contexto.setVariable("enlace", "https://tratoseguro-ec.onrender.com/transacciones/1");
        contexto.setVariable("urlApp", "https://tratoseguro-ec.onrender.com");
        String html = plantillas.process("correo/aviso", contexto);
        assertTrue(html.contains("Hola, Ana Torres"));
        assertTrue(html.contains("https://tratoseguro-ec.onrender.com/transacciones/1"));
        assertTrue(html.contains("&lt;script&gt;"));
    }
}
