package com.tratoseguro.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Errores que ocurren antes de saber qué controlador atiende la petición
 * (por ejemplo, un archivo demasiado grande se rechaza al leer el formulario).
 */
@ControllerAdvice
public class ManejoErroresGlobales {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String archivoMuyGrande(HttpServletRequest request, RedirectAttributes flash) {
        flash.addFlashAttribute("error", "El archivo pesa más de 5 MB. Comprime la foto o divide el PDF.");
        String ruta = request.getRequestURI();
        // Vuelve al detalle del trato (/transacciones/{id}/evidencia o /disputa -> /transacciones/{id})
        if (ruta != null && ruta.matches("/transacciones/\\d+/(evidencia|disputa)")) {
            return "redirect:" + ruta.substring(0, ruta.lastIndexOf('/'));
        }
        return "redirect:/transacciones";
    }
}
