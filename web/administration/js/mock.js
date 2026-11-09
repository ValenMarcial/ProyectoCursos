/**
 * Módulo mock.js
 *
 * Simula la API a nivel HTTP, con datos en memoria: recibe la misma ruta y el
 * mismo cuerpo que el backend y devuelve { estado, cuerpo }. Así api.js ejecuta
 * el mismo código en modo mock y en modo real.
 *
 * Respuestas copiadas de lo verificado contra Flowable 6.8 en la Fase 0:
 * reclamar una tarea tomada da 409, las validaciones del listener dan 500 con
 * el texto en exception, y tras un rechazo la tarea sigue activa sin guardar
 * variables.
 *
 * No simula el avance del proceso: al completar, la tarea solo desaparece.
 */

import { baseApi, grupoAdministracion, tareas, variables as nombres } from './contrato.js';

/** Usuario que reemplaza a la sesión del backend cuando se reclama. */
export const usuarioSimulado = 'jefeAdministrativo';

/**
 * Crea una variable en el formato de Flowable.
 * Recibe: nombre, tipo y valor. Devuelve: { name, type, value, scope }.
 */
function variable(nombre, tipo, valor) {
  return { name: nombre, type: tipo, value: valor, scope: 'local' };
}

/**
 * Crea una tarea en el formato de Flowable.
 * Recibe: id, nombre, clave del BPMN, instancia, asignado y grupos candidatos.
 * Devuelve: la tarea simulada.
 */
function tarea(id, nombre, clave, instancia, asignado, grupos = []) {
  return {
    id,
    name: nombre,
    assignee: asignado,
    candidateGroups: grupos,
    taskDefinitionKey: clave,
    processInstanceId: instancia,
    createTime: '2026-11-09T12:00:00.000Z',
  };
}

/**
 * Arma los datos de ejemplo, con una tarea por pantalla.
 * No recibe nada. Devuelve: { tareas, instancias }.
 */
function crearDatosIniciales() {
  const solicitud = [
    variable('empresaNombre', 'string', 'Tech Solutions S.A.'),
    variable('tematica', 'string', 'Capacitación BPMN'),
    variable('descripcion', 'string', 'Curso para el equipo de desarrollo'),
    variable('contactoNombre', 'string', 'Ana Pérez'),
    variable('contactoEmail', 'string', 'ana@example.com'),
    variable('participantesEstimados', 'integer', 12),
    variable('fechasPreferidas', 'string', 'Marzo de 2027'),
  ];
  const propuesta = [
    variable(nombres.programa, 'string', 'Modelado de procesos'),
    variable(nombres.presupuesto, 'long', 150000),
    variable(nombres.fechaInicioPosible, 'date', '2027-03-01T00:00:00.000Z'),
    variable(nombres.fechaFinPropuesta, 'date', '2027-03-31T00:00:00.000Z'),
  ];

  return {
    tareas: [
      tarea('tarea-registrar', 'Registrar y Elaborar propuesta', tareas.registrarPropuesta, 'instancia-1', usuarioSimulado),
      tarea('tarea-revisar', 'Revisar propuesta', tareas.revisarPropuesta, 'instancia-2', usuarioSimulado),
      tarea('tarea-archivar', 'Archivar solicitud', tareas.archivarSolicitud, 'instancia-3', usuarioSimulado),
      tarea('tarea-evaluar', 'Evaluar Recursos', tareas.evaluarRecursos, 'instancia-4', null, [grupoAdministracion]),
      tarea('tarea-evaluar-tomada', 'Evaluar Recursos', tareas.evaluarRecursos, 'instancia-5', 'empleadoAdministrativo', [grupoAdministracion]),
      tarea('tarea-lista', 'Confeccionar lista definitiva', tareas.confeccionarLista, 'instancia-6', usuarioSimulado),
      tarea('tarea-informe', 'Enviar informe a empresa y Archivar expediente', tareas.enviarInforme, 'instancia-7', usuarioSimulado),
    ],
    instancias: {
      'instancia-1': [...solicitud],
      'instancia-2': [...solicitud, ...propuesta, variable('observaciones', 'string', 'Reducir el presupuesto')],
      'instancia-3': [...solicitud, variable('resultado', 'string', 'rechazado')],
      'instancia-4': [...solicitud, ...propuesta],
      'instancia-5': [...solicitud, ...propuesta],
      'instancia-6': [...solicitud, ...propuesta, variable('participantes', 'string', 'Ana Pérez, Juan Gómez')],
      'instancia-7': [...solicitud, ...propuesta, variable(nombres.informeResultados, 'string', 'El curso se dictó con normalidad.')],
    },
  };
}

let datos = crearDatosIniciales();

/**
 * Vuelve los datos a su estado inicial.
 * No recibe ni devuelve nada.
 */
export function reiniciarMock() {
  datos = crearDatosIniciales();
}

/**
 * Arma una respuesta simulada.
 * Recibe: status y cuerpo opcional. Devuelve: { estado, cuerpo }.
 */
function responder(estado, cuerpo = null) {
  // Se copia para que quien la recibe no pueda modificar los datos internos.
  return { estado, cuerpo: structuredClone(cuerpo) };
}

/**
 * Arma el 500 que devuelve Flowable cuando falla la tarea.
 * Recibe: texto de la excepción. Devuelve: la respuesta simulada.
 */
function errorInterno(excepcion) {
  return responder(500, { message: 'Internal server error', exception: excepcion });
}

/**
 * Arma el texto del error de casteo de Java.
 * Recibe: clase recibida y clase esperada, con su paquete. Devuelve: el texto.
 */
