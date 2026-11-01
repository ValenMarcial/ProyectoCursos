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
  empresa_id BIGINT NOT NULL REFERENCES empresas (id),
  nombre TEXT NOT NULL,
  descripcion TEXT NOT NULL,
  programa TEXT NOT NULL,
  presupuesto INT NOT NULL CHECK (presupuesto > 0),
  fecha_inicio DATE NOT NULL,
  fecha_fin DATE NOT NULL,
  resultados VARCHAR(255),
  instancia_flowable_id VARCHAR(64) UNIQUE,
  estado TEXT NOT NULL DEFAULT 'solicitado',
  CONSTRAINT fechas_validas CHECK (fecha_inicio <= fecha_fin),
  CONSTRAINT cursos_empresa_nombre_unique UNIQUE (empresa_id, nombre),
  CONSTRAINT cursos_estado_valido CHECK (
    estado IN (
      'solicitado',
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
  )
);

INSERT INTO schema_version (version)
VALUES (2)
ON CONFLICT (version) DO NOTHING;
