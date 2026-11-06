package com.tratoseguro;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling activa la revisión diaria de plazos (PlazoService)
@SpringBootApplication
@EnableScheduling
public class TratoSeguroApplication {

    public static void main(String[] args) {
        SpringApplication.run(TratoSeguroApplication.class, args);
    }
}
