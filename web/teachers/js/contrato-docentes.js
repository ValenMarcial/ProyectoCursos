/**
 * Módulo contrato-docentes.js
 *
 * Lo que depende de acuerdos con el grupo y es propio del equipo docente: ids de
 * las tareas, los integrantes del grupo Docentes y los usuarios del modo simulado.
 * Las rutas de la API, el modo del adaptador y los tipos de las variables son los
 * mismos de Administración y se toman de su contrato.js, para no mantener dos copias.
 *
 * No tiene lógica ni credenciales: solo exporta constantes.
 */

export { variables } from '../../administration/js/contrato.js';

/** Ids de las tareas del equipo docente, tal como están en el BPMN. */
export const tareasDocentes = {
  definirDocente: 'sid-A3B3F53F-1D5E-42AA-AAC0-C69120F53E85',
  desarrollarCurso: 'sid-378C089F-415C-4EC6-B426-E2D989460B8C',
  elaborarInforme: 'sid-8EE040F3-0293-488C-AE3F-3BD79DB56897',
};

/** Grupo de Flowable del equipo docente. */
export const grupoDocentes = 'Docentes';

/** Usuario de Flowable del jefe del equipo docente (asignado en el BPMN). */
export const jefeDocente = 'jefeDeEquipo';

/**
 * Los tres integrantes del grupo Docentes, como los crea la seed
 * (flowable/seed/FlowableSeed.java). El jefe puede elegirse a sí mismo.
 *
 * Están fijos porque el backend no tiene una ruta que liste los integrantes de un
 * grupo. No es un riesgo: si alguien manda otro usuario, el motor lo rechaza con
 * ValidateSelectedTeacher ("El docente seleccionado debe pertenecer al grupo Docentes").
 * Si se agrega esa ruta, esta lista se reemplaza por la consulta.
 */
export const integrantesDocentes = [
  { usuario: jefeDocente, nombre: 'Jefe del equipo docente' },
  { usuario: 'docente1', nombre: 'Docente 1' },
  { usuario: 'docente2', nombre: 'Docente 2' },
];

/** Mismos textos que flowable.assignment.ValidateSelectedTeacher. */
export const mensajesSeleccion = {
  sinDocente: 'Debe indicar el ID del docente seleccionado.',
  fueraDelGrupo: 'El docente seleccionado debe pertenecer al grupo Docentes.',
};

/**
 * Parámetro de la URL que elige el usuario del modo simulado (?usuario=docente1).
 * Solo existe en modo mock: en modo real el usuario sale de la sesión del backend.
 */
export const parametroUsuarioSimulado = 'usuario';

/** Clave de sessionStorage donde queda el usuario simulado elegido en la pestaña. */
export const claveUsuarioSimulado = 'docentes.usuarioSimulado';
