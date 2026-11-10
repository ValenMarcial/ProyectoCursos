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
 */
export const baseApi = '/api';

/**
 * Arma las rutas de la API.
 * PROVISORIAS: el backend todavía no tiene estos endpoints. Se propone que
 * repitan el formato de la API REST de Flowable, ya verificado en la Fase 0.
 */
export const rutasApi = {
  /**
   * Ruta para listar tareas.
   * Recibe: filtros { usuario, grupo }, ambos opcionales.
   * Devuelve: la ruta con su query string.
   */
  tareas({ usuario, grupo } = {}) {
    const parametros = new URLSearchParams();
    if (usuario) parametros.set('assignee', usuario);
    if (grupo) parametros.set('candidateGroup', grupo);
    const consulta = parametros.toString();
    return `${baseApi}/tareas${consulta ? `?${consulta}` : ''}`;
  },

  /**
   * Ruta para actuar sobre una tarea (reclamar o completar).
   * Recibe: id de la tarea. Devuelve: la ruta.
   */
  tarea(id) {
    return `${baseApi}/tareas/${encodeURIComponent(id)}`;
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

/** Mensaje de flowable.resources.ValidateResourceSelection; la pantalla anticipa la misma regla. */
export const mensajeSinRecursos = 'Debe seleccionar al menos un recurso.';

/**
 * Mensajes de los listeners que se pueden mostrar tal cual al usuario.
 * Copiados de flowable.proposal.ValidateProposalDates y de
 * flowable.resources.ValidateResourceSelection.
 *
 * PROVISORIA: se reemplaza cuando el backend traduzca los errores. Hace falta
 * porque Flowable devuelve 500 tanto para validaciones de negocio como para
 * bugs, y el status no permite distinguirlos.
 */
export const mensajesNegocio = new Set([
  'El presupuesto es obligatorio y debe ser un número mayor o igual a 0.',
  'El presupuesto debe ser mayor o igual a 0.',
  'Las fechas posibles de inicio y finalización son obligatorias.',
  'La fecha de finalización debe ser posterior o igual a la fecha de inicio.',
  mensajeSinRecursos,
]);
