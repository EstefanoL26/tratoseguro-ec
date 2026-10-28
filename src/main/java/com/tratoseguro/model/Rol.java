package com.tratoseguro.model;

/**
 * Roles del sistema según el documento de análisis.
 */
public enum Rol {
    COMPRADOR("Comprador"),
    VENDEDOR("Vendedor"),
    MEDIADOR("Mediador"),
    FINANZAS("Finanzas"),
    ADMIN("Administrador");

    private final String descripcion;

    Rol(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
