/**
 * Módulo mock.js
 *
 * Simula la API del backend a nivel HTTP, con datos en memoria: recibe la misma
 * ruta y el mismo cuerpo que el backend y devuelve { estado, cuerpo }. Así api.js
 * ejecuta el mismo código en modo mock y en modo real.
 *
 * Respuestas copiadas de lo verificado contra el backend (PR de tareas #56 y #57)
 * y Flowable 6.8:
 * - Errores del backend: { message } en español (400 de validación, 403 sin
 *   permiso, 404 que no distingue "no existe" de "no es tuya").
 * - Errores del motor, reenviados tal cual: { message, exception } (400 de los
 *   listeners, 409 al reclamar una tarea tomada).
 * - Reclamar y completar responden 204 sin cuerpo.
 * - Tras un rechazo la tarea sigue activa sin guardar variables.
 * - Fechas como las manda Flowable: createTime con milisegundos y variables date
 *   sin milisegundos, ambas en UTC con Z.
 *
 * No simula el avance del proceso: al completar, la tarea solo desaparece.
 */

import { alcances, baseApi, grupoAdministracion, tareas, tiposVariables, variables as nombres } from './contrato.js';

/** Usuario de la sesión simulada. */
export const usuarioSimulado = 'jefeAdministrativo';

/** Grupos de la sesión simulada. */
const gruposSimulados = [grupoAdministracion];

/**
 * Hay sesión iniciada. Arranca en true para que las pantallas anden sin login;
 * DELETE /api/sesion la cierra (y todo pasa a responder 401) hasta un POST.
 * Como el resto del mock vive en memoria, se reinicia al recargar la página.
 */
let sesionActiva = true;

// Mensajes del backend (TareaException.Motivo en el backend), copiados para que el
// mock responda igual.
const mensajes = {
  alcanceInvalido: 'El alcance tiene que ser "mias" o "grupo".',
  tareaNoEncontrada: 'La tarea no existe o no tenés permiso para verla.',
  instanciaNoEncontrada: 'La instancia no existe o no tenés tareas activas en ella.',
  noAutorizada: 'La tarea no es tuya ni de tu grupo.',
  noAsignada: 'La tarea no está asignada a vos: reclamala primero.',
  variableInvalida: 'Variable no permitida o con tipo o valor incorrecto:',
  // Mensajes de SesionException.Motivo.
  datosInvalidos: 'Ingresá el usuario y la clave. El usuario no puede contener ":".',
  credencialesInvalidas: 'Usuario o clave incorrectos.',
  sinSesion: 'No hay una sesión iniciada.',
};

/**
 * Crea una variable en el formato de la API.
 * Recibe: nombre, tipo y valor. Devuelve: { name, type, value }.
 */
function variable(nombre, tipo, valor) {
  return { name: nombre, type: tipo, value: valor };
}

/**
 * Crea una tarea simulada. Los grupos candidatos quedan internos: la API no los
 * devuelve, los usa solo para decidir permisos.
 * Recibe: id, nombre, clave del BPMN, instancia, asignado, grupos candidatos y
 * minuto de creación (para ordenar como el backend, por fecha de creación).
 * Devuelve: la tarea simulada.
 */
