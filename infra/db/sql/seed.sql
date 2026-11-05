-- Seed para la tabla empresas
INSERT INTO empresas (nombre, cantidad_empleados) VALUES
  ('Tech Solutions S.A.', 150),
  ('AgroServicios del Sur', 45),
  ('Logística Global RCU', 280),
  ('Desarrollos e Innovación SRL', 12)
ON CONFLICT DO NOTHING;

-- Seed para las solicitudes históricas asociadas a los cursos de ejemplo
INSERT INTO solicitudes (
  empresa_id,
  business_key,
  tematica,
  descripcion,
  contacto_nombre,
  contacto_email,
  contacto_telefono,
  participantes_estimados,
  fechas_preferidas,
  instancia_flowable_id,
  integracion_flowable_estado
) VALUES
  (
    (SELECT id FROM empresas WHERE nombre = 'Tech Solutions S.A.'),
    'seed-solicitud-tech-bpmn',
    'Capacitación BPMN y Flowable',
    'Curso intensivo sobre modelado de procesos e integración con motores de flujo de trabajo.',
    'Contacto de prueba',
    'contacto@tech-solutions.example',
    NULL,
    20,
    'Marzo de 2026',
    'PROC_INST_1001',
    'legacy'
  ),
  (
    (SELECT id FROM empresas WHERE nombre = 'AgroServicios del Sur'),
    'seed-solicitud-agro-liderazgo',
    'Liderazgo y gestión de equipos',
    'Taller orientado a mandos medios sobre gestión de talento y resolución de conflictos.',
    'Contacto de prueba',
    'contacto@agroservicios.example',
    NULL,
    12,
    'Abril de 2026',
    'PROC_INST_1002',
    'legacy'
  ),
  (
    (SELECT id FROM empresas WHERE nombre = 'Logística Global RCU'),
    'seed-solicitud-logistica-seguridad',
    'Seguridad e higiene laboral',
    'Capacitación normativa obligatoria sobre prevención de riesgos de trabajo.',
    'Contacto de prueba',
    'contacto@logistica-global.example',
    NULL,
    30,
    'Mayo de 2026',
    NULL,
    'legacy'
  )
ON CONFLICT (business_key) DO NOTHING;

-- Seed para la tabla cursos
INSERT INTO cursos (
  empresa_id,
  nombre,
  descripcion,
  programa,
  presupuesto,
  fecha_inicio,
  fecha_fin,
  resultados,
  instancia_flowable_id,
  estado
) SELECT seed.empresa_id, seed.nombre, seed.descripcion, seed.programa, seed.presupuesto,
         seed.fecha_inicio, seed.fecha_fin, seed.resultados, seed.instancia_flowable_id, seed.estado
FROM (VALUES
  (
    (SELECT id FROM empresas WHERE nombre = 'Tech Solutions S.A.'),
    'Capacitación BPMN y Flowable',
    'Curso intensivo sobre modelado de procesos e integración con motores de flujo de trabajo.',
    '1. Fundamentos BPMN 2.0\n2. Arquitectura de Flowable\n3. Integración con Java\n4. Manejo de User Tasks y Variables',
    250000,
    '2026-03-01'::DATE,
    '2026-03-31'::DATE,
    'informes/capacitacion-bpmn-flowable.pdf',
    'PROC_INST_1001',
    'finalizado'
  ),
  (
    (SELECT id FROM empresas WHERE nombre = 'AgroServicios del Sur'),
    'Liderazgo y Gestión de Equipos',
    'Taller orientado a mandos medios sobre gestión de talento y resolución de conflictos.',
    '1. Comunicación Asertiva\n2. Delegación Efectiva\n3. Feedback y Evaluación',
    180000,
    '2026-04-10'::DATE,
    '2026-04-25'::DATE,
    NULL,
    'PROC_INST_1002',
    'en_desarrollo'
  ),
  (
    (SELECT id FROM empresas WHERE nombre = 'Logística Global RCU'),
    'Seguridad e Higiene Laboral',
    'Capacitación normativa obligatoria sobre prevención de riesgos de trabajo.',
    '1. Protocolos de Evacuación\n2. Ergonomía en el Puesto de Trabajo\n3. Primeros Auxilios',
    95000,
    '2026-05-05'::DATE,
    '2026-05-15'::DATE,
    NULL,
    NULL,
    'solicitado'
  )
) AS seed(empresa_id, nombre, descripcion, programa, presupuesto, fecha_inicio,
          fecha_fin, resultados, instancia_flowable_id, estado)
WHERE NOT EXISTS (
  SELECT 1
  FROM cursos existing
  WHERE existing.empresa_id = seed.empresa_id
    AND existing.nombre = seed.nombre
    AND existing.fecha_inicio = seed.fecha_inicio
);
