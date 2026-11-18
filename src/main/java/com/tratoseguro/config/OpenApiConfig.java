package com.tratoseguro.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentación de la API REST con OpenAPI 3 (Swagger UI en /swagger-ui.html).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI apiTratoSeguro() {
        return new OpenAPI()
                .info(new Info()
                        .title("TratoSeguro EC - API REST")
                        .version("v1")
                        .description("Consulta de tratos, liquidaciones (R + comisión + neto = M) y reputación de vendedores. "
                                + "Autenticación HTTP Basic con un usuario de la aplicación (por ejemplo anatorres / clave123). "
                                + "Cada usuario solo ve sus tratos."))
                .components(new Components().addSecuritySchemes("basic",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")));
    }
}
