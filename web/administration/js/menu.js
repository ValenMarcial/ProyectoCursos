/**
 * Módulo menu.js
 *
 * Menú de Administración (index.html): exige una sesión antes de mostrar los
 * enlaces. Sin sesión va al login con ?volver= del menú; si no se pudo consultar
 * la sesión (backend caído, por ejemplo), muestra el aviso y deja el menú oculto,
 * sin redirigir.
 *
 * Los datos reales igual los protege el backend (401 sin sesión): esto evita que
 * el menú se vea como si hubiera una sesión iniciada.
 */

import { mostrarError } from '../../common/js/avisos.js';
import { exigirSesion } from '../../common/js/sesion.js';

const aviso = document.getElementById('aviso');
const menu = document.getElementById('menu');

/**
 * Arranca el menú: con sesión lo muestra; sin sesión exigirSesion ya pidió ir al
 * login; con error muestra el aviso.
 * No recibe nada. Devuelve: promesa sin valor.
 */
async function iniciar() {
  try {
    const usuario = await exigirSesion();
    if (usuario) menu.hidden = false;
  } catch (error) {
    mostrarError(aviso, error);
  }
}

iniciar();
