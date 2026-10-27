-- Migracion: convertir columna descripcion de OID (large object) a TEXT nativo.
-- Motivo: @Lob en Hibernate 6 + PostgreSQL almacena strings como OID/CLOB,
-- lo cual requiere transaccion activa para leer y causa errores 500.
--
-- Ejecutar ANTES de reiniciar la aplicacion con el cambio en Proceso.java.

-- 1. Agregar columna temporal tipo text
ALTER TABLE procesos ADD COLUMN descripcion_tmp text;

-- 2. Copiar el contenido de los large objects a la columna temporal
UPDATE procesos SET descripcion_tmp = convert_from(lo_get(descripcion), 'UTF8');

-- 3. Eliminar la columna OID original
ALTER TABLE procesos DROP COLUMN descripcion;

-- 4. Renombrar la columna temporal
ALTER TABLE procesos RENAME COLUMN descripcion_tmp TO descripcion;

-- 5. Aplicar constraint NOT NULL
ALTER TABLE procesos ALTER COLUMN descripcion SET NOT NULL;
