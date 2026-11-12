/**
 * Módulo avisos.js
 *
 * Avisos de error, de éxito o neutros que comparten las pantallas del personal.
 * El texto siempre va con textContent: nunca se interpreta como HTML.
 *
 * Los estilos (.aviso, .aviso-error, .aviso-exito) están en administracion.css.
 */

import { ErrorApi } from '../../administration/js/api.js';

/** Texto para cualquier error que no sea un ErrorApi (un bug, por ejemplo). */
export const mensajeGenerico = 'Ocurrió un error inesperado. Intentá de nuevo más tarde.';

/**
 * Elige el texto que ve el usuario para un error atrapado.
 * Solo el message de un ErrorApi es apto para mostrar; cualquier otro error
 * puede traer texto técnico.
 * Recibe: el error. Devuelve: el texto a mostrar.
 */
export function mensajeParaUsuario(error) {
  return error instanceof ErrorApi ? error.message : mensajeGenerico;
}

/**
 * Muestra un aviso y lo trae a la vista.
 * Recibe: el elemento del aviso, el texto y el tipo ('error', 'exito' o 'neutro').
 * No devuelve nada.
 */
export function mostrarAviso(aviso, texto, tipo) {
  aviso.textContent = texto;
  aviso.className = `aviso aviso-${tipo}`;
  aviso.hidden = false;
  aviso.scrollIntoView({ block: 'nearest' });
}

/**
 * Oculta el aviso, por ejemplo antes de reintentar una operación.
 * Recibe: el elemento del aviso. No devuelve nada.
 */
export function ocultarAviso(aviso) {
  aviso.hidden = true;
  aviso.textContent = '';
}

/**
 * Muestra un error atrapado sin exponer texto técnico: el detalle va a la consola.
 * Recibe: el elemento del aviso y el error. No devuelve nada.
 */
export function mostrarError(aviso, error) {
  console.error(error, error?.detalle ?? '');
  mostrarAviso(aviso, mensajeParaUsuario(error), 'error');
}
