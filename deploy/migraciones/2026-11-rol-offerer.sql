-- Migración: nuevo rol ROLE_OFFERER (solo ADMIN y OFFERER pueden publicar espacios)
-- Correr UNA vez sobre space_venue_db ANTES (o justo después) de desplegar la versión del backend que lo incluye.
--
-- 1) Hibernate crea la columna credentials.rol como ENUM nativo de MySQL con los valores que existían
--    al crear la tabla. Con ddl-auto=update NO se agregan valores nuevos al ENUM, así que sin este ALTER
--    guardar ROLE_OFFERER falla ("Data truncated for column 'rol'").
ALTER TABLE credentials
    MODIFY COLUMN rol ENUM('ROLE_ADMIN', 'ROLE_CLIENT', 'ROLE_OFFERER') NULL;

-- 2) Los clientes que ya tienen espacios publicados (no eliminados por ellos) pasan a OFFERER,
--    para que puedan seguir gestionándolos.
UPDATE credentials c
    JOIN consumers co ON co.username = c.username
SET c.rol = 'ROLE_OFFERER'
WHERE c.rol = 'ROLE_CLIENT'
  AND EXISTS (SELECT 1
              FROM spaces s
              WHERE s.idConsumerOwner = co.idConsumer
                AND (s.deletedByOwner = FALSE OR s.deletedByOwner IS NULL));

-- Verificación
SELECT rol, COUNT(*) FROM credentials GROUP BY rol;
