package com.tratoseguro.service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.tratoseguro.model.Disputa;
import com.tratoseguro.model.Historial;
import com.tratoseguro.model.Movimiento;
import com.tratoseguro.model.Transaccion;
import com.tratoseguro.model.Usuario;
import com.tratoseguro.repository.DisputaRepository;
import com.tratoseguro.util.Reloj;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;

/**
 * Comprobante de liquidación en PDF: reparto del dinero retenido (R + comisión + neto = M),
 * movimientos, decisión del mediador e historial. Lleva un código de verificación calculado
 * con SHA-256 sobre los datos de la liquidación: si alguien edita las cifras, el código no coincide.
 */
@Service
public class ComprobanteService {

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Color AZUL = new Color(0x0B, 0x3D, 0x91);
    private static final Color GRIS = new Color(0x5F, 0x6B, 0x7A);
    private static final Color FONDO = new Color(0xF1, 0xF4, 0xF9);
    private static final Color VERDE = new Color(0x1B, 0x7F, 0x4B);

    private final TransaccionService transaccionService;
    private final LiquidacionService liquidacionService;
    private final DisputaRepository disputaRepository;

    public ComprobanteService(TransaccionService transaccionService, LiquidacionService liquidacionService,
                              DisputaRepository disputaRepository) {
        this.transaccionService = transaccionService;
        this.liquidacionService = liquidacionService;
        this.disputaRepository = disputaRepository;
    }

