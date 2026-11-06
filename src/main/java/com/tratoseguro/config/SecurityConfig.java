package com.tratoseguro.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Reglas de seguridad: qué URLs son públicas y qué rol necesita cada sección.
 * Además, cada servicio comprueba que el usuario participe en el trato que quiere ver o cambiar.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/registro", "/css/**", "/img/**", "/js/**", "/error").permitAll()
                        // Stripe avisa los pagos aquí; se acepta solo con una firma válida (ver StripeWebhookController)
                        .requestMatchers(HttpMethod.POST, "/stripe/webhook").permitAll()
                        // Cada sección solo la puede usar su rol (si no, responde 403)
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/disputas/**").hasRole("MEDIADOR")
                        .requestMatchers("/finanzas/**").hasAnyRole("FINANZAS", "ADMIN")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/transacciones", true)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                )
                // El webhook lo llama Stripe (no un formulario), así que no lleva token CSRF
                .csrf(csrf -> csrf.ignoringRequestMatchers("/stripe/webhook"));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
