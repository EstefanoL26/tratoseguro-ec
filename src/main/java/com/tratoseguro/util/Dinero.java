package com.tratoseguro.util;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

/**
 * Formato de dinero para los mensajes que arma el servidor: "$ 1.250,00",
 * igual que en las vistas (fragmento dinero de base.html).
 */
public final class Dinero {

    private Dinero() {
    }

    public static String formatear(BigDecimal valor) {
        DecimalFormatSymbols simbolos = new DecimalFormatSymbols();
        simbolos.setDecimalSeparator(',');
        simbolos.setGroupingSeparator('.');
        DecimalFormat formato = new DecimalFormat("#,##0.00", simbolos);
        return "$ " + formato.format(valor);
    }
}