    /**
     * Genera el PDF del trato (solo si ya se liquidó). Comprueba que el usuario pueda ver el trato.
     */
    @Transactional(readOnly = true)
    public byte[] generar(Long transaccionId, Usuario usuario) {
        Transaccion t = transaccionService.obtenerParaVer(transaccionId, usuario);
        if (t.getComision() == null) {
            throw new ReglaNegocioException("El comprobante está disponible cuando el trato se liquida (liberado o reembolsado).");
        }
        List<Movimiento> movimientos = liquidacionService.movimientos(t.getId());
        List<Historial> historial = transaccionService.historial(t.getId());
        Disputa disputa = disputaRepository.findByTransaccionId(t.getId()).orElse(null);

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A4, 48, 48, 48, 48);
        try {
            PdfWriter.getInstance(documento, salida);
            documento.addTitle("Comprobante de liquidación - Trato #" + t.getId());
            documento.addAuthor("TratoSeguro EC");
            documento.open();

            encabezado(documento, t);
            datosDelTrato(documento, t);
            liquidacion(documento, t);
            if (disputa != null && disputa.isResuelta()) {
                decision(documento, disputa);
            }
            tablaMovimientos(documento, movimientos);
            tablaHistorial(documento, historial);
            pie(documento, t, usuario);
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el comprobante", e);
        } finally {
            documento.close();
        }
        return salida.toByteArray();
    }

    /**
     * Código de verificación: SHA-256 de las cifras de la liquidación, en 4 grupos de 4 caracteres.
     * Es el mismo en el PDF, en el detalle del trato y en la API.
     */
    public static String codigoVerificacion(Transaccion t) {
        if (t.getComision() == null) {
            return null;
        }
        String datos = "TRATO-" + t.getId() + "|" + t.getMonto().toPlainString() + "|" + t.getReembolso().toPlainString()
                + "|" + t.getComision().toPlainString() + "|" + t.getMontoVendedor().toPlainString() + "|" + t.getEstado().name()
                + "|" + t.getVendedor().getId() + "|" + t.getComprador().getId();
        try {
            String hex = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(datos.getBytes(StandardCharsets.UTF_8)))
                    .substring(0, 16).toUpperCase();
            return hex.substring(0, 4) + "-" + hex.substring(4, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    // ---------- Secciones ----------

    private void encabezado(Document documento, Transaccion t) throws DocumentException {
        Paragraph marca = new Paragraph("TratoSeguro EC", fuente(20, Font.BOLD, AZUL));
        documento.add(marca);
        documento.add(new Paragraph("Pago protegido para compras entre personas", fuente(9, Font.NORMAL, GRIS)));
        documento.add(espacio(10));
        documento.add(new Paragraph("Comprobante de liquidación · Trato #" + t.getId(), fuente(14, Font.BOLD, Color.BLACK)));
        Paragraph aviso = new Paragraph("Documento de prueba: los pagos de TratoSeguro EC son simulados o de Stripe en modo de prueba. "
                + "No representa un cobro real.", fuente(8, Font.ITALIC, GRIS));
        aviso.setSpacingAfter(12);
        documento.add(aviso);
    }

    private void datosDelTrato(Document documento, Transaccion t) throws DocumentException {
        PdfPTable tabla = tablaDatos();
        fila(tabla, "Producto", t.getProducto());
        fila(tabla, "Vendedor", t.getVendedor().getNombre() + " (cédula " + ocultarCedula(t.getVendedor().getCedula()) + ")");
        fila(tabla, "Comprador", t.getComprador().getNombre() + " (cédula " + ocultarCedula(t.getComprador().getCedula()) + ")");
        fila(tabla, "Creado", t.getFechaCreacion().format(FECHA_HORA));
        if (t.getFechaPago() != null) {
            fila(tabla, "Pagado", t.getFechaPago().format(FECHA_HORA) + " · " + t.getMetodoPago().getDescripcion());
        }
        fila(tabla, "Estado final", t.getEstado().getDescripcion());
        documento.add(tabla);
    }

    private void liquidacion(Document documento, Transaccion t) throws DocumentException {
        documento.add(titulo("Reparto del dinero retenido"));
        BigDecimal base = t.getMonto().subtract(t.getReembolso());

        PdfPTable tabla = new PdfPTable(new float[]{3.2f, 1.3f});
        tabla.setWidthPercentage(100);
        filaMonto(tabla, "Monto retenido (M)", t.getMonto(), false);
        filaMonto(tabla, "Reembolso al comprador (R)", t.getReembolso(), false);
        filaMonto(tabla, "Base = M - R", base, false);
        filaMonto(tabla, "Comisión de la plataforma (3 % de la base)", t.getComision(), false);
        filaMonto(tabla, "Neto al vendedor = base - comisión", t.getMontoVendedor(), false);
        filaMonto(tabla, "R + comisión + neto", t.getReembolso().add(t.getComision()).add(t.getMontoVendedor()), true);
        documento.add(tabla);

        boolean cuadra = t.getReembolso().add(t.getComision()).add(t.getMontoVendedor()).compareTo(t.getMonto()) == 0;
        Paragraph cuadre = new Paragraph(cuadra ? "Regla de cuadre: R + comisión + neto = M. Cuadra."
                : "Regla de cuadre: NO cuadra.", fuente(9, Font.BOLD, cuadra ? VERDE : Color.RED));
        cuadre.setSpacingBefore(4);
        documento.add(cuadre);
    }

    private void decision(Document documento, Disputa disputa) throws DocumentException {
        documento.add(titulo("Decisión del mediador"));
        PdfPTable tabla = tablaDatos();
        fila(tabla, "Motivo de la disputa", disputa.getMotivo());
        fila(tabla, "Decisión", disputa.getDecision().getDescripcion() + (disputa.isViaRapida() ? " (vía rápida por fraude)" : ""));
        fila(tabla, "Mediador", disputa.getMediador() == null ? "—" : disputa.getMediador().getNombre());
        fila(tabla, "Justificación", disputa.getJustificacion());
        documento.add(tabla);
    }

    private void tablaMovimientos(Document documento, List<Movimiento> movimientos) throws DocumentException {
        documento.add(titulo("Movimientos de dinero"));
        PdfPTable tabla = new PdfPTable(new float[]{2.2f, 1.2f, 1.6f, 2.6f});
        tabla.setWidthPercentage(100);
        cabecera(tabla, "Tipo", "Monto", "Fecha", "Referencia");
        for (Movimiento m : movimientos) {
            celda(tabla, m.getTipo().getDescripcion(), Element.ALIGN_LEFT);
            celda(tabla, dinero(m.getMonto()), Element.ALIGN_RIGHT);
            celda(tabla, m.getFecha().format(FECHA_HORA), Element.ALIGN_LEFT);
            celda(tabla, m.getReferencia() == null ? "—" : m.getReferencia(), Element.ALIGN_LEFT);
        }
        documento.add(tabla);
    }

    private void tablaHistorial(Document documento, List<Historial> historial) throws DocumentException {
        documento.add(titulo("Historial de estados"));
        PdfPTable tabla = new PdfPTable(new float[]{1.6f, 1.4f, 4.2f});
        tabla.setWidthPercentage(100);
        cabecera(tabla, "Fecha", "Estado", "Motivo");
        for (Historial h : historial) {
            celda(tabla, h.getFecha().format(FECHA_HORA), Element.ALIGN_LEFT);
            celda(tabla, h.getEstadoNuevo().getDescripcion(), Element.ALIGN_LEFT);
            celda(tabla, (h.getMotivo() == null ? "" : h.getMotivo()) + " · " + h.describirAutor(), Element.ALIGN_LEFT);
        }
        documento.add(tabla);
    }

    private void pie(Document documento, Transaccion t, Usuario usuario) throws DocumentException {
        documento.add(espacio(16));
        PdfPTable caja = new PdfPTable(1);
        caja.setWidthPercentage(100);
        Phrase texto = new Phrase();
        texto.add(new Chunk("Código de verificación: ", fuente(10, Font.NORMAL, Color.BLACK)));
        texto.add(new Chunk(codigoVerificacion(t), fuente(12, Font.BOLD, AZUL)));
        texto.add(new Chunk("\nGenerado el " + Reloj.ahora().format(FECHA_HORA) + " (hora de Ecuador) por "
                + usuario.getNombre() + ". El código se calcula con SHA-256 sobre las cifras de la liquidación; "
                + "si alguna cifra se altera, deja de coincidir con el que muestra la aplicación.", fuente(8, Font.NORMAL, GRIS)));
        PdfPCell celda = new PdfPCell(texto);
        celda.setBackgroundColor(FONDO);
        celda.setBorderColor(FONDO);
        celda.setPadding(10);
        caja.addCell(celda);
        documento.add(caja);
    }

    // ---------- Ayudantes de formato ----------

    private Font fuente(float tamano, int estilo, Color color) {
        return FontFactory.getFont(FontFactory.HELVETICA, "Cp1252", false, tamano, estilo, color);
    }

    private Paragraph titulo(String texto) {
        Paragraph p = new Paragraph(texto, fuente(11, Font.BOLD, AZUL));
        p.setSpacingBefore(14);
        p.setSpacingAfter(6);
        return p;
    }

    private Paragraph espacio(float alto) {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(alto);
        return p;
    }

    private PdfPTable tablaDatos() {
        PdfPTable tabla = new PdfPTable(new float[]{1.4f, 4f});
        tabla.setWidthPercentage(100);
        return tabla;
    }

    private void fila(PdfPTable tabla, String etiqueta, String valor) {
        PdfPCell a = new PdfPCell(new Phrase(etiqueta, fuente(9, Font.NORMAL, GRIS)));
        PdfPCell b = new PdfPCell(new Phrase(valor == null ? "—" : valor, fuente(10, Font.NORMAL, Color.BLACK)));
        for (PdfPCell c : new PdfPCell[]{a, b}) {
            c.setBorder(Rectangle.BOTTOM);
            c.setBorderColor(FONDO);
            c.setPadding(5);
            tabla.addCell(c);
        }
    }

    private void filaMonto(PdfPTable tabla, String etiqueta, BigDecimal monto, boolean total) {
        int estilo = total ? Font.BOLD : Font.NORMAL;
        PdfPCell a = new PdfPCell(new Phrase(etiqueta, fuente(10, estilo, Color.BLACK)));
        PdfPCell b = new PdfPCell(new Phrase(dinero(monto), fuente(10, estilo, Color.BLACK)));
        b.setHorizontalAlignment(Element.ALIGN_RIGHT);
        for (PdfPCell c : new PdfPCell[]{a, b}) {
            c.setBorder(total ? Rectangle.TOP : Rectangle.BOTTOM);
            c.setBorderColor(total ? Color.BLACK : FONDO);
            c.setPadding(5);
            if (total) {
                c.setBackgroundColor(FONDO);
            }
            tabla.addCell(c);
        }
    }

    private void cabecera(PdfPTable tabla, String... textos) {
        for (String texto : textos) {
            PdfPCell c = new PdfPCell(new Phrase(texto, fuente(9, Font.BOLD, Color.WHITE)));
            c.setBackgroundColor(AZUL);
            c.setBorderColor(AZUL);
            c.setPadding(5);
            tabla.addCell(c);
        }
        tabla.setHeaderRows(1);
    }

    private void celda(PdfPTable tabla, String texto, int alineacion) {
        PdfPCell c = new PdfPCell(new Phrase(texto, fuente(8.5f, Font.NORMAL, Color.BLACK)));
        c.setHorizontalAlignment(alineacion);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(FONDO);
        c.setPadding(4);
        tabla.addCell(c);
    }

    // $ 1.250,00 (formato de Ecuador)
    static String dinero(BigDecimal valor) {
        DecimalFormatSymbols simbolos = new DecimalFormatSymbols();
        simbolos.setDecimalSeparator(',');
        simbolos.setGroupingSeparator('.');
        return "$ " + new DecimalFormat("#,##0.00", simbolos).format(valor);
    }

    // Solo se muestran los 4 últimos dígitos de la cédula (protección de datos personales)
    static String ocultarCedula(String cedula) {
        if (cedula == null || cedula.length() < 4) {
            return "—";
        }
        return "******" + cedula.substring(cedula.length() - 4);
    }
}
