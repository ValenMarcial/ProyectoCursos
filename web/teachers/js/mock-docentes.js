/**
 * Módulo mock-docentes.js
 *
 * Simula la API del backend para las pantallas del equipo docente, con datos en
 * memoria. Responde igual que mock.js de Administración (y que el backend): misma
 * ruta, mismo cuerpo y mismos errores, así api-docentes.js corre el mismo código
 * en modo mock y en modo real.
 *
 * Diferencias con mock.js de Administración:
 * - El usuario de la sesión es un integrante del grupo Docentes y se puede cambiar
 *   (?usuario=docente1), para probar las pantallas del jefe y las de un docente.
 * - Simula ValidateSelectedTeacher al completar "Definir Docente".
 *
 * Como el de Administración, no simula el avance del proceso: al completar, la
 * tarea solo desaparece. En el proceso real, "Desarrollar curso" aparece recién
 * cuando llega la fecha de inicio (timer "Fecha programada").
 */

import { baseApi, tiposVariables } from '../../administration/js/contrato.js';
import {
  claveUsuarioSimulado, grupoDocentes, integrantesDocentes, jefeDocente, mensajesSeleccion,
  parametroUsuarioSimulado, tareasDocentes, variables as nombres,
} from './contrato-docentes.js';

/** Usuarios del mock: los tres integrantes del grupo Docentes. */
const usuariosSimulados = integrantesDocentes.map(({ usuario }) => usuario);

// Mensajes del backend (TareaException.Motivo y SesionException.Motivo), copiados
// de mock.js de Administración para responder igual.
const mensajes = {
  alcanceInvalido: 'El alcance tiene que ser "mias" o "grupo".',
  tareaNoEncontrada: 'La tarea no existe o no tenés permiso para verla.',
  instanciaNoEncontrada: 'La instancia no existe o no tenés tareas activas en ella.',
  noAutorizada: 'La tarea no es tuya ni de tu grupo.',
  noAsignada: 'La tarea no está asignada a vos: reclamala primero.',
  variableInvalida: 'Variable no permitida o con tipo o valor incorrecto:',
  datosInvalidos: 'Ingresá el usuario y la clave. El usuario no puede contener ":".',
  credencialesInvalidas: 'Usuario o clave incorrectos.',
  sinSesion: 'No hay una sesión iniciada.',
};

/**
 * Elige el usuario simulado: ?usuario= de la URL (y lo recuerda en la pestaña),
 * si no el recordado, y si no el jefe docente.
 * No recibe nada. Devuelve: el id de usuario.
 */
function elegirUsuarioSimulado() {
  const pedido = new URLSearchParams(globalThis.location?.search ?? '').get(parametroUsuarioSimulado);
  try {
    if (usuariosSimulados.includes(pedido)) {
      globalThis.sessionStorage?.setItem(claveUsuarioSimulado, pedido);
      return pedido;
    }
    const guardado = globalThis.sessionStorage?.getItem(claveUsuarioSimulado);
    if (usuariosSimulados.includes(guardado)) return guardado;
  } catch {
    // Sin storage (modo privado o Node): vale lo de la URL o el jefe.
    if (usuariosSimulados.includes(pedido)) return pedido;
  }
  return jefeDocente;
}

let usuarioSimulado = elegirUsuarioSimulado();
let sesionActiva = true;

/** Devuelve el usuario de la sesión simulada (para mostrarlo en la bandeja). */
export function usuarioSimuladoActual() {
  return usuarioSimulado;
}

/** Crea una variable en el formato de la API. */
function variable(nombre, tipo, valor) {
  return { name: nombre, type: tipo, value: valor };
}

/**
 * Crea una tarea simulada. Todas las tareas docentes van asignadas a un usuario
 * (al jefe por el BPMN, o al docente elegido), así que no tienen grupos candidatos.
 */
function tarea(id, nombre, clave, instancia, asignado, minuto) {
  return {
    id,
    name: nombre,
    assignee: asignado,
    taskDefinitionKey: clave,
    processInstanceId: instancia,
    createTime: `2026-11-12T12:${String(minuto).padStart(2, '0')}:00.000Z`,
    dueDate: null,
    gruposCandidatos: [],
  };
}

/**
 * Variables de un curso ya preparado, como las deja el proceso real.
 * Recibe: empresa, temática, programa, fecha de inicio y participantes.
 * Devuelve: la lista de variables.
 */
