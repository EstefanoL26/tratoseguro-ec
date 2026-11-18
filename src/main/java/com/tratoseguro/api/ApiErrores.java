package com.tratoseguro.api;

import com.tratoseguro.api.ApiDtos.ErrorDto;
import com.tratoseguro.service.ReglaNegocioException;
import com.tratoseguro.util.Reloj;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Errores de la API en JSON (las pantallas usan ManejoErrores, que redirige con un mensaje).
 * Tiene prioridad sobre ManejoErrores para los controladores de este paquete.
 */
@RestControllerAdvice(basePackageClasses = ApiErrores.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiErrores {

    // Regla de negocio incumplida (transición no permitida, reembolso inválido, liquidación que no cuadra...)
    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<ErrorDto> reglaDeNegocio(ReglaNegocioException e) {
        return error(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorDto> sinPermiso(AccessDeniedException e) {
        return error(HttpStatus.FORBIDDEN, e.getMessage());
    }

    // Bloqueo optimista: otro proceso cambió el trato al mismo tiempo
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorDto> conflicto() {
        return error(HttpStatus.CONFLICT, "El trato cambió mientras se procesaba la petición. Consulta su estado y reintenta.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorDto> parametroInvalido(MethodArgumentTypeMismatchException e) {
        return error(HttpStatus.BAD_REQUEST, "Valor inválido para el parámetro \"" + e.getName() + "\".");
    }

    private ResponseEntity<ErrorDto> error(HttpStatus estado, String mensaje) {
        return ResponseEntity.status(estado).body(new ErrorDto(estado.value(), estado.getReasonPhrase(), mensaje, Reloj.ahora()));
    }
}
