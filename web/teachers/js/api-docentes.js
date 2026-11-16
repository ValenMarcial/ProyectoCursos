/**
 * Módulo api-docentes.js
 *
 * Adaptador de las pantallas del equipo docente. Reutiliza el de Administración
 * (crearApi, enviarReal y ErrorApi de administration/js/api.js): la misma
 * conversión de variables, la misma traducción de errores y las mismas rutas del
 * backend. Lo único propio es el transporte simulado, con usuarios del grupo Docentes.
 *
 * El modo se elige igual que en Administración: ?api=real o ?api=mock en la URL,
 * recordado en la pestaña, y si no, el default de contrato.js ('real').
 */

import { crearApi, enviarReal, ErrorApi } from '../../administration/js/api.js';
import { claveModoApi, modoApi, parametroModoApi } from '../../administration/js/contrato.js';
import { urlLogin } from '../../common/js/navegacion.js';
import { enviarSimuladoDocentes } from './mock-docentes.js';

export { ErrorApi };

const transportes = { mock: enviarSimuladoDocentes, real: enviarReal };

/**
 * Decide el modo: ?api= de la URL (y lo guarda en la pestaña), si no el guardado,
 * y si no el default. Usa la misma clave que Administración, así el modo elegido
 * sigue al pasar de una sección a la otra.
 * No recibe nada. Devuelve: 'mock' o 'real'.
 */
function elegirModo() {
  const pedido = new URLSearchParams(globalThis.location?.search ?? '').get(parametroModoApi);
  try {
    if (Object.hasOwn(transportes, pedido ?? '')) {
      globalThis.sessionStorage?.setItem(claveModoApi, pedido);
      return pedido;
    }
    const guardado = globalThis.sessionStorage?.getItem(claveModoApi);
    if (Object.hasOwn(transportes, guardado ?? '')) return guardado;
  } catch {
    if (Object.hasOwn(transportes, pedido ?? '')) return pedido;
  }
  return modoApi;
}

/** Modo de esta pestaña: 'mock' o 'real'. Las pantallas lo usan para avisar que los datos son simulados. */
export const modoActual = elegirModo();

/**
 * Si la sesión vence en medio de una operación, manda al login común con
 * ?volver= de la pantalla actual (urlLogin de common/js/navegacion.js).
 */
function redirigirALogin() {
  if (typeof window === 'undefined') return;
  window.location.assign(urlLogin(window.location.pathname + window.location.search));
}

export const {
  usuarioActual, listarTareas, completarTarea, obtenerVariables,
} = crearApi(transportes[modoActual], { alPerderSesion: redirigirALogin });