function textoCasteo(recibida, esperada) {
  return `class ${recibida} cannot be cast to class ${esperada} (${recibida} and ${esperada} are in module java.base of loader 'bootstrap')`;
}

/**
 * Busca el rechazo que daría ValidateProposalDates, en el mismo orden que el listener.
 * Recibe: variables nuevas que se quieren guardar en la instancia.
 * Devuelve: la respuesta 500, o null si la propuesta es válida.
 */
function validarPropuesta(nuevas) {
  const buscar = (nombre) => nuevas.find((item) => item.name === nombre);
  const presupuesto = buscar(nombres.presupuesto);
  const inicio = buscar(nombres.fechaInicioPosible);
  const fin = buscar(nombres.fechaFinPropuesta);

  // Los casteos de Java fallan antes de llegar a cualquier validación.
  if (presupuesto && presupuesto.type === 'integer') {
    return errorInterno(textoCasteo('java.lang.Integer', 'java.lang.Long'));
  }
  if ((inicio && inicio.type !== 'date') || (fin && fin.type !== 'date')) {
    return errorInterno(textoCasteo('java.lang.String', 'java.util.Date'));
  }

  if (!presupuesto) {
    return errorInterno('El presupuesto es obligatorio y debe ser un número mayor o igual a 0.');
  }
  if (presupuesto.value < 0) {
    return errorInterno('El presupuesto debe ser mayor o igual a 0.');
  }
  if (!inicio || !fin) {
    return errorInterno('Las fechas posibles de inicio y finalización son obligatorias.');
  }
  if (new Date(fin.value) < new Date(inicio.value)) {
    return errorInterno('La fecha de finalización debe ser posterior o igual a la fecha de inicio.');
  }
  return null;
}

/**
 * Simula GET de tareas.
 * Recibe: parámetros de la consulta.
 * Devuelve: la respuesta con el sobre de paginación de Flowable.
 */
function listar(parametros) {
  const usuario = parametros.get('assignee');
  const grupo = parametros.get('candidateGroup');

  const encontradas = datos.tareas.filter((item) => {
    if (usuario && item.assignee !== usuario) return false;
    // Como en Flowable, una tarea ya reclamada deja de estar disponible para el grupo.
    if (grupo && (item.assignee !== null || !item.candidateGroups.includes(grupo))) return false;
    return true;
  });

  return responder(200, {
    data: encontradas,
    total: encontradas.length,
    start: 0,
    sort: 'id',
    order: 'asc',
    size: encontradas.length,
  });
}

/**
 * Simula reclamar una tarea.
 * Recibe: la tarea. Devuelve: 200 sin cuerpo, o 409 si la tiene otra persona.
 */
function reclamar(item) {
  if (item.assignee !== null && item.assignee !== usuarioSimulado) {
    return responder(409, {
      message: 'Task was already claimed',
      exception: `Task '${item.id}' is already claimed by someone else.`,
    });
  }
  item.assignee = usuarioSimulado;
  return responder(200);
}

/**
 * Simula completar una tarea.
 * Recibe: la tarea y las variables enviadas.
 * Devuelve: 200 sin cuerpo, o 500 si el listener rechaza.
 */
function completar(item, nuevas = []) {
  // Solo Registrar tiene el listener en el BPMN actual.
  if (item.taskDefinitionKey === tareas.registrarPropuesta) {
    const rechazo = validarPropuesta(nuevas);
    // Se corta antes de escribir: tras un rechazo no cambia nada, como en la transacción real.
    if (rechazo) return rechazo;
  }

  const instancia = datos.instancias[item.processInstanceId];
  for (const { name, type, value } of nuevas) {
    const sinRepetir = instancia.findIndex((existente) => existente.name === name);
    if (sinRepetir >= 0) instancia.splice(sinRepetir, 1);
    instancia.push(variable(name, type, value));
  }
  datos.tareas = datos.tareas.filter((existente) => existente.id !== item.id);
  return responder(200);
}

/**
 * Transporte simulado, con la misma firma que enviarReal.
 * Recibe: método HTTP, ruta y cuerpo opcional.
 * Devuelve: promesa de { estado, cuerpo }.
 */
export async function enviarSimulado(metodo, ruta, cuerpo) {
  // URL pide una base absoluta; el dominio no se usa.
  const url = new URL(ruta, 'http://mock.local');
  const partes = url.pathname.slice(baseApi.length + 1).split('/').map(decodeURIComponent);

  if (metodo === 'GET' && partes.length === 1 && partes[0] === 'tareas') {
    return listar(url.searchParams);
  }

  if (metodo === 'POST' && partes.length === 2 && partes[0] === 'tareas') {
    const item = datos.tareas.find((existente) => existente.id === partes[1]);
    if (!item) {
      return responder(404, { message: 'Not found', exception: `Could not find a task with id '${partes[1]}'.` });
    }
    if (cuerpo?.action === 'claim') return reclamar(item);
    if (cuerpo?.action === 'complete') return completar(item, cuerpo.variables);
    return responder(400, { message: 'Bad request', exception: `Invalid action: '${cuerpo?.action}'.` });
  }

  if (metodo === 'GET' && partes.length === 3 && partes[0] === 'instancias' && partes[2] === 'variables') {
    const instancia = datos.instancias[partes[1]];
    if (!instancia) {
      return responder(404, { message: 'Not found', exception: `Could not find a process instance with id '${partes[1]}'.` });
    }
    return responder(200, instancia);
  }

  return responder(404, { message: 'Not found', exception: `Ruta no simulada: ${metodo} ${ruta}` });
}
