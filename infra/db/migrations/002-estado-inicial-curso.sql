BEGIN;

ALTER TABLE cursos
  ALTER COLUMN estado SET DEFAULT 'en_preparacion';

UPDATE cursos
SET estado = 'en_preparacion'
WHERE estado = 'solicitado';

ALTER TABLE cursos
  DROP CONSTRAINT cursos_estado_valido,
  ADD CONSTRAINT cursos_estado_valido CHECK (
    estado IN (
      'en_preparacion',
      'confirmado',
      'pendiente_de_inicio',
      'en_desarrollo',
      'en_elaboracion_informe',
      'finalizado',
      'cancelado',
      'rechazado',
      'cerrado_por_falta_de_respuesta',
      'no_viable'
    )
  );

INSERT INTO schema_version (version)
VALUES (2)
ON CONFLICT (version) DO NOTHING;

COMMIT;
