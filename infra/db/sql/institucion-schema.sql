CREATE TABLE IF NOT EXISTS schema_version (
  version integer PRIMARY KEY,
  applied_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS empresas (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nombre TEXT NOT NULL UNIQUE,
  cantidad_empleados INTEGER CHECK (cantidad_empleados >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS empresas_nombre_normalizado_unique
  ON empresas (lower(btrim(nombre)));

CREATE TABLE IF NOT EXISTS solicitudes (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  empresa_id BIGINT NOT NULL REFERENCES empresas (id),
  business_key TEXT NOT NULL UNIQUE,
  tematica TEXT NOT NULL,
  descripcion TEXT NOT NULL,
  contacto_nombre TEXT,
  contacto_email TEXT,
  contacto_telefono TEXT,
  participantes_estimados INTEGER CHECK (participantes_estimados > 0),
  fechas_preferidas TEXT,
  instancia_flowable_id VARCHAR(64) UNIQUE,
  integracion_flowable_estado TEXT NOT NULL DEFAULT 'pendiente'
    CHECK (integracion_flowable_estado IN ('pendiente', 'iniciada', 'legacy')),
  creada_en timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS cursos (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  empresa_id BIGINT NOT NULL REFERENCES empresas (id),
  nombre TEXT NOT NULL,
  descripcion TEXT NOT NULL,
  programa TEXT NOT NULL,
  presupuesto INT NOT NULL CHECK (presupuesto >= 0),
  fecha_inicio DATE NOT NULL,
  fecha_fin DATE NOT NULL,
  resultados VARCHAR(255),
  instancia_flowable_id VARCHAR(64) UNIQUE,
  estado TEXT NOT NULL DEFAULT 'solicitado',
  CONSTRAINT fechas_validas CHECK (fecha_inicio <= fecha_fin),
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
VALUES (0)
ON CONFLICT (version) DO NOTHING;