function curso(empresa, tematica, programa, inicio, participantes, recursos) {
  return [
    variable('empresaNombre', 'string', empresa),
    variable('tematica', 'string', tematica),
    variable('descripcion', 'string', `Curso de ${tematica} para el equipo de ${empresa}`),
    variable(nombres.programa, 'string', programa),
    // La empresa la manda como texto ISO al aceptar (ver el simulador).
    variable('fechaInicio', 'string', inicio),
    variable(nombres.fechaFinPropuesta, 'date', '2027-03-31T00:00:00Z'),
    variable(nombres.listaDefinitiva, 'string', participantes),
    variable(nombres.aulaFisica, 'boolean', recursos.includes('fisica')),
    variable(nombres.aulaVirtual, 'boolean', recursos.includes('virtual')),
    variable(nombres.equipamiento, 'boolean', recursos.includes('equipamiento')),
  ];
}

/**
 * Arma los datos de ejemplo: una tarea de cada tipo, repartidas entre el jefe y un docente.
 * No recibe nada. Devuelve: { tareas, instancias }.
 */
function crearDatosIniciales() {
  return {
    tareas: [
      tarea('tarea-definir', 'Definir Docente que desarrolla el curso', tareasDocentes.definirDocente, 'curso-1', jefeDocente, 1),
      tarea('tarea-desarrollar-jefe', 'Desarrollar curso', tareasDocentes.desarrollarCurso, 'curso-2', jefeDocente, 2),
      tarea('tarea-desarrollar-docente', 'Desarrollar curso', tareasDocentes.desarrollarCurso, 'curso-3', 'docente1', 3),
      tarea('tarea-informe', 'Elaborar informe de resultados', tareasDocentes.elaborarInforme, 'curso-4', jefeDocente, 4),
    ],
    instancias: {
      'curso-1': curso('Tech Solutions S.A.', 'Capacitación BPMN', 'Modelado de procesos',
        '2027-03-08T09:00:00-03:00', 'Ana Pérez, Juan Gómez, Sofía Díaz', ['fisica', 'equipamiento']),
      'curso-2': curso('Logística del Sur', 'Excel avanzado', 'Tablas dinámicas y macros',
        '2027-02-15T14:00:00-03:00', 'Lucía Fernández, Pedro Ruiz', ['virtual']),
      'curso-3': curso('Agro Norte', 'Seguridad informática', 'Buenas prácticas y phishing',
        '2027-02-20T10:00:00-03:00', 'Marta Gil, Raúl Sosa, Inés Vega', ['fisica']),
      'curso-4': curso('Banco Central Sur', 'Atención al cliente', 'Comunicación y reclamos',
        '2027-01-10T09:00:00-03:00', 'Laura Díaz, Tomás Ríos', ['fisica', 'virtual']),
    },
  };
}

let datos = crearDatosIniciales();

/**
 * Vuelve los datos a su estado inicial.
 * Recibe: opcionalmente el usuario simulado (para pruebas). No devuelve nada.
 */
export function reiniciarMockDocentes(usuario = jefeDocente) {
  datos = crearDatosIniciales();
  usuarioSimulado = usuario;
  sesionActiva = true;
}

/** Arma una respuesta simulada. La copia evita que se modifiquen los datos internos. */
function responder(estado, cuerpo = null) {
  return { estado, cuerpo: structuredClone(cuerpo) };
}

/** Error del backend: { message } en español. */
function errorBackend(estado, mensaje) {
  return responder(estado, { message: mensaje });
}

/** 400 de un listener del motor (FlowableIllegalArgumentException) reenviado por el backend. */
function errorNegocio(excepcion) {
  return responder(400, { message: 'Bad request', exception: excepcion });
}

/** La tarea sin los datos internos del mock, como la devuelve la API. */
function publica({ gruposCandidatos, ...resto }) {
  return resto;
}

function esSuya(item) {
  return item.assignee === usuarioSimulado;
}

function esDeSuGrupo(item) {
  return item.gruposCandidatos.includes(grupoDocentes);
}

/** Indica si un valor JSON corresponde al tipo de Flowable, como lo valida el backend. */
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

/** Valida las variables como el backend: lista de escritura, tipo, valor y sin repetidas. */
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
 * Busca el rechazo que daría ValidateSelectedTeacher, en el mismo orden que el listener.
 * Recibe: variables nuevas. Devuelve: la respuesta 400, o null si el docente es válido.
 */
function validarSeleccion(nuevas) {
  const elegido = nuevas.find((item) => item.name === nombres.docenteSeleccionado)?.value;
  if (typeof elegido !== 'string' || elegido.trim() === '') {
    return errorNegocio(mensajesSeleccion.sinDocente);
  }
  return usuariosSimulados.includes(elegido) ? null : errorNegocio(mensajesSeleccion.fueraDelGrupo);
}

