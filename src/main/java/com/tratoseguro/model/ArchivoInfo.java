package com.tratoseguro.model;

/**
 * Datos de un archivo de evidencia sin sus bytes (para listar evidencias sin cargar los archivos).
 */
public record ArchivoInfo(Long id, Long evidenciaId, String nombre, String tipoContenido, Integer tamano, String sha256) {

    public boolean isImagen() {
        return tipoContenido.startsWith("image/");
    }

    // Tamaño legible: 850 KB, 2,4 MB
    public String getTamanoLegible() {
        if (tamano < 1024 * 1024) {
            return Math.max(1, Math.round(tamano / 1024.0)) + " KB";
        }
        return String.format("%.1f MB", tamano / (1024.0 * 1024.0)).replace('.', ',');
    }
}
