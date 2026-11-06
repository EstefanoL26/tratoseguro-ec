package com.tratoseguro.controller;

import com.tratoseguro.service.ReglaNegocioException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URI;

/**
 * Convierte los errores de negocio en un mensaje para el usuario y lo devuelve a la página donde estaba.
 * Solo aplica a los controladores con vistas (no al webhook de Stripe).
 */
@ControllerAdvice(annotations = Controller.class)
public class ManejoErrores {

    @ExceptionHandler(ReglaNegocioException.class)
    public String reglaDeNegocio(ReglaNegocioException e, HttpServletRequest request, RedirectAttributes flash) {
        flash.addFlashAttribute("error", e.getMessage());
        return "redirect:" + paginaAnterior(request);
    }

    // Bloqueo optimista (@Version): otro proceso cambió el trato al mismo tiempo
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public String conflicto(HttpServletRequest request, RedirectAttributes flash) {
        flash.addFlashAttribute("error", "El trato cambió mientras lo procesabas (por ejemplo, venció un plazo). Revisa su estado y vuelve a intentarlo.");
        return "redirect:" + paginaAnterior(request);
    }

    // Solo se usa la ruta de la página anterior (nunca otro dominio), para evitar redirecciones abiertas
    private String paginaAnterior(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer != null) {
            try {
                URI uri = URI.create(referer);
                if (uri.getPath() != null && uri.getPath().startsWith("/")) {
                    return uri.getPath() + (uri.getQuery() == null ? "" : "?" + uri.getQuery());
                }
            } catch (IllegalArgumentException ignorada) {
                // Referer mal formado: se vuelve a la lista
            }
        }
        return "/transacciones";
    }
}
