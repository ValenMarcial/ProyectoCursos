/**
 * Módulo pantallas.js
 *
 * Mapa de cada tarea del BPMN (taskDefinitionKey) a la pantalla del sitio que la
 * resuelve. Lo usa la bandeja para armar el enlace de cada tarea.
 *
 * Los ids salen de los contratos de cada sección: acá no se copia ninguno.
 * Son funciones puras (sin DOM), así se pueden probar con Node.
 */

import { tareas } from '../../administration/js/contrato.js';
import { tareasDocentes } from '../../teachers/js/contrato-docentes.js';

/**
 * Pantalla de cada tarea, con ruta absoluta desde la raíz del sitio.
 * null: la tarea existe pero todavía no tiene pantalla y se completa desde Flowable.
 * Una tarea que no figura acá se trata igual que null.
 */
export const pantallasPorTarea = {
  [tareas.registrarPropuesta]: '/administration/registrar-propuesta.html',
  [tareas.revisarPropuesta]: null,
  [tareas.archivarSolicitud]: null,
  [tareas.evaluarRecursos]: '/administration/evaluar-recursos.html',
  [tareas.confeccionarLista]: '/administration/lista-definitiva.html',
  [tareas.enviarInforme]: null,
  [tareasDocentes.definirDocente]: '/teachers/definir-docente.html',
  [tareasDocentes.desarrollarCurso]: '/teachers/desarrollar-curso.html',
  // El jefe del equipo docente la hace desde Flowable, adjuntando el informe en PDF
  // (el sitio es opcional para esta tarea, enunciado 4.1.3).
  [tareasDocentes.elaborarInforme]: null,
};

/** Parámetro con el que las pantallas reciben la tarea a abrir (?tarea=<id>). */
export const parametroTarea = 'tarea';

/**
 * Arma el enlace a la pantalla de una tarea.
 * Recibe: la tarea normalizada por el adaptador ({ id, claveTarea }).
 * Devuelve: la ruta con ?tarea=<id>, o null si la tarea no tiene pantalla.
 */
export function enlaceDeTarea(tarea) {
  const pantalla = Object.hasOwn(pantallasPorTarea, tarea.claveTarea) ? pantallasPorTarea[tarea.claveTarea] : null;
  if (pantalla === null) return null;
  return `${pantalla}?${new URLSearchParams({ [parametroTarea]: tarea.id })}`;
}
