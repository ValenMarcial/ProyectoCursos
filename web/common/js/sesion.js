/**
 * Módulo sesion.js
 *
 * Exige una sesión iniciada en las pantallas del personal (menú, bandeja y las
 * que vengan): si no hay sesión, lleva al login con ?volver= de la pantalla
 * actual, para que el login devuelva al usuario adonde estaba.
 *
 * Solo redirige cuando el backend responde que no hay sesión (401). Si el
 * backend no responde o falla, relanza el error para que la pantalla muestre el
 * aviso: así un servidor caído no arma un bucle de redirecciones.
 *
 * En modo simulado (?api=mock) usuarioActual() devuelve siempre el usuario
 * simulado, así que nunca redirige.
 */

import { usuarioActual } from '../../administration/js/api.js';
import { urlLogin } from './navegacion.js';

/**
 * Pide el usuario de la sesión y, si no hay, manda al login.
 * Usa location.replace y no assign: así "Atrás" desde el login no vuelve a una
 * pantalla que redirigiría otra vez.
 * Recibe: opcionalmente { consultarUsuario, ubicacion } para probarla sin
 * navegador; por defecto usuarioActual() de api.js y window.location.
 * Devuelve: promesa del usuario ({ usuario, nombre, grupos }), o de null si no
 * hay sesión y ya se pidió ir al login (la pantalla no tiene que seguir).
 * Lanza: el ErrorApi de usuarioActual() si no se pudo consultar la sesión
 * (error de red, 500 o 502).
 */
export async function exigirSesion({
  consultarUsuario = usuarioActual,
  ubicacion = globalThis.location,
} = {}) {
  const usuario = await consultarUsuario();
  if (usuario) return usuario;

  // Se conserva la query (por ejemplo ?api=real o ?tarea=) para volver a la misma pantalla.
  ubicacion.replace(urlLogin(ubicacion.pathname + ubicacion.search));
  return null;
}
