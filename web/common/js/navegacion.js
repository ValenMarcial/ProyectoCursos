/**
 * Módulo navegacion.js
 *
 * Decide a dónde va el personal después del login: valida la pantalla pedida en
 * ?volver= y, si no sirve, elige un destino por defecto según sus grupos.
 *
 * Son funciones puras (no leen window ni el DOM), así se pueden probar con Node.
 * Las rutas son absolutas desde la raíz del sitio: valen igual con el backend
 * (:8081) que con python3 -m http.server levantado en web/.
 */

import { grupoAdministracion, rutaLogin } from '../../administration/js/contrato.js';

/** Parámetro de la URL del login con la pantalla a la que hay que volver. */
export const parametroVolver = 'volver';

/** Menú de Administración: destino por defecto del grupo Administracion. */
export const rutaAdministracion = '/administration/index.html';

/** Portada del sitio: destino por defecto de cualquier otro usuario. */
export const rutaPortada = '/index.html';

// Caracteres de control (incluye tab y salto de línea). El parser de URL los
// descarta en silencio, así que "/\t/otro.sitio" terminaría siendo "//otro.sitio".
const patronControl = /[\u0000-\u001f\u007f]/;

/**
 * Valida la pantalla a la que hay que volver después del login.
 * Solo se acepta una ruta del mismo sitio: tiene que empezar con una sola barra,
 * sin esquema, sin "//" al principio, sin "\" (algunos navegadores la toman como
 * "/") y sin caracteres de control. Tampoco se acepta el propio login, para no
 * armar un bucle.
 * Recibe: el valor de ?volver= (texto o null) y el origen del sitio
 * (por ejemplo http://127.0.0.1:8081).
 * Devuelve: la ruta normalizada (ruta, query y fragmento) o null si no es segura.
 */
export function rutaVolverSegura(volver, origen) {
  if (typeof volver !== 'string' || !volver.startsWith('/') || volver.startsWith('//')) return null;
  if (volver.includes('\\') || patronControl.test(volver)) return null;

  let url;
  try {
    url = new URL(volver, origen);
  } catch {
    return null;
  }
  // Una ruta como "/.//otro.sitio" se normaliza a "//otro.sitio": volver a
  // revisar después de normalizar evita que salga una URL a otro dominio.
  if (url.origin !== origen || url.pathname.startsWith('//')) return null;
  if (url.pathname === rutaLogin) return null;
  return url.pathname + url.search + url.hash;
}

/**
 * Elige el destino por defecto según los grupos del usuario.
 * Recibe: la lista de grupos de la sesión (puede faltar).
 * Devuelve: la ruta del menú de Administración o, para cualquier otro grupo, la portada.
 */
export function destinoPorDefecto(grupos) {
  return Array.isArray(grupos) && grupos.includes(grupoAdministracion) ? rutaAdministracion : rutaPortada;
}

/**
 * Elige a dónde ir después del login: la pantalla pedida si es segura y, si no,
 * el destino por defecto.
 * Recibe: el valor de ?volver=, los grupos del usuario y el origen del sitio.
 * Devuelve: la ruta destino.
 */
export function elegirDestino(volver, grupos, origen) {
  return rutaVolverSegura(volver, origen) ?? destinoPorDefecto(grupos);
}

/**
 * Lee ?volver= de un query string.
 * Recibe: el texto de location.search (con o sin "?").
 * Devuelve: el valor tal cual viene, o null si no está. Se valida aparte.
 */
export function leerVolver(busqueda) {
  return new URLSearchParams(busqueda).get(parametroVolver);
}

/**
 * Arma la URL del login con la pantalla a la que hay que volver.
 * Recibe: la ruta actual (pathname + search).
 * Devuelve: la URL del login con ?volver=.
 */
export function urlLogin(volver) {
  return `${rutaLogin}?${new URLSearchParams({ [parametroVolver]: volver })}`;
}
