BEGIN;

-- Resultado del proceso para todas las solicitudes, haya curso o no: rechazo, sin respuesta y no viable
-- terminan antes de "Habilitar acceso", que es la que crea el curso. NULL mientras la instancia sigue activa.
ALTER TABLE solicitudes
  ADD COLUMN IF NOT EXISTS resultado TEXT
    CHECK (resultado IN ('finalizado', 'cancelado', 'rechazado', 'cerrado_por_falta_de_respuesta', 'no_viable'));

INSERT INTO schema_version (version)
VALUES (3)
ON CONFLICT (version) DO NOTHING;

COMMIT;
