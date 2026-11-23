package com.tratoseguro.service;

import com.tratoseguro.util.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

/**
 * Envía los avisos por correo con la API de Brevo (HTTPS, puerto 443).
 * Render en su plan gratuito bloquea el envío por SMTP (puertos 25, 465 y 587), por eso no se usa SMTP.
 * Sin BREVO_API_KEY o sin CORREO_REMITENTE no se envía nada y la app sigue funcionando igual.
 */
@Service
public class CorreoService {

    private static final Logger log = LoggerFactory.getLogger(CorreoService.class);
    private static final String URL_BREVO = "https://api.brevo.com/v3/smtp/email";

    private final ITemplateEngine plantillas;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String apiKey;
    private final String remitente;
    private final String urlApp;

    public CorreoService(ITemplateEngine plantillas,
                         @Value("${correo.brevo-api-key:}") String apiKey,
                         @Value("${correo.remitente:}") String remitente,
                         @Value("${tratoseguro.url:http://localhost:8080}") String urlApp) {
        this.plantillas = plantillas;
        this.apiKey = apiKey.trim();
        this.remitente = remitente.trim();
        this.urlApp = urlApp.endsWith("/") ? urlApp.substring(0, urlApp.length() - 1) : urlApp;
    }

    public boolean isActivo() {
        return !apiKey.isEmpty() && !remitente.isEmpty();
    }

    public String getRemitente() {
        return remitente;
    }

    // Arma el correo con la plantilla templates/correo/aviso.html y lo envía. Nunca lanza excepciones:
    // si el correo falla, el trato ya se guardó y el aviso interno sigue en la campana.
    public void enviarAviso(NotificacionCreada aviso) {
        if (!isActivo() || aviso.email() == null || aviso.email().isBlank()) {
            return;
        }
        String enlace = aviso.transaccionId() != null
                ? urlApp + "/transacciones/" + aviso.transaccionId()
                : urlApp + "/notificaciones";
        String asunto = aviso.producto() != null
                ? "Novedad en tu trato: " + aviso.producto()
                : "Novedad en TratoSeguro EC";

        Context contexto = new Context(Locale.forLanguageTag("es-EC"));
        contexto.setVariable("nombre", aviso.nombre());
        contexto.setVariable("mensaje", aviso.mensaje());
        contexto.setVariable("producto", aviso.producto());
        contexto.setVariable("enlace", enlace);
        contexto.setVariable("urlApp", urlApp);
        String html = plantillas.process("correo/aviso", contexto);
        String texto = "Hola " + aviso.nombre() + ",\n\n" + aviso.mensaje() + "\n\nVer en TratoSeguro EC: " + enlace;

        String cuerpo = "{"
                + "\"sender\":{\"name\":\"TratoSeguro EC\",\"email\":" + Json.texto(remitente) + "},"
                + "\"to\":[{\"email\":" + Json.texto(aviso.email()) + ",\"name\":" + Json.texto(aviso.nombre()) + "}],"
                + "\"subject\":" + Json.texto(asunto) + ","
                + "\"htmlContent\":" + Json.texto(html) + ","
                + "\"textContent\":" + Json.texto(texto)
                + "}";
        try {
            HttpRequest pedido = HttpRequest.newBuilder(URI.create(URL_BREVO))
                    .timeout(Duration.ofSeconds(15))
                    .header("api-key", apiKey)
                    .header("accept", "application/json")
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                    .build();
            HttpResponse<String> respuesta = http.send(pedido, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() >= 300) {
                log.warn("Brevo no aceptó el correo para el usuario {}: HTTP {} {}", aviso.usuarioId(),
                        respuesta.statusCode(), respuesta.body());
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("No se pudo enviar el correo al usuario {}: {}", aviso.usuarioId(), e.getMessage());
        }
    }
}
