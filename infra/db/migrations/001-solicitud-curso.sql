BEGIN;

ALTER TABLE solicitudes
  ADD COLUMN IF NOT EXISTS curso_id BIGINT UNIQUE REFERENCES cursos (id);

INSERT INTO schema_version (version)
VALUES (1)
ON CONFLICT (version) DO NOTHING;

COMMIT;
