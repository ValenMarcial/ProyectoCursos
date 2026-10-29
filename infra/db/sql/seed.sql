-- Seed para la tabla empresas
INSERT INTO empresas (nombre, cantidad_empleados) VALUES
  ('Tech Solutions S.A.', 150),
  ('AgroServicios del Sur', 45),
  ('Logística Global RCU', 280),
  ('Desarrollos e Innovación SRL', 12);

-- Seed para la tabla cursos
INSERT INTO cursos (
  nombre, 
  descripcion, 
  programa, 
  presupuesto, 
  fecha_inicio, 
  fecha_fin, 
  resultados, 
  instancia_flowable_id
) VALUES
  (
    'Capacitación BPMN y Flowable', 
    'Curso intensivo sobre modelado de procesos e integración con motores de flujo de trabajo.', 
    '1. Fundamentos BPMN 2.0\n2. Arquitectura de Flowable\n3. Integración con Java\n4. Manejo de User Tasks y Variables', 
    250000, 
    '2026-03-01', 
    '2026-03-31', 
    'Proceso finalizado. El 95% de los participantes aprobó el examen integrador.', 
    'PROC_INST_1001'
  ),
  (
    'Liderazgo y Gestión de Equipos', 
    'Taller orientado a mandos medios sobre gestión de talento y resolución de conflictos.', 
    '1. Comunicación Asertiva\n2. Delegación Efectiva\n3. Feedback y Evaluación', 
    180000, 
    '2026-04-10', 
    '2026-04-25', 
    NULL, 
    'PROC_INST_1002'
  ),
  (
    'Seguridad e Higiene Laboral', 
    'Capacitación normativa obligatoria sobre prevención de riesgos de trabajo.', 
    '1. Protocolos de Evacuación\n2. Ergonomía en el Puesto de Trabajo\n3. Primeros Auxilios', 
    95000, 
    '2026-05-05', 
    '2026-05-15', 
    NULL, 
    NULL
  );