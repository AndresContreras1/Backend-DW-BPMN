-- Migracion: convertir columna contenido de OID (large object) a TEXT nativo
-- en la tabla mensajes. Mismo problema que procesos.descripcion.
--
-- Ejecutar ANTES de reiniciar la aplicacion con el cambio en Mensaje.java.

ALTER TABLE mensajes ADD COLUMN contenido_tmp text;
UPDATE mensajes SET contenido_tmp = convert_from(lo_get(contenido), 'UTF8');
ALTER TABLE mensajes DROP COLUMN contenido;
ALTER TABLE mensajes RENAME COLUMN contenido_tmp TO contenido;
ALTER TABLE mensajes ALTER COLUMN contenido SET NOT NULL;
