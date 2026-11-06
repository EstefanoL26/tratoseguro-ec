-- =====================================================================
-- TratoSeguro EC - Consultas para revisar la base de datos (PostgreSQL)
-- Abrir en DBeaver: File > Open File... y ejecutar cada bloque con Cmd + Enter
-- =====================================================================

-- 1. Tablas creadas por la migración de Flyway (y su historial de versiones)
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
ORDER BY table_name;

SELECT version, description, installed_on, success
FROM flyway_schema_history
ORDER BY installed_rank;

-- 2. Claves foráneas: qué columna apunta a qué tabla
SELECT tc.constraint_name AS restriccion,
       tc.table_name      AS tabla,
       kcu.column_name    AS columna,
       ccu.table_name     AS tabla_referenciada,
       ccu.column_name    AS columna_referenciada
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu
     ON tc.constraint_name = kcu.constraint_name
JOIN information_schema.constraint_column_usage ccu
     ON tc.constraint_name = ccu.constraint_name
WHERE tc.constraint_type = 'FOREIGN KEY'
ORDER BY tc.table_name, tc.constraint_name;

-- 3. Cada trato con sus dos partes (dos claves foráneas hacia USUARIO)
SELECT t.id, t.producto, t.monto, t.estado,
       v.nombre AS vendedor, c.nombre AS comprador,
       t.fecha_limite_envio, t.fecha_limite_revision
FROM transaccion t
JOIN usuario v ON v.id = t.id_vendedor
JOIN usuario c ON c.id = t.id_comprador
ORDER BY t.id;

-- 4. Regla de cuadre de cada trato liquidado: reembolso + comisión + neto = monto
SELECT id, producto, monto, reembolso, comision, monto_vendedor,
       reembolso + comision + monto_vendedor AS suma,
       CASE WHEN reembolso + comision + monto_vendedor = monto THEN 'CUADRA' ELSE 'NO CUADRA' END AS cuadre
FROM transaccion
WHERE comision IS NOT NULL
ORDER BY id;

-- 5. Movimientos de dinero (la contabilidad de la custodia)
SELECT m.id, t.producto, m.tipo, m.monto, m.fecha, m.referencia
FROM movimiento m
JOIN transaccion t ON t.id = m.id_transaccion
ORDER BY m.id;

-- 6. Expediente: historial de cambios de estado (usuario vacío = el sistema)
SELECT h.id_transaccion AS trato, h.estado_ant, h.estado_nuevo,
       COALESCE(u.nombre, 'Sistema') AS quien, h.fecha, h.motivo
FROM historial h
LEFT JOIN usuario u ON u.id = h.id_usuario
ORDER BY h.id_transaccion, h.id;

-- 7. Disputas con su evidencia y la decisión del mediador
SELECT d.id AS disputa, t.producto, d.motivo, e.descripcion AS evidencia,
       u.nombre AS aportada_por, d.decision, d.reembolso, d.via_rapida
FROM disputa d
JOIN transaccion t ON t.id = d.id_transaccion
LEFT JOIN evidencia e ON e.id_disputa = d.id
LEFT JOIN usuario u ON u.id = e.id_usuario
ORDER BY d.id, e.id;

-- 8. La base rechaza un comprador que no existe (fk_transaccion_comprador).
--    Debe dar ERROR: violates foreign key constraint. No se guarda nada.
INSERT INTO transaccion (id_vendedor, id_comprador, producto, monto, dias_entrega, estado, fecha)
VALUES (2, 99999, 'Prueba FK', 100.00, 5, 'CREADA', CURRENT_TIMESTAMP);

-- 9. La base rechaza un estado que no está en la máquina de estados (ck_transaccion_estado).
--    Debe dar ERROR: violates check constraint. No se cambia nada.
UPDATE transaccion SET estado = 'REGALADA' WHERE id = 1;

-- 10. La base no deja borrar a un usuario que participa en tratos (fk_transaccion_vendedor).
--     Debe dar ERROR: violates foreign key constraint. No se borra nada.
DELETE FROM usuario WHERE username = 'carlos';
