/**
 * Módulo contrato.js
 *
 * Reúne todo lo que depende de acuerdos con el grupo: el modo del adaptador,
 * las rutas de la API, los ids de las tareas y los nombres y tipos de las
 * variables. Si el grupo decide otro nombre, se cambia solo acá.
 *
 * No tiene lógica ni credenciales: solo exporta constantes y una función que
 * arma rutas.
 */

/**
 * Único interruptor del adaptador.
 * 'mock': usa los datos simulados de mock.js.
 * 'real': llama al backend con fetch.
 */
export const modoApi = 'mock';

/**
 * Prefijo de la API del backend.
 * El navegador nunca habla directo con Flowable (ver docs/contrato.md).
 *
 * En modo 'real' estas rutas tienen que ser siempre las del backend (/api/...),
 * nunca las de Flowable: el adaptador reclama sin mandar assignee porque el
 * backend lo toma de la sesión, y un claim sin assignee contra Flowable libera
 * la tarea en lugar de tomarla.
 */
export const baseApi = '/api';

/**
 * Alcances de GET /api/tareas, en minúscula como los espera el backend.
 * mias: asignadas al usuario de la sesión. grupo: sin asignar, de sus grupos.
 */
export const alcances = {
  mias: 'mias',
  grupo: 'grupo',
};

/** Tamaño de página que se pide al listar: es el máximo que acepta el backend. */
export const tamanioPagina = 100;

/**
 * Arma las rutas de la API del backend (tareas: lectura en la #56, acciones en
 * la #57). El usuario nunca va en la ruta ni en el cuerpo: el backend lo toma de
 * la sesión.
 */
export const rutasApi = {
  /**
   * Ruta para listar tareas.
   * Recibe: { alcance, start, size }; alcance es obligatorio ('mias' o 'grupo').
   * Devuelve: la ruta con su query string.
   */
  tareas({ alcance, start = 0, size = tamanioPagina }) {
    const parametros = new URLSearchParams({ alcance, start: String(start), size: String(size) });
    return `${baseApi}/tareas?${parametros}`;
  },

  /**
   * Ruta de una tarea (GET para leerla).
   * Recibe: id de la tarea. Devuelve: la ruta.
   */
  tarea(id) {
    return `${baseApi}/tareas/${encodeURIComponent(id)}`;
  },

  /**
   * Ruta para reclamar una tarea (POST sin cuerpo).
   * Recibe: id de la tarea. Devuelve: la ruta.
   */
  reclamar(id) {
    return `${rutasApi.tarea(id)}/reclamar`;
  },

  /**
   * Ruta para completar una tarea (POST con { variables }).
   * Recibe: id de la tarea. Devuelve: la ruta.
   */
  completar(id) {
    return `${rutasApi.tarea(id)}/completar`;
  },

  /**
   * Ruta para leer las variables de una instancia.
   * Recibe: id de la instancia. Devuelve: la ruta.
   */
  variables(instanciaId) {
    return `${baseApi}/instancias/${encodeURIComponent(instanciaId)}/variables`;
  },
};

/** Ids de las tareas de Administración, tal como están en el BPMN. */
export const tareas = {
  registrarPropuesta: 'sid-F96A6968-13D6-4EA4-929E-69230127C208',
  revisarPropuesta: 'sid-BE500ABC-83C7-4A8A-9C83-23183950A2F6',
  archivarSolicitud: 'sid-DA4178CD-28E4-4E74-A085-B6D7BE829D62',
  evaluarRecursos: 'sid-F3381B5B-C506-4AC2-A09D-747447EFB31B',
  confeccionarLista: 'sid-67285B9F-672D-4038-A765-E67245B387D1',
  enviarInforme: 'sid-BA739FA3-D40A-4AFD-AD69-062B808ABF63',
};

/** Grupo de Flowable del personal administrativo. */
export const grupoAdministracion = 'Administracion';

/**
 * Nombres de las variables que envían las pantallas.
 * Los marcados como PENDIENTE son propuestas que el grupo todavía no acordó.
 */
export const variables = {
  programa: 'programa',
  presupuesto: 'presupuesto',
  fechaInicioPosible: 'fechaInicioPosible',
  fechaFinPropuesta: 'fechaFinPropuesta',
  aulaFisica: 'aulaFisica',
  aulaVirtual: 'aulaVirtual',
  equipamiento: 'equipamiento',
  listaDefinitiva: 'listaDefinitiva', // PENDIENTE: nombre a acordar
  informeResultados: 'informeResultados', // PENDIENTE: nombre a acordar
};

/**
 * Tipo de Flowable de cada variable que se puede enviar al completar.
 * El tipo siempre va explícito: los listeners castean a Long y a Date, y un
 * número mandado como "integer" hace fallar la tarea con un error interno.
 */
export const tiposVariables = {
  [variables.programa]: 'string',
  [variables.presupuesto]: 'long',
  [variables.fechaInicioPosible]: 'date',
  [variables.fechaFinPropuesta]: 'date',
  [variables.aulaFisica]: 'boolean',
  [variables.aulaVirtual]: 'boolean',
  [variables.equipamiento]: 'boolean',
  [variables.listaDefinitiva]: 'string',
  [variables.informeResultados]: 'string',
};

/**
 * Mismo texto que flowable.resources.ValidateResourceSelection. La pantalla de
 * evaluar recursos lo usa para anticipar la regla antes de enviar, así el aviso
 * es igual tanto si lo detecta el navegador como si lo rechaza el motor.
 */
export const mensajeSinRecursos = 'Debe seleccionar al menos un recurso.';
