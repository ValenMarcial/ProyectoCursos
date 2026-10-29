CREATE TABLE IF NOT EXISTS schema_version (
  version integer PRIMARY KEY,
  applied_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS empresas (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nombre TEXT NOT NULL UNIQUE,
  cantidad_empleados INTEGER NOT NULL CHECK (cantidad_empleados >= 0)
);

CREATE TABLE IF NOT EXISTS cursos (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nombre TEXT NOT NULL UNIQUE,
  descripcion TEXT NOT NULL,
  programa TEXT NOT NULL,
  presupuesto INT NOT NULL CHECK (presupuesto > 0),
  fecha_inicio DATE NOT NULL,
  fecha_fin DATE NOT NULL,
  resultados TEXT,
  instancia_flowable_id VARCHAR(64) UNIQUE,
  CONSTRAINT fechas_validas CHECK (fecha_inicio <= fecha_fin)
);

INSERT INTO schema_version (version)
VALUES (1)
ON CONFLICT (version) DO NOTHING;
