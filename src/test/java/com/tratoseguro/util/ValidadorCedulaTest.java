package com.tratoseguro.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidadorCedulaTest {

    @Test
    void aceptaCedulasValidas() {
        assertTrue(ValidadorCedula.esValida("1712345675")); // Pichincha
        assertTrue(ValidadorCedula.esValida("0923456784")); // Guayas
        assertTrue(ValidadorCedula.esValida("0104567896")); // Azuay
    }

    @Test
    void rechazaDigitoVerificadorIncorrecto() {
        assertFalse(ValidadorCedula.esValida("1712345674"));
        assertFalse(ValidadorCedula.esValida("1234567890"));
    }

    @Test
    void rechazaFormatoIncorrecto() {
        assertFalse(ValidadorCedula.esValida(null));
        assertFalse(ValidadorCedula.esValida(""));
        assertFalse(ValidadorCedula.esValida("171234567"));    // 9 dígitos
        assertFalse(ValidadorCedula.esValida("17123456755"));  // 11 dígitos
        assertFalse(ValidadorCedula.esValida("17123A5675"));   // letras
    }

    @Test
    void rechazaProvinciaOTercerDigitoInvalidos() {
        assertFalse(ValidadorCedula.esValida("2512345678")); // provincia 25 no existe
        assertFalse(ValidadorCedula.esValida("0012345678")); // provincia 00 no existe
        assertFalse(ValidadorCedula.esValida("1762345678")); // tercer dígito 6 no es persona natural
    }
}
