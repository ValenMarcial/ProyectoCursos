/**
 * Módulo registrar-propuesta.js
 *
 * Pantalla 1, "Registrar y Elaborar propuesta": muestra los datos de la
 * solicitud, valida el formulario, completa la tarea y muestra la vista previa
 * del PDF de la propuesta, que se descarga solo con el botón.
 *
 * Toda la comunicación pasa por api.js. Los datos se muestran con textContent
 * porque vienen de un formulario público: nunca se interpretan como HTML.
 */

import { completarTarea, obtenerVariables } from './api.js';
import { tareas } from './contrato.js';
import { convertirPropuesta, crearFormularioPropuesta, variablesPropuesta } from './formulario-propuesta.js';
import { buscarTarea, camposSolicitud, mostrarDatos } from './pantalla-tarea.js';
import { armarPdfPropuesta } from './pdf-propuesta.js';
import { mostrarAviso, mostrarError } from '../../common/js/avisos.js';

const aviso = document.getElementById('aviso');
const bloqueSolicitud = document.getElementById('bloque-solicitud');
const datosSolicitud = document.getElementById('datos-solicitud');
const bloquePropuesta = document.getElementById('bloque-propuesta');
const formulario = document.getElementById('formulario-propuesta');
const botonCompletar = document.getElementById('boton-completar');
const bloqueResultado = document.getElementById('bloque-resultado');
const tituloResultado = document.getElementById('titulo-resultado');
const vistaPrevia = document.getElementById('vista-previa-pdf');
const sinVisor = document.getElementById('sin-visor');
const enlaceAbrir = document.getElementById('abrir-pdf');
const botonDescargar = document.getElementById('boton-descargar');

// Lectura, validación y marcas de error de los campos (también desmarca al corregir).
const campos = crearFormularioPropuesta(formulario);

// Se completan en iniciar() y los usa el envío del formulario.
let tareaActual = null;
let variablesSolicitud = {};

/**
 * Descarga el PDF sin perder la pantalla si falla: la tarea ya está completada.
 * Recibe: el objeto devuelto por armarPdfPropuesta. No devuelve nada.
 */
function descargarPdf(pdf) {
  try {
    pdf.descargar();
  } catch (error) {
    console.error(error);
    mostrarAviso(aviso, 'La propuesta quedó registrada, pero no se pudo descargar el PDF. Probá de nuevo con el botón.', 'error');
  }
}

/**
 * Muestra el PDF en la vista previa y deja listos los botones de descargar y abrir.
 * Recibe: el objeto devuelto por armarPdfPropuesta. No devuelve nada.
 */
function mostrarVistaPrevia(pdf) {
  enlaceAbrir.href = pdf.url;
  // false indica que el navegador no muestra PDF embebidos (por ejemplo, en el celular);
  // si no lo informa (undefined), se intenta igual y quedan los botones de respaldo.
  if (navigator.pdfViewerEnabled === false) {
    vistaPrevia.hidden = true;
    sinVisor.hidden = false;
  } else {
    vistaPrevia.src = pdf.url;
  }
  botonDescargar.addEventListener('click', () => descargarPdf(pdf));
}

/**
 * Procesa el envío: valida, arma el PDF, completa la tarea y muestra la vista previa.
 * Recibe: el evento submit. Devuelve: promesa sin valor.
 */
async function alEnviar(evento) {
  evento.preventDefault();
  aviso.hidden = true;

  const datos = campos.leer();
  // validar marca los campos y enfoca el primer error: el aviso va después, para que quede a la vista.
  const errores = campos.validar(datos);
  if (errores.length > 0) {
    mostrarAviso(aviso, errores.map((error) => error.mensaje).join('\n'), 'error');
    return;
  }

  const propuesta = convertirPropuesta(datos);

  // El PDF se arma antes de completar: si falla, la tarea sigue pendiente.
  let pdf;
  try {
    pdf = armarPdfPropuesta({
      empresa: variablesSolicitud.empresaNombre,
      tematica: variablesSolicitud.tematica,
      ...propuesta,
    });
  } catch (error) {
    console.error(error);
    mostrarAviso(aviso, 'No se pudo preparar el PDF. La propuesta no se registró.', 'error');
    return;
  }

  // Con el botón deshabilitado tampoco se puede reenviar con Enter.
  botonCompletar.disabled = true;
  try {
    await completarTarea(tareaActual.id, variablesPropuesta(propuesta));
  } catch (error) {
    // Tras un rechazo la tarea sigue activa: se deja corregir y reintentar. El PDF
    // no se va a mostrar y cada reintento arma uno nuevo, así que se libera.
    pdf.liberar();
    botonCompletar.disabled = false;
    mostrarError(aviso, error);
    return;
  }

  bloquePropuesta.hidden = true;
  bloqueResultado.hidden = false;
  mostrarVistaPrevia(pdf);
  // El botón pulsado desapareció con el formulario: el foco pasa al título del PDF.
  tituloResultado.focus();
  mostrarAviso(aviso, 'La propuesta quedó registrada. Revisá el PDF y descargalo antes de salir: no queda guardado.', 'exito');
}

/**
 * Carga la pantalla: busca la tarea y muestra sus datos.
 * No recibe nada. Devuelve: promesa sin valor.
 */
async function iniciar() {
  try {
    const busqueda = await buscarTarea({
      clave: tareas.registrarPropuesta,
      textoSinTareas: 'No hay ninguna tarea pendiente de registrar propuesta.',
    });
    if (!busqueda.tarea) {
      mostrarAviso(aviso, busqueda.texto, busqueda.tipo);
      return;
    }
    tareaActual = busqueda.tarea;
    variablesSolicitud = await obtenerVariables(tareaActual.instanciaId);
  } catch (error) {
    mostrarError(aviso, error);
    return;
  }

  mostrarDatos(datosSolicitud, camposSolicitud, variablesSolicitud);
  bloqueSolicitud.hidden = false;
  bloquePropuesta.hidden = false;
}

formulario.addEventListener('submit', alEnviar);
iniciar();
