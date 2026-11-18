package com.tratoseguro.controller;

import com.tratoseguro.model.ArchivoEvidencia;
import com.tratoseguro.service.ArchivoEvidenciaService;
import com.tratoseguro.service.UsuarioService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Descarga de los archivos de evidencia. Solo los ve quien puede ver el trato
 * (las partes, el mediador, finanzas y el administrador); cualquier otro recibe 403.
 */
@Controller
public class EvidenciaController {

    private final ArchivoEvidenciaService archivoEvidenciaService;
    private final UsuarioService usuarioService;

    public EvidenciaController(ArchivoEvidenciaService archivoEvidenciaService, UsuarioService usuarioService) {
        this.archivoEvidenciaService = archivoEvidenciaService;
        this.usuarioService = usuarioService;
    }

    @GetMapping("/evidencias/{evidenciaId}/archivo")
    public ResponseEntity<byte[]> archivo(@PathVariable Long evidenciaId,
                                          @RequestParam(defaultValue = "false") boolean descargar,
                                          Authentication authentication) {
        ArchivoEvidencia archivo = archivoEvidenciaService.obtenerParaVer(evidenciaId,
                usuarioService.buscarPorUsername(authentication.getName()));
        ContentDisposition disposicion = (descargar ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(archivo.getNombre()).build();
        return ResponseEntity.ok()
                // El tipo es el detectado por los bytes al subirlo, nunca el que mandó el navegador
                .contentType(MediaType.parseMediaType(archivo.getTipoContenido()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .header(HttpHeaders.ETAG, "\"" + archivo.getSha256() + "\"")
                .body(archivo.getDatos());
    }
}