/** Simula GET /api/tareas?alcance=mias|grupo&start&size. */
function listar(parametros) {
  const alcance = parametros.get('alcance');
  if (alcance !== 'mias' && alcance !== 'grupo') {
    return errorBackend(400, mensajes.alcanceInvalido);
  }
  const start = Number(parametros.get('start') ?? 0);
  const size = Number(parametros.get('size') ?? 50);
  const encontradas = datos.tareas
    .filter((item) => (alcance === 'mias' ? esSuya(item) : item.assignee === null && esDeSuGrupo(item)))
    .sort((a, b) => a.createTime.localeCompare(b.createTime));
  return responder(200, {
    data: encontradas.slice(start, start + size).map(publica),
    total: encontradas.length,
    start,
    size,
  });
}

/** Simula GET /api/tareas/{id}. */
function leer(item) {
  if (!item || !(esSuya(item) || esDeSuGrupo(item))) {
    return errorBackend(404, mensajes.tareaNoEncontrada);
  }
  return responder(200, publica(item));
}

/** Simula POST /api/tareas/{id}/reclamar (las tareas docentes ya vienen asignadas). */
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

/** Simula POST /api/tareas/{id}/completar, en el orden del backend. */
function completar(id, item, nuevas = []) {
  const invalida = validarEscritura(nuevas);
  if (invalida) return invalida;
  if (!item) return errorBackend(404, mensajes.tareaNoEncontrada);
  if (!esSuya(item)) {
    return errorBackend(403, esDeSuGrupo(item) ? mensajes.noAsignada : mensajes.noAutorizada);
  }
  if (item.taskDefinitionKey === tareasDocentes.definirDocente) {
    // Tras un rechazo no cambia nada, como en la transacción real.
    const rechazo = validarSeleccion(nuevas);
    if (rechazo) return rechazo;
  }

  const instancia = datos.instancias[item.processInstanceId];
  for (const { name, type, value } of nuevas) {
    const existente = instancia.findIndex((otra) => otra.name === name);
    if (existente >= 0) instancia.splice(existente, 1);
    instancia.push(variable(name, type, value));
  }
  datos.tareas = datos.tareas.filter((otra) => otra.id !== id);
  return responder(204);
}

/** Simula GET /api/instancias/{id}/variables: solo con una tarea activa en la instancia. */
function leerVariables(instanciaId) {
  const tieneTarea = datos.tareas.some((item) => item.processInstanceId === instanciaId
    && (esSuya(item) || (item.assignee === null && esDeSuGrupo(item))));
  const instancia = datos.instancias[instanciaId];
  if (!tieneTarea || !instancia) {
    return errorBackend(404, mensajes.instanciaNoEncontrada);
  }
  return responder(200, instancia);
}

/** Simula /api/sesion con los tres integrantes del grupo Docentes. */
function sesion(metodo, cuerpo) {
  const nombre = integrantesDocentes.find(({ usuario }) => usuario === usuarioSimulado).nombre;
  const datosSesion = { usuario: usuarioSimulado, nombre, grupos: [grupoDocentes] };
  if (metodo === 'POST') {
    if (!cuerpo?.usuario?.trim() || !cuerpo?.clave) return errorBackend(400, mensajes.datosInvalidos);
    if (!usuariosSimulados.includes(cuerpo.usuario)) return errorBackend(401, mensajes.credencialesInvalidas);
    usuarioSimulado = cuerpo.usuario;
    sesionActiva = true;
    return sesion('GET');
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
 * Transporte simulado, con la misma firma que enviarReal de api.js.
 * Recibe: método HTTP, ruta y cuerpo opcional. Devuelve: promesa de { estado, cuerpo }.
 */
export async function enviarSimuladoDocentes(metodo, ruta, cuerpo) {
  const url = new URL(ruta, 'http://mock.local');
  const partes = url.pathname.slice(baseApi.length + 1).split('/').map(decodeURIComponent);
  const [recurso, id, accion] = partes;

  if (recurso === 'sesion' && partes.length === 1) return sesion(metodo, cuerpo);
  if (!sesionActiva) return errorBackend(401, mensajes.sinSesion);

  if (recurso === 'tareas') {
    if (metodo === 'GET' && partes.length === 1) return listar(url.searchParams);
    const item = datos.tareas.find((otra) => otra.id === id);
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
  return responder(404, { status: 404, error: 'Not Found', path: url.pathname });
}
