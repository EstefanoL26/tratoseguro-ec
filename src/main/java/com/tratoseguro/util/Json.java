package com.tratoseguro.util;

/**
 * Arma textos JSON simples para las llamadas a servicios externos (correo y push)
 * sin depender de la versión de Jackson del proyecto.
 */
public final class Json {

    private Json() {
    }

    // Devuelve el texto entre comillas, con los caracteres especiales escapados
    public static String texto(String valor) {
        if (valor == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (char c : valor.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
