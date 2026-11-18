package com.tratoseguro.service;

import com.tratoseguro.model.ArchivoEvidencia;
import com.tratoseguro.model.ArchivoInfo;
import com.tratoseguro.model.Evidencia;
import com.tratoseguro.model.Usuario;
import com.tratoseguro.repository.ArchivoEvidenciaRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Archivos de evidencia (fotos JPG, PNG o WebP y documentos PDF, hasta 5 MB).
 *
 * Seguridad:
 *  - El tipo se detecta por los primeros bytes del archivo (firma), no por la extensión
 *    ni por el Content-Type que manda el navegador: un .exe renombrado a .jpg se rechaza.
 *  - Se guarda la huella SHA-256 para poder demostrar que el archivo no cambió.
 *  - Solo las partes del trato, el mediador, finanzas y el administrador pueden descargarlo.
 */
@Service
public class ArchivoEvidenciaService {

    public static final int TAMANO_MAXIMO = 5 * 1024 * 1024;
    public static final int MAXIMO_POR_DISPUTA = 10;

    private final ArchivoEvidenciaRepository archivoRepository;
    private final TransaccionService transaccionService;

    public ArchivoEvidenciaService(ArchivoEvidenciaRepository archivoRepository, TransaccionService transaccionService) {
        this.archivoRepository = archivoRepository;
        this.transaccionService = transaccionService;
    }

    public static boolean vacio(MultipartFile archivo) {
        return archivo == null || archivo.isEmpty();
    }

    // Valida el archivo antes de guardar nada (así una evidencia con archivo inválido no queda a medias)
    public byte[] validar(MultipartFile archivo, Long disputaId) {
        if (archivo.getSize() > TAMANO_MAXIMO) {
            throw new ReglaNegocioException("El archivo pesa más de 5 MB. Comprime la foto o divide el PDF.");
        }
        if (disputaId != null && archivoRepository.buscarInfoDeDisputa(disputaId).size() >= MAXIMO_POR_DISPUTA) {
            throw new ReglaNegocioException("La disputa ya tiene " + MAXIMO_POR_DISPUTA + " archivos; usa un enlace para el resto.");
        }
        byte[] datos;
        try {
            datos = archivo.getBytes();
        } catch (IOException e) {
            throw new ReglaNegocioException("No se pudo leer el archivo. Vuelve a subirlo.");
        }
        if (datos.length == 0 || datos.length > TAMANO_MAXIMO) {
            throw new ReglaNegocioException("El archivo está vacío o pesa más de 5 MB.");
        }
        if (detectarTipo(datos) == null) {
            throw new ReglaNegocioException("Solo se aceptan fotos JPG, PNG o WebP y documentos PDF.");
        }
        return datos;
    }

    @Transactional
    public ArchivoEvidencia guardar(Evidencia evidencia, String nombreOriginal, byte[] datos) {
        String tipo = detectarTipo(datos);
        return archivoRepository.save(new ArchivoEvidencia(evidencia, nombreSeguro(nombreOriginal, tipo), tipo, datos, sha256(datos)));
    }

    // Archivos de una disputa indexados por id de evidencia (sin cargar los bytes)
    @Transactional(readOnly = true)
    public Map<Long, ArchivoInfo> deDisputa(Long disputaId) {
        Map<Long, ArchivoInfo> mapa = new LinkedHashMap<>();
        if (disputaId != null) {
            for (ArchivoInfo info : archivoRepository.buscarInfoDeDisputa(disputaId)) {
                mapa.put(info.evidenciaId(), info);
            }
        }
        return mapa;
    }

    // Busca el archivo de una evidencia y comprueba que el usuario pueda verlo (si no, 403)
    @Transactional(readOnly = true)
    public ArchivoEvidencia obtenerParaVer(Long evidenciaId, Usuario usuario) {
        ArchivoEvidencia archivo = archivoRepository.findByEvidenciaId(evidenciaId)
                .orElseThrow(() -> new ReglaNegocioException("Esta evidencia no tiene archivo."));
        if (!transaccionService.puedeVer(archivo.getEvidencia().getDisputa().getTransaccion(), usuario)) {
            throw new AccessDeniedException("No participas en este trato.");
        }
        return archivo;
    }

    // ---------- Detección del tipo por la firma de bytes ----------

    static String detectarTipo(byte[] d) {
        if (empieza(d, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (empieza(d, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }
        // WebP: "RIFF" + 4 bytes de tamaño + "WEBP"
        if (d.length >= 12 && empieza(d, 'R', 'I', 'F', 'F') && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return "image/webp";
        }
        if (empieza(d, '%', 'P', 'D', 'F', '-')) {
            return "application/pdf";
        }
        return null;
    }

    private static boolean empieza(byte[] datos, int... firma) {
        if (datos.length < firma.length) {
            return false;
        }
        for (int i = 0; i < firma.length; i++) {
            if ((datos[i] & 0xFF) != firma[i]) {
                return false;
            }
        }
        return true;
    }

    // Quita rutas, tildes y caracteres raros; la extensión la pone el tipo real del archivo
    static String nombreSeguro(String original, String tipo) {
        String nombre = original == null ? "" : original;
        nombre = nombre.substring(Math.max(nombre.lastIndexOf('/'), nombre.lastIndexOf('\\')) + 1);
        int punto = nombre.lastIndexOf('.');
        if (punto > 0) {
            nombre = nombre.substring(0, punto);
        }
        nombre = Normalizer.normalize(nombre, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        nombre = nombre.replaceAll("[^A-Za-z0-9 _-]", "_").replaceAll("_+", "_").trim();
        if (nombre.isEmpty()) {
            nombre = "evidencia";
        }
        if (nombre.length() > 120) {
            nombre = nombre.substring(0, 120);
        }
        return nombre + extension(tipo);
    }

    private static String extension(String tipo) {
        return switch (tipo) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".pdf";
        };
    }

    static String sha256(byte[] datos) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(datos));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
