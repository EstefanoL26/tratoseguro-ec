package com.tratoseguro.util;

/**
 * Valida una cédula ecuatoriana con el algoritmo del Registro Civil (módulo 10).
 *
 * Reglas:
 * 1. Exactamente 10 dígitos.
 * 2. Los dos primeros dígitos son la provincia: 01 a 24, o 30 (ecuatorianos en el exterior).
 * 3. El tercer dígito es menor a 6 (persona natural).
 * 4. Los 9 primeros dígitos se multiplican por 2,1,2,1,2,1,2,1,2; si un producto pasa de 9 se le resta 9.
 *    Se suman y el dígito verificador debe ser (10 - suma % 10) % 10, que tiene que coincidir con el décimo dígito.
 */
public final class ValidadorCedula {

    private static final int[] COEFICIENTES = {2, 1, 2, 1, 2, 1, 2, 1, 2};

    private ValidadorCedula() {
    }

    public static boolean esValida(String cedula) {
        if (cedula == null || !cedula.matches("\\d{10}")) {
            return false;
        }

        int provincia = Integer.parseInt(cedula.substring(0, 2));
        if ((provincia < 1 || provincia > 24) && provincia != 30) {
            return false;
        }

        int tercerDigito = cedula.charAt(2) - '0';
        if (tercerDigito >= 6) {
            return false;
        }

        int suma = 0;
        for (int i = 0; i < 9; i++) {
            int producto = (cedula.charAt(i) - '0') * COEFICIENTES[i];
            if (producto > 9) {
                producto -= 9;
            }
            suma += producto;
        }

        int verificador = (10 - suma % 10) % 10;
        return verificador == cedula.charAt(9) - '0';
    }
}
