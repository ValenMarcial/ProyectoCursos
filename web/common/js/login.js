/**
 * Módulo login.js
 *
 * Pantalla de login del personal (Administración y Docentes). Inicia la sesión
 * en el backend con el usuario y la clave de Flowable y lleva al usuario a la
 * pantalla de ?volver= (si es una ruta segura del sitio) o a su destino por defecto.
 *
 * La clave solo viaja en el pedido de login: no se guarda en ningún storage, no
 * se escribe en la consola y el campo se vacía después de cada intento.
 */

import { cerrarSesion, iniciarSesion, usuarioActual } from '../../administration/js/api.js';
import { mostrarAviso, mostrarError, ocultarAviso } from './avisos.js';
import { elegirDestino, leerVolver } from './navegacion.js';

const aviso = document.getElementById('aviso');
const bloqueSesion = document.getElementById('bloque-sesion');
const textoSesion = document.getElementById('texto-sesion');
const enlaceContinuar = document.getElementById('continuar');
const botonOtroUsuario = document.getElementById('otro-usuario');
const bloqueIngreso = document.getElementById('bloque-ingreso');
const formulario = document.getElementById('formulario-ingreso');
const campoUsuario = document.getElementById('usuario');
const campoClave = document.getElementById('clave');
const botonIngresar = document.getElementById('ingresar');

/** Mismo texto que el backend (SesionException.Motivo.DATOS_INVALIDOS, sin la regla del ":"). */
const mensajeDatosFaltantes = 'Ingresá el usuario y la clave.';

/** Pantalla pedida en ?volver=, tal cual vino. Se valida al elegir el destino. */
const volver = leerVolver(window.location.search);

/**
 * Calcula el destino para un usuario.
 * Recibe: los datos de la sesión ({ grupos }). Devuelve: la ruta destino.
 */
function destinoDe(usuario) {
  return elegirDestino(volver, usuario?.grupos, window.location.origin);
}

/** Muestra el formulario de ingreso y le da foco al usuario. */
function mostrarFormulario() {
  bloqueSesion.hidden = true;
  bloqueIngreso.hidden = false;
  campoUsuario.focus();
}

/**
 * Muestra que ya hay una sesión iniciada, con la opción de seguir o de cambiar
 * de usuario. No redirige solo: en modo simulado la sesión siempre está activa
 * al cargar, y así el formulario se puede probar igual.
 * Recibe: los datos de la sesión ({ usuario, nombre, grupos }).
 */
function mostrarSesionIniciada(usuario) {
  textoSesion.textContent = `Ya iniciaste sesión como ${usuario.nombre} (${usuario.usuario}).`;
  enlaceContinuar.href = destinoDe(usuario);
  bloqueIngreso.hidden = true;
  bloqueSesion.hidden = false;
}

/**
 * Habilita o deshabilita el formulario mientras se espera la respuesta.
 * Recibe: true para bloquearlo.
 */
function bloquearFormulario(bloqueado) {
  botonIngresar.disabled = bloqueado;
  botonIngresar.textContent = bloqueado ? 'Ingresando…' : 'Ingresar';
}

/**
 * Marca los campos vacíos como inválidos.
 * Recibe: el usuario ya recortado y la clave.
 * Devuelve: true si están los dos datos.
 */
function validarCampos(usuario, clave) {
  campoUsuario.setAttribute('aria-invalid', String(usuario === ''));
  campoClave.setAttribute('aria-invalid', String(clave === ''));
  return usuario !== '' && clave !== '';
}

/**
 * Envía el formulario: valida, inicia la sesión y redirige.
 * Recibe: el evento submit. Devuelve: promesa sin valor.
 */
async function ingresar(evento) {
  evento.preventDefault();
  ocultarAviso(aviso);

  const usuario = campoUsuario.value.trim();
  const clave = campoClave.value;
  if (!validarCampos(usuario, clave)) {
    mostrarAviso(aviso, mensajeDatosFaltantes, 'error');
    (usuario === '' ? campoUsuario : campoClave).focus();
    return;
  }

  bloquearFormulario(true);
  try {
    const sesion = await iniciarSesion(usuario, clave);
    // replace: con "atrás" no se vuelve al login.
    window.location.replace(destinoDe(sesion));
  } catch (error) {
    mostrarError(aviso, error);
    if (error?.tipo === 'credenciales') {
      campoClave.setAttribute('aria-invalid', 'true');
    }
    bloquearFormulario(false);
    campoClave.focus();
  } finally {
    // La clave no queda en la página, ni si salió bien ni si salió mal.
    campoClave.value = '';
  }
}

/**
 * Cierra la sesión actual para entrar con otro usuario.
 * No recibe nada. Devuelve: promesa sin valor.
 */
async function cambiarDeUsuario() {
  ocultarAviso(aviso);
  botonOtroUsuario.disabled = true;
  try {
    await cerrarSesion();
    mostrarFormulario();
  } catch (error) {
    mostrarError(aviso, error);
  } finally {
    botonOtroUsuario.disabled = false;
  }
}

/**
 * Arranca la pantalla: si ya hay sesión lo avisa; si no, muestra el formulario.
 * Si no se puede consultar la sesión (servidor caído), muestra el formulario con el error.
 * No recibe nada. Devuelve: promesa sin valor.
 */
async function iniciar() {
  formulario.addEventListener('submit', ingresar);
  botonOtroUsuario.addEventListener('click', cambiarDeUsuario);
  try {
    const usuario = await usuarioActual();
    if (usuario) {
      mostrarSesionIniciada(usuario);
      return;
    }
  } catch (error) {
    mostrarError(aviso, error);
  }
  mostrarFormulario();
}

iniciar();
