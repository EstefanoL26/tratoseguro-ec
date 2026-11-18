package com.tratoseguro.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DineroTest {

    @Test
    void usaComaParaDecimalesYPuntoParaMiles() {
        assertEquals("$ 200,00", Dinero.formatear(new BigDecimal("200.00")));
        assertEquals("$ 4,50", Dinero.formatear(new BigDecimal("4.5")));
        assertEquals("$ 10.000,00", Dinero.formatear(new BigDecimal("10000")));
        assertEquals("$ 0,00", Dinero.formatear(BigDecimal.ZERO));
    }
}
