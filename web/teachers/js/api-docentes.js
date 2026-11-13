/**
 * Módulo api-docentes.js
 *
 * Adaptador de las pantallas del equipo docente. Reutiliza el de Administración
 * (crearApi, enviarReal y ErrorApi de administration/js/api.js): la misma
 * conversión de variables, la misma traducción de errores y las mismas rutas del
 * backend. Lo único propio es el transporte simulado, con usuarios del grupo Docentes.
 *
 * El modo se elige igual que en Administración: ?api=real o ?api=mock en la URL,
 * recordado en la pestaña, y si no, el default de contrato.js (hoy 'mock', hasta
 * que exista el login).
 */

import { crearApi, enviarReal, ErrorApi } from '../../administration/js/api.js';
import { claveModoApi, modoApi, parametroModoApi, rutaLogin } from '../../administration/js/contrato.js';
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
 * Manda al login con ?volver=, como en Administración. Mientras rutaLogin sea
 * null no hace nada y la pantalla muestra el aviso de sesión vencida.
 */
function redirigirALogin() {
  if (rutaLogin === null || typeof window === 'undefined') return;
  const volver = window.location.pathname + window.location.search;
  window.location.assign(`${rutaLogin}?${new URLSearchParams({ volver })}`);
}

export const {
  usuarioActual, listarTareas, completarTarea, obtenerVariables,
} = crearApi(transportes[modoActual], { alPerderSesion: redirigirALogin });
