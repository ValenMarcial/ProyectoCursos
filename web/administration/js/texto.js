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

/**
 * Formatea la fecha que acordó la empresa (fechaInicio, la manda al aceptar).
 * Recibe: texto ISO-8601, como 2027-03-08T09:00:00.
 * Devuelve: texto dd/MM/yyyy, o el valor original si no tiene ese formato.
 */
export function formatearFechaAcordada(fecha) {
  // Se toma el texto tal cual: convertirlo a Date podría correr el día por la zona horaria.
  const coincidencia = /^(\d{4})-(\d{2})-(\d{2})/.exec(String(fecha));
  return coincidencia ? `${coincidencia[3]}/${coincidencia[2]}/${coincidencia[1]}` : String(fecha);
}
