package com.tratoseguro.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Validación de archivos de evidencia: el tipo se decide por los bytes, no por el nombre.
 */
class ArchivoEvidenciaServiceTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};
    static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};
    static final byte[] WEBP = {'R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};
    static final byte[] PDF = "%PDF-1.7\n%prueba".getBytes(StandardCharsets.US_ASCII);
    static final byte[] EXE = {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0};

    private final ArchivoEvidenciaService servicio = new ArchivoEvidenciaService(null, null);

    @Test
    void detectaElTipoPorLaFirmaDeBytes() {
        assertEquals("image/png", ArchivoEvidenciaService.detectarTipo(PNG));
        assertEquals("image/jpeg", ArchivoEvidenciaService.detectarTipo(JPG));
        assertEquals("image/webp", ArchivoEvidenciaService.detectarTipo(WEBP));
        assertEquals("application/pdf", ArchivoEvidenciaService.detectarTipo(PDF));
        assertNull(ArchivoEvidenciaService.detectarTipo(EXE));
        assertNull(ArchivoEvidenciaService.detectarTipo(new byte[]{1}));
    }

    @Test
    void rechazaUnEjecutableRenombradoComoFoto() {
        MockMultipartFile falso = new MockMultipartFile("archivo", "foto.jpg", "image/jpeg", EXE);
        assertThrows(ReglaNegocioException.class, () -> servicio.validar(falso, null));
    }

    @Test
    void rechazaArchivosDeMasDe5MB() {
        byte[] grande = new byte[ArchivoEvidenciaService.TAMANO_MAXIMO + 1];
        System.arraycopy(PNG, 0, grande, 0, PNG.length);
        MockMultipartFile archivo = new MockMultipartFile("archivo", "grande.png", "image/png", grande);
        assertThrows(ReglaNegocioException.class, () -> servicio.validar(archivo, null));
    }

    @Test
    void aceptaUnPdfValido() {
        MockMultipartFile archivo = new MockMultipartFile("archivo", "factura.pdf", "application/pdf", PDF);
        assertEquals(PDF.length, servicio.validar(archivo, null).length);
    }

    @Test
    void elNombreSeSaneaYLaExtensionLaPoneElTipoReal() {
        assertEquals("Foto del paquete.png", ArchivoEvidenciaService.nombreSeguro("C:\\fotos\\Fotó del paquete.jpg", "image/png"));
        // Se descarta la ruta: no se puede escribir fuera de la carpeta ni engañar con "../"
        assertEquals("passwd.pdf", ArchivoEvidenciaService.nombreSeguro("../../etc/passwd", "application/pdf"));
        // Los caracteres de HTML se reemplazan
        assertEquals("_script_.jpg", ArchivoEvidenciaService.nombreSeguro("<script>.jpg", "image/jpeg"));
        assertEquals("evidencia.pdf", ArchivoEvidenciaService.nombreSeguro(null, "application/pdf"));
    }

    @Test
    void laHuellaEsSha256() {
        // SHA-256 de "abc" (vector de prueba del estándar FIPS 180-2)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                ArchivoEvidenciaService.sha256("abc".getBytes(StandardCharsets.US_ASCII)));
    }
}
