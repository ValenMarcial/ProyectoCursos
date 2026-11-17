/**
 * Módulo secciones.js
 *
 * Las secciones del personal (Administración y Equipo docente) y qué grupo de Flowable
 * da acceso a cada una. Es la única tabla de "quién entra adónde": el login la usa para
 * llevar a cada persona a su sección y las pantallas, para no dejar entrar a la de otro grupo.
 *
 * Es una regla de la interfaz, no de seguridad: los datos los protege el backend (una tarea
 * de otro grupo responde 404 al verla y 403 al completarla). Acá solo se evita que la
 * persona navegue a pantallas que no le corresponden.
 *
 * Son funciones puras (sin DOM), así se pueden probar con Node.
 */

import { grupoAdministracion } from '../../administration/js/contrato.js';
import { grupoDocentes } from '../../teachers/js/contrato-docentes.js';

/**
 * Cada sección con su grupo, su nombre para mostrar, su pantalla de inicio y la carpeta
 * donde viven sus pantallas (rutas absolutas desde la raíz del sitio).
 */
export const secciones = Object.freeze({
  administracion: Object.freeze({
    grupo: grupoAdministracion,
    nombre: 'Administración',
    inicio: '/administration/index.html',
    carpeta: '/administration/',
  }),
  docentes: Object.freeze({
    grupo: grupoDocentes,
    nombre: 'Equipo docente',
    inicio: '/teachers/index.html',
    carpeta: '/teachers/',
  }),
});

/**
 * Indica si el usuario puede entrar a una sección.
 * Recibe: los grupos del usuario (lista de texto) y la clave de la sección.
 * Devuelve: true si pertenece al grupo de esa sección; false si no, si la clave no
 * existe o si los grupos no son una lista.
 */
export function puedeEntrar(grupos, clave) {
  return Array.isArray(grupos) && Object.hasOwn(secciones, clave) && grupos.includes(secciones[clave].grupo);
}

/**
 * Lista las secciones a las que pertenece el usuario.
 * Recibe: los grupos del usuario. Devuelve: lista de claves de secciones (puede ser vacía).
 */
export function seccionesDelUsuario(grupos) {
  return Object.keys(secciones).filter((clave) => puedeEntrar(grupos, clave));
}

/**
 * Dice a qué sección pertenece una ruta del sitio.
 * Recibe: la ruta (pathname), por ejemplo /teachers/definir-docente.html.
 * Devuelve: la clave de la sección, o null si no es de ninguna (la bandeja común, el login).
 */
export function seccionDeRuta(ruta) {
  const clave = Object.keys(secciones).find((k) => String(ruta).startsWith(secciones[k].carpeta));
  return clave ?? null;
}

/**
 * Elige la pantalla de inicio de una persona según sus grupos.
 * Recibe: los grupos del usuario y la ruta a usar si no hay una sección única para él
 * (sin ninguno de los dos grupos, o en los dos a la vez).
 * Devuelve: el inicio de su sección, o la ruta por defecto.
 */
export function destinoPorGrupo(grupos, porDefecto) {
  const propias = seccionesDelUsuario(grupos);
  return propias.length === 1 ? secciones[propias[0]].inicio : porDefecto;
}
