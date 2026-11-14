/**
 * Módulo pantalla-docentes.js
 *
 * Funciones que comparten las pantallas del equipo docente: datos del curso,
 * búsqueda de la tarea y la marca de modo simulado. Los avisos son los comunes
 * del personal (common/js/avisos.js) y se reexportan desde acá. Los datos se
 * muestran siempre con textContent: nunca se interpretan como HTML.
 */

import { listarTareas, modoActual } from './api-docentes.js';
import { integrantesDocentes, variables } from './contrato-docentes.js';

export { mostrarAviso, mostrarError, ocultarAviso } from '../../common/js/avisos.js';

/**
 * Datos del curso que ven el jefe y el docente: variable, etiqueta y formato opcional.
 * Son variables de VARIABLES_LECTURA del backend.
 */
export const camposCurso = [
  ['empresaNombre', 'Empresa'],
  ['tematica', 'Temática'],
  [variables.programa, 'Programa'],
  ['fechaInicio', 'Inicio acordado', formatearFechaAcordada],
  [variables.fechaFinPropuesta, 'Finalización propuesta'],
  ['participantes', 'Participantes', null, participantes],
  ['recursos', 'Recursos', null, recursos],
];

/**
 * Formatea la fecha que acordó la empresa.
 * Recibe: texto ISO-8601 (por ejemplo 2027-03-08T09:00:00-03:00).
 * Devuelve: dd/MM/yyyy, tomado del texto tal cual para que la zona horaria no corra el día.
 */
export function formatearFechaAcordada(fecha) {
  const coincidencia = /^(\d{4})-(\d{2})-(\d{2})/.exec(String(fecha));
  return coincidencia ? `${coincidencia[3]}/${coincidencia[2]}/${coincidencia[1]}` : String(fecha);
}

/** Lista definitiva si ya existe; si no, la nómina que mandó la empresa. */
function participantes(valores) {
  return valores[variables.listaDefinitiva] ?? valores.participantes ?? '';
}

/** Recursos elegidos en "Evaluar Recursos", en texto. */
function recursos(valores) {
  const elegidos = [
    [variables.aulaFisica, 'aula presencial'],
    [variables.aulaVirtual, 'aula virtual'],
    [variables.equipamiento, 'equipamiento especial'],
  ].filter(([nombre]) => valores[nombre] === true).map(([, texto]) => texto);
  if (elegidos.length === 0) return '';
  const texto = elegidos.join(', ');
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}

/**
 * Nombre para mostrar de un integrante del equipo docente.
 * Recibe: id de usuario. Devuelve: el nombre, o el mismo id si no es del equipo.
 */
export function nombreDocente(usuario) {
  return integrantesDocentes.find((integrante) => integrante.usuario === usuario)?.nombre ?? usuario;
}

/**
 * Dibuja los datos del curso en una lista de definiciones.
 * Recibe: el elemento <dl> y el objeto { nombreVariable: valor }.
 */
export function mostrarCurso(lista, valores) {
  lista.replaceChildren(...camposCurso.flatMap(([nombre, etiqueta, formatear, calcular]) => {
    const termino = document.createElement('dt');
    termino.textContent = etiqueta;
    const definicion = document.createElement('dd');
    const valor = calcular ? calcular(valores) : (valores[nombre] ?? '');
    definicion.textContent = valor === '' ? 'No informado' : (formatear ? formatear(valor) : String(valor));
    return [termino, definicion];
  }));
}

/**
 * Busca la tarea de esta pantalla entre las del usuario.
 * Usa ?tarea=<id> de la URL o, si no viene, la más antigua de ese tipo.
 * Recibe: la clave de la tarea en el BPMN y el texto para cuando no hay ninguna.
 * Devuelve: promesa de { tarea }, o de { tarea: null, texto, tipo } con el aviso a mostrar.
 */
export async function buscarTarea(clave, textoSinTareas) {
  const idPedido = new URLSearchParams(window.location.search).get('tarea');
  const deEstaPantalla = (await listarTareas()).filter((tarea) => tarea.claveTarea === clave);
  if (idPedido) {
    const pedida = deEstaPantalla.find((tarea) => tarea.id === idPedido);
    return pedida
      ? { tarea: pedida }
      : { tarea: null, texto: 'Esa tarea no existe, ya fue completada o no está asignada a vos.', tipo: 'error' };
  }
  return deEstaPantalla[0]
    ? { tarea: deEstaPantalla[0] }
    : { tarea: null, texto: textoSinTareas, tipo: 'neutro' };
}

/** Deja la tarea en la URL, para que al recargar se abra la misma. */
export function recordarTareaEnUrl(id) {
  const url = new URL(window.location.href);
  url.searchParams.set('tarea', id);
  window.history.replaceState(null, '', url);
}

/**
 * En modo simulado, muestra una marca para que nadie confunda los datos con los reales.
 * Recibe: el elemento donde va la marca (queda oculto en modo real).
 */
export function marcarModoSimulado(elemento) {
  if (modoActual !== 'mock') return;
  elemento.textContent = 'Modo simulado: los datos son de ejemplo y no se guardan en el motor.';
  elemento.hidden = false;
}
