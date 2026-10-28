-- =====================================================================
-- TratoSeguro EC - Consultas para revisar la base de datos (PostgreSQL)
-- Abrir en DBeaver: File > Open File... y ejecutar cada bloque con Cmd + Enter
-- =====================================================================

-- 1. Tablas que creó JPA/Hibernate a partir de las entidades
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
ORDER BY table_name;

-- 2. Claves foráneas: qué columna apunta a qué tabla
SELECT tc.table_name   AS tabla,
       kcu.column_name AS columna,
       ccu.table_name  AS tabla_referenciada,
       ccu.column_name AS columna_referenciada,
       tc.constraint_name AS restriccion
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu
     ON tc.constraint_name = kcu.constraint_name
JOIN information_schema.constraint_column_usage ccu
     ON tc.constraint_name = ccu.constraint_name
WHERE tc.constraint_type = 'FOREIGN KEY'
ORDER BY tc.table_name;

-- 3. Cómo se guarda un usuario (contraseña cifrada con BCrypt, rol como texto, id_ciudad = FK)
SELECT id, username, cedula, rol, id_ciudad, password
FROM usuario
ORDER BY id;

-- 4. Usuarios con su ciudad y su provincia siguiendo las dos claves foráneas
SELECT u.nombre, u.cedula, u.rol, c.nombre AS ciudad, p.nombre AS provincia
FROM usuario u
JOIN ciudad c    ON c.id = u.id_ciudad
JOIN provincia p ON p.id = c.id_provincia
ORDER BY u.id;

-- 5. Cuántas ciudades tiene cada provincia (relación 1 a N)
SELECT p.codigo, p.nombre AS provincia, COUNT(c.id) AS ciudades
FROM provincia p
LEFT JOIN ciudad c ON c.id_provincia = p.id
GROUP BY p.codigo, p.nombre
ORDER BY p.codigo;

-- 6. La base rechaza una ciudad que no existe (la FK id_ciudad lo impide).
--    Debe dar ERROR: violates foreign key constraint. No se guarda nada.
INSERT INTO usuario (username, email, password, nombre, cedula, rol, id_ciudad)
VALUES ('prueba_fk', 'prueba@correo.ec', 'x', 'Prueba FK', '1102345673', 'COMPRADOR', 99999);

-- 7. La base no deja borrar una provincia que tiene ciudades.
--    Debe dar ERROR: violates foreign key constraint. No se borra nada.
DELETE FROM provincia WHERE codigo = '17';