function tarea(id, nombre, clave, instancia, asignado, grupos, minuto) {
  return {
    id,
    name: nombre,
    assignee: asignado,
    taskDefinitionKey: clave,
    processInstanceId: instancia,
    createTime: `2026-11-09T12:${String(minuto).padStart(2, '0')}:00.000Z`,
    dueDate: null,
    gruposCandidatos: grupos,
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
    variable(nombres.fechaInicioPosible, 'date', '2027-03-01T00:00:00Z'),
    variable(nombres.fechaFinPropuesta, 'date', '2027-03-31T00:00:00Z'),
  ];
  // Fecha que envía la empresa al aceptar (texto): existe desde "Preparar curso" en adelante.
  const aceptacion = variable('fechaInicio', 'string', '2027-03-08');
  const administracion = [grupoAdministracion];

  return {
    tareas: [
      tarea('tarea-registrar', 'Registrar y Elaborar propuesta', tareas.registrarPropuesta, 'instancia-1', usuarioSimulado, [], 1),
      tarea('tarea-revisar', 'Revisar propuesta', tareas.revisarPropuesta, 'instancia-2', usuarioSimulado, [], 2),
      tarea('tarea-archivar', 'Archivar solicitud', tareas.archivarSolicitud, 'instancia-3', usuarioSimulado, [], 3),
      tarea('tarea-evaluar', 'Evaluar Recursos', tareas.evaluarRecursos, 'instancia-4', null, administracion, 4),
      tarea('tarea-evaluar-tomada', 'Evaluar Recursos', tareas.evaluarRecursos, 'instancia-5', 'empleadoAdministrativo', administracion, 5),
      tarea('tarea-lista', 'Confeccionar lista definitiva', tareas.confeccionarLista, 'instancia-6', usuarioSimulado, administracion, 6),
      tarea('tarea-informe', 'Enviar informe a empresa y Archivar expediente', tareas.enviarInforme, 'instancia-7', usuarioSimulado, [], 7),
      // Caso de respaldo del listener de asignación: sin integrantes en el grupo, la
      // tarea queda sin assignee y con Administracion como candidato. Va última para
      // que la pantalla siga abriendo por defecto la tarea asignada.
      tarea('tarea-lista-sin-asignar', 'Confeccionar lista definitiva', tareas.confeccionarLista, 'instancia-8', null, administracion, 8),
      // Evaluar Recursos ya reclamada por la sesión: la pantalla muestra el formulario
      // sin pedir que se tome. Va última para no cambiar la tarea que se abre por defecto.
      tarea('tarea-evaluar-propia', 'Evaluar Recursos', tareas.evaluarRecursos, 'instancia-9', usuarioSimulado, administracion, 9),
    ],
    instancias: {
      'instancia-1': [...solicitud],
      'instancia-2': [...solicitud, ...propuesta, variable('observaciones', 'string', 'Reducir el presupuesto')],
      'instancia-3': [...solicitud, variable('resultado', 'string', 'rechazado')],
      'instancia-4': [...solicitud, ...propuesta, aceptacion],
      'instancia-5': [...solicitud, ...propuesta, aceptacion],
      'instancia-6': [...solicitud, ...propuesta, variable('participantes', 'string', 'Ana Pérez, Juan Gómez')],
      'instancia-7': [...solicitud, ...propuesta, variable(nombres.informeResultados, 'string', 'El curso se dictó con normalidad.')],
      'instancia-8': [...solicitud, ...propuesta, variable('participantes', 'string', 'Lucía Fernández, Pedro Ruiz')],
      'instancia-9': [...solicitud, ...propuesta, aceptacion],
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
  sesionActiva = true;
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
 * Arma un error del backend: { message } en español.
 * Recibe: status y mensaje. Devuelve: la respuesta simulada.
 */
function errorBackend(estado, mensaje) {
  return responder(estado, { message: mensaje });
}

/**
 * Arma el 400 que reenvía el backend cuando un listener rechaza por una regla de
 * negocio (FlowableIllegalArgumentException del motor).
 * Recibe: texto de la excepción. Devuelve: la respuesta simulada.
 */
function errorNegocio(excepcion) {
  return responder(400, { message: 'Bad request', exception: excepcion });
}

/**
 * Devuelve la tarea sin los datos internos del mock, como la devuelve la API.
 * Recibe: la tarea simulada. Devuelve: la tarea pública.
 */
function publica({ gruposCandidatos, ...resto }) {
  return resto;
}

/** Indica si la tarea está asignada al usuario de la sesión simulada. */
function esSuya(item) {
  return item.assignee === usuarioSimulado;
}

/** Indica si algún grupo de la sesión simulada es candidato de la tarea. */
function esDeSuGrupo(item) {
  return item.gruposCandidatos.some((grupo) => gruposSimulados.includes(grupo));
}

/**
 * Indica si un valor JSON corresponde al tipo de Flowable, como lo valida el backend.
 * Recibe: tipo y valor. Devuelve: boolean.
 */
function valorDelTipo(tipo, valor) {
  switch (tipo) {
    case 'string':
    case 'date':
      return typeof valor === 'string';
    case 'long':
      return Number.isInteger(valor);
    case 'boolean':
      return typeof valor === 'boolean';
    default:
      return false;
  }
}

/**
 * Valida las variables como el backend: nombre en la lista de escritura (la misma
 * que tiposVariables), mismo tipo, valor no nulo del tipo y sin repetidas.
 * Recibe: variables enviadas. Devuelve: la respuesta 400, o null si son válidas.
 */
function validarEscritura(nuevas) {
  const vistas = new Set();
  for (const item of nuevas) {
    const tipo = tiposVariables[item?.name];
    if (!tipo || item.type !== tipo || !valorDelTipo(tipo, item.value) || vistas.has(item.name)) {
      return errorBackend(400, `${mensajes.variableInvalida} ${item?.name}`);
    }
    vistas.add(item.name);
  }
  return null;
}

/**
 * Busca el rechazo que daría ValidateProposalDates, en el mismo orden que el listener.
 * Los tipos ya los validó validarEscritura, como hace el backend antes del motor.
 * Recibe: variables nuevas que se quieren guardar en la instancia.
 * Devuelve: la respuesta 400, o null si la propuesta es válida.
 */
function validarPropuesta(nuevas) {
  const buscar = (nombre) => nuevas.find((item) => item.name === nombre);
  const presupuesto = buscar(nombres.presupuesto);
  const inicio = buscar(nombres.fechaInicioPosible);
  const fin = buscar(nombres.fechaFinPropuesta);

  if (!presupuesto) {
    return errorNegocio('El presupuesto es obligatorio y debe ser un número mayor o igual a 0.');
  }
  if (presupuesto.value < 0) {
    return errorNegocio('El presupuesto debe ser mayor o igual a 0.');
  }
  if (!inicio || !fin) {
    return errorNegocio('Las fechas posibles de inicio y finalización son obligatorias.');
  }
  if (new Date(fin.value) < new Date(inicio.value)) {
    return errorNegocio('La fecha de finalización debe ser posterior o igual a la fecha de inicio.');
  }
  return null;
}

/**
 * Busca el rechazo que daría ValidateResourceSelection.
 * Recibe: variables nuevas que se quieren guardar en la instancia.
 * Devuelve: la respuesta 400, o null si hay al menos un recurso en true.
 */
function validarRecursos(nuevas) {
  const recursos = [nombres.aulaFisica, nombres.aulaVirtual, nombres.equipamiento];
  // Como Boolean.TRUE.equals en el listener: una variable ausente no cuenta como marcada.
  const hayRecurso = nuevas.some((item) => recursos.includes(item.name) && item.value === true);
  return hayRecurso ? null : errorNegocio('Debe seleccionar al menos un recurso.');
}

/**
 * Simula GET /api/tareas?alcance=mias|grupo&start&size.
 * Recibe: parámetros de la consulta.
 * Devuelve: la página { data, total, start, size }, ordenada por fecha de creación.
 */
function listar(parametros) {
  const alcance = parametros.get('alcance');
  if (alcance !== alcances.mias && alcance !== alcances.grupo) {
    return errorBackend(400, mensajes.alcanceInvalido);
  }
  const start = Number(parametros.get('start') ?? 0);
  const size = Number(parametros.get('size') ?? 50);

  const encontradas = datos.tareas
    // Como en el backend: el grupo solo ve las tareas que nadie reclamó.
    .filter((item) => (alcance === alcances.mias ? esSuya(item) : item.assignee === null && esDeSuGrupo(item)))
    .sort((a, b) => a.createTime.localeCompare(b.createTime));

  return responder(200, {
    data: encontradas.slice(start, start + size).map(publica),
    total: encontradas.length,
    start,
    size,
  });
}

/**
 * Simula GET /api/tareas/{id}: visible si es suya o de su grupo.
 * Recibe: la tarea, o undefined si no existe. Devuelve: la respuesta.
 */
function leer(item) {
  if (!item || !(esSuya(item) || esDeSuGrupo(item))) {
    return errorBackend(404, mensajes.tareaNoEncontrada);
  }
  return responder(200, publica(item));
}

/**
 * Simula POST /api/tareas/{id}/reclamar.
 * Recibe: la tarea. Devuelve: 204; 409 del motor si la tiene otra persona; 403 si
 * no es suya ni de su grupo.
 */
function reclamar(item) {
  if (esSuya(item)) return responder(204);
  if (!esDeSuGrupo(item)) return errorBackend(403, mensajes.noAutorizada);
  if (item.assignee !== null) {
    return responder(409, {
      message: 'Task was already claimed',
      exception: `Task '${item.id}' is already claimed by someone else.`,
    });
  }
  item.assignee = usuarioSimulado;
  return responder(204);
}

/**
 * Simula POST /api/tareas/{id}/completar, en el orden del backend: primero valida
 * las variables, después que exista, después que sea suya y por último el motor.
 * Recibe: el id pedido, la tarea (o undefined) y las variables enviadas.
 * Devuelve: 204, o el error que corresponda.
 */
function completar(id, item, nuevas = []) {
  const invalida = validarEscritura(nuevas);
  if (invalida) return invalida;
  if (!item) return errorBackend(404, mensajes.tareaNoEncontrada);
  if (!esSuya(item)) {
    return errorBackend(403, esDeSuGrupo(item) ? mensajes.noAsignada : mensajes.noAutorizada);
  }

  // Registrar y Evaluar Recursos son las tareas de estas pantallas con validación en el BPMN.
  // ValidateSelectedTeacher (Definir Docente) no se simula: el mock no tiene tareas de Docentes.
  let rechazo = null;
  if (item.taskDefinitionKey === tareas.registrarPropuesta) rechazo = validarPropuesta(nuevas);
  if (item.taskDefinitionKey === tareas.evaluarRecursos) rechazo = validarRecursos(nuevas);
  // Se corta antes de escribir: tras un rechazo no cambia nada, como en la transacción real.
  if (rechazo) return rechazo;

  const instancia = datos.instancias[item.processInstanceId];
  for (const { name, type, value } of nuevas) {
    const sinRepetir = instancia.findIndex((existente) => existente.name === name);
    if (sinRepetir >= 0) instancia.splice(sinRepetir, 1);
    instancia.push(variable(name, type, value));
  }
  datos.tareas = datos.tareas.filter((existente) => existente.id !== id);
  return responder(204);
}

/**
 * Simula GET /api/instancias/{id}/variables: solo si la sesión tiene una tarea
 * activa en la instancia (suya, o sin asignar de su grupo).
 * Recibe: id de la instancia. Devuelve: la lista de variables o el 404.
 */
function leerVariables(instanciaId) {
  const tieneTarea = datos.tareas.some((item) => item.processInstanceId === instanciaId
    && (esSuya(item) || (item.assignee === null && esDeSuGrupo(item))));
  const instancia = datos.instancias[instanciaId];
  if (!tieneTarea || !instancia) {
    return errorBackend(404, mensajes.instanciaNoEncontrada);
  }
  return responder(200, instancia);
}

/**
 * Simula /api/sesion. Solo existe el usuario simulado; la clave no se revisa,
 * pero no puede faltar (como en el backend).
 * Recibe: método y cuerpo. Devuelve: la respuesta.
 */
function sesion(metodo, cuerpo) {
  const datosSesion = { usuario: usuarioSimulado, nombre: 'Jefe administrativo', grupos: gruposSimulados };
  if (metodo === 'POST') {
    if (!cuerpo?.usuario?.trim() || !cuerpo?.clave) return errorBackend(400, mensajes.datosInvalidos);
    if (cuerpo.usuario !== usuarioSimulado) return errorBackend(401, mensajes.credencialesInvalidas);
    sesionActiva = true;
    return responder(200, datosSesion);
  }
  if (!sesionActiva) return errorBackend(401, mensajes.sinSesion);
  if (metodo === 'GET') return responder(200, datosSesion);
  if (metodo === 'DELETE') {
    sesionActiva = false;
    return responder(204);
  }
  return responder(405, { status: 405, error: 'Method Not Allowed' });
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
  const [recurso, id, accion] = partes;

  if (recurso === 'sesion' && partes.length === 1) return sesion(metodo, cuerpo);
  // Como SesionInterceptor: sin sesión, el resto de la API responde 401.
  if (!sesionActiva) return errorBackend(401, mensajes.sinSesion);

  if (recurso === 'tareas') {
    if (metodo === 'GET' && partes.length === 1) return listar(url.searchParams);
    const item = datos.tareas.find((existente) => existente.id === id);
    if (metodo === 'GET' && partes.length === 2) return leer(item);
    if (metodo === 'POST' && partes.length === 3 && accion === 'reclamar') {
      return item ? reclamar(item) : errorBackend(404, mensajes.tareaNoEncontrada);
    }
    if (metodo === 'POST' && partes.length === 3 && accion === 'completar') {
      return completar(id, item, cuerpo?.variables ?? []);
    }
  }

  if (recurso === 'instancias' && metodo === 'GET' && partes.length === 3 && accion === 'variables') {
    return leerVariables(id);
  }

  // Igual que Spring: una ruta o método que no existe no trae { message }.
  return responder(404, { status: 404, error: 'Not Found', path: url.pathname });
}
