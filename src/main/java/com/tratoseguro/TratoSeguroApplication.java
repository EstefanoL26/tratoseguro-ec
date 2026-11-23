package com.tratoseguro;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling activa la revisión diaria de plazos (PlazoService)
// @EnableAsync permite mandar correos y push en segundo plano (AvisosExternos)
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class TratoSeguroApplication {

    public static void main(String[] args) {
        SpringApplication.run(TratoSeguroApplication.class, args);
    }
}
