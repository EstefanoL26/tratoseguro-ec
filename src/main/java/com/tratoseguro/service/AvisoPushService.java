package com.tratoseguro.service;

import com.tratoseguro.model.PushSuscripcion;
import com.tratoseguro.model.Usuario;
import com.tratoseguro.repository.PushSuscripcionRepository;
import com.tratoseguro.util.Json;
import com.tratoseguro.util.Reloj;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Security;
import java.util.List;

/**
 * Notificaciones push al celular o la computadora (estándar Web Push con claves VAPID).
 * El navegador del usuario guarda una suscripción; el servidor cifra el aviso para ese dispositivo
 * y se lo entrega al servicio de push del navegador (Google, Apple o Mozilla).
 * Sin VAPID_PUBLIC_KEY y VAPID_PRIVATE_KEY la función queda apagada y la app sigue funcionando.
 */
@Service
public class AvisoPushService {

    private static final Logger log = LoggerFactory.getLogger(AvisoPushService.class);

    private final PushSuscripcionRepository suscripcionRepository;
    private final String clavePublica;
    private final PushService push;

    public AvisoPushService(PushSuscripcionRepository suscripcionRepository,
                            @Value("${push.vapid-public-key:}") String clavePublica,
                            @Value("${push.vapid-private-key:}") String clavePrivada,
                            @Value("${push.vapid-subject:mailto:soporte@tratoseguro.ec}") String asunto) {
        this.suscripcionRepository = suscripcionRepository;
        this.clavePublica = clavePublica.trim();
        PushService servicio = null;
        if (!this.clavePublica.isEmpty() && !clavePrivada.isBlank()) {
            try {
                if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                    Security.addProvider(new BouncyCastleProvider());
                }
                servicio = new PushService(this.clavePublica, clavePrivada.trim(), asunto);
            } catch (Exception e) {
                log.warn("Las claves VAPID no son válidas; las notificaciones push quedan apagadas: {}", e.getMessage());
            }
        }
        this.push = servicio;
    }

    public boolean isActivo() {
        return push != null;
    }

    public String getClavePublica() {
        return clavePublica;
    }

    public long contarDispositivos(Usuario usuario) {
        return suscripcionRepository.countByUsuarioId(usuario.getId());
    }

    // Guarda (o actualiza) el dispositivo. Si ese navegador ya estaba suscrito con otra cuenta, pasa a esta.
    @Transactional
    public void suscribir(Usuario usuario, String endpoint, String p256dh, String auth) {
        if (endpoint == null || !endpoint.startsWith("https://") || endpoint.length() > 500
                || p256dh == null || p256dh.isBlank() || p256dh.length() > 200
                || auth == null || auth.isBlank() || auth.length() > 100) {
            throw new ReglaNegocioException("El navegador envió una suscripción inválida.");
        }
        PushSuscripcion suscripcion = suscripcionRepository.findByEndpoint(endpoint).orElse(null);
        if (suscripcion == null) {
            suscripcionRepository.save(new PushSuscripcion(usuario, endpoint, p256dh, auth, Reloj.ahora()));
        } else {
            suscripcion.setUsuario(usuario);
            suscripcion.setP256dh(p256dh);
            suscripcion.setAuth(auth);
            suscripcion.setFecha(Reloj.ahora());
        }
    }

    // Solo el dueño puede quitar su dispositivo
    @Transactional
    public void cancelar(Usuario usuario, String endpoint) {
        suscripcionRepository.findByEndpoint(endpoint)
                .filter(s -> s.getUsuario().getId().equals(usuario.getId()))
                .ifPresent(suscripcionRepository::delete);
    }

    // Manda el aviso a todos los dispositivos del usuario. Nunca lanza excepciones.
    @Transactional
    public void enviarA(NotificacionCreada aviso) {
        if (!isActivo()) {
            return;
        }
        String url = aviso.transaccionId() != null ? "/transacciones/" + aviso.transaccionId() : "/notificaciones";
        String titulo = aviso.producto() != null ? aviso.producto() : "TratoSeguro EC";
        String carga = "{\"titulo\":" + Json.texto(titulo)
                + ",\"cuerpo\":" + Json.texto(aviso.mensaje())
                + ",\"url\":" + Json.texto(url) + "}";

        List<PushSuscripcion> dispositivos = suscripcionRepository.findByUsuarioId(aviso.usuarioId());
        for (PushSuscripcion dispositivo : dispositivos) {
            try {
                Subscription destino = new Subscription(dispositivo.getEndpoint(),
                        new Subscription.Keys(dispositivo.getP256dh(), dispositivo.getAuth()));
                HttpResponse respuesta = push.send(new Notification(destino, carga), Encoding.AES128GCM);
                int codigo = respuesta.getStatusLine().getStatusCode();
                // 404 o 410: el usuario quitó el permiso o desinstaló; ese dispositivo ya no sirve
                if (codigo == 404 || codigo == 410) {
                    suscripcionRepository.delete(dispositivo);
                } else if (codigo >= 300) {
                    log.warn("El servicio de push respondió HTTP {} para el usuario {}", codigo, aviso.usuarioId());
                } else {
                    log.info("Push entregado al servicio del navegador para el usuario {} (HTTP {})", aviso.usuarioId(), codigo);
                }
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                log.warn("No se pudo enviar el push al usuario {}: {}", aviso.usuarioId(), e.getMessage());
            }
        }
    }
}
