package com.tratoseguro.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EstadoTransaccionTest {

    @Test
    void permiteLasTransicionesDeLaTabla() {
        assertTrue(EstadoTransaccion.CREADA.puedeCambiarA(EstadoTransaccion.PAGADA));
        assertTrue(EstadoTransaccion.CREADA.puedeCambiarA(EstadoTransaccion.CANCELADA));
        assertTrue(EstadoTransaccion.PAGADA.puedeCambiarA(EstadoTransaccion.ENVIADA));
        assertTrue(EstadoTransaccion.PAGADA.puedeCambiarA(EstadoTransaccion.REEMBOLSADA));
        assertTrue(EstadoTransaccion.PAGADA.puedeCambiarA(EstadoTransaccion.EN_DISPUTA));
        assertTrue(EstadoTransaccion.ENVIADA.puedeCambiarA(EstadoTransaccion.LIBERADA));
        assertTrue(EstadoTransaccion.ENVIADA.puedeCambiarA(EstadoTransaccion.EN_DISPUTA));
        assertTrue(EstadoTransaccion.EN_DISPUTA.puedeCambiarA(EstadoTransaccion.LIBERADA));
        assertTrue(EstadoTransaccion.EN_DISPUTA.puedeCambiarA(EstadoTransaccion.REEMBOLSADA));
    }

    @Test
    void rechazaCualquierOtraTransicion() {
        assertFalse(EstadoTransaccion.CREADA.puedeCambiarA(EstadoTransaccion.LIBERADA));
        assertFalse(EstadoTransaccion.CREADA.puedeCambiarA(EstadoTransaccion.ENVIADA));
        assertFalse(EstadoTransaccion.PAGADA.puedeCambiarA(EstadoTransaccion.CANCELADA));
        assertFalse(EstadoTransaccion.ENVIADA.puedeCambiarA(EstadoTransaccion.REEMBOLSADA));
        assertFalse(EstadoTransaccion.EN_DISPUTA.puedeCambiarA(EstadoTransaccion.ENVIADA));
    }

    @Test
    void losEstadosFinalesNoCambian() {
        for (EstadoTransaccion fin : new EstadoTransaccion[]{EstadoTransaccion.LIBERADA, EstadoTransaccion.REEMBOLSADA, EstadoTransaccion.CANCELADA}) {
            assertTrue(fin.esFinal());
            for (EstadoTransaccion otro : EstadoTransaccion.values()) {
                assertFalse(fin.puedeCambiarA(otro), fin + " no debería poder pasar a " + otro);
            }
        }
    }
}
