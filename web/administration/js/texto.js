/**
 * Módulo texto.js
 *
 * Utilidades de texto que comparten varias pantallas. No importa ningún otro
 * módulo ni usa el DOM, así que lo puede usar cualquiera sin crear dependencias
 * circulares.
 */

/**
 * Quita los acentos y demás signos diacríticos de un texto.
 * NFD separa cada letra de su signo (é pasa a e + ´), y el reemplazo borra los
 * signos del bloque Unicode de diacríticos combinables (U+0300 a U+036F).
 * Ojo: la ñ también pierde su tilde y queda como n.
 * Recibe: texto. Devuelve: el texto sin diacríticos ("Pérez" da "Perez").
 */
export function quitarAcentos(texto) {
  return String(texto ?? '').normalize('NFD').replace(/[\u0300-\u036f]/g, '');
}

/**
 * Formatea un presupuesto para mostrarlo.
 * Recibe: número entero. Devuelve: texto, por ejemplo "$ 150.000".
 */
export function formatearPresupuesto(presupuesto) {
  return `$ ${new Intl.NumberFormat('es-AR').format(presupuesto)}`;
}
