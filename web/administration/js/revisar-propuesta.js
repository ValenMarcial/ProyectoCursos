/**
 * Módulo revisar-propuesta.js
 *
 * Pantalla 2, "Revisar propuesta": la empresa pidió modificaciones (evento
 * "Modificaciones" del BPMN, que trae observaciones) y el jefe administrativo
 * corrige la propuesta. Muestra lo que pidió la empresa, los datos de la solicitud
 * y la propuesta anterior; precarga el formulario con esa propuesta, lo valida,
 * completa la tarea y muestra la vista previa del PDF de la nueva versión.
 *
 * La tarea está asignada a jefeAdministrativo en el BPMN: no se reclama. Tiene los
 * mismos campos y el mismo listener (ValidateProposalDates) que "Registrar", así
 * que usa el mismo formulario (formulario-propuesta.js) y el mismo PDF.
 *
 * Toda la comunicación pasa por api.js. Los datos se muestran con textContent:
 * nunca se interpretan como HTML.
 */

import { completarTarea, obtenerVariables } from './api.js';
import { tareas, variables } from './contrato.js';
import { convertirPropuesta, crearFormularioPropuesta, variablesPropuesta } from './formulario-propuesta.js';
import { buscarTarea, camposSolicitud, mostrarDatos, recordarTareaEnUrl } from './pantalla-tarea.js';
import { armarPdfPropuesta } from './pdf-propuesta.js';
import { formatearPresupuesto } from './texto.js';
import { mostrarAviso, mostrarError } from '../../common/js/avisos.js';

/** Lo que pidió la empresa al mandar el mensaje msgModificaciones. Si no vino, "No informado". */
const camposObservaciones = [
  ['observaciones', 'Observaciones'],
];

/** Propuesta enviada antes (la que la empresa pidió cambiar), solo para consulta. */
const camposAnterior = [
  [variables.programa, 'Programa del curso'],
  [variables.presupuesto, 'Presupuesto', formatearPresupuesto],
  [variables.fechaInicioPosible, 'Fecha de inicio posible'],
  [variables.fechaFinPropuesta, 'Fecha de finalización posible'],
];

const aviso = document.getElementById('aviso');
const bloqueObservaciones = document.getElementById('bloque-observaciones');
const datosObservaciones = document.getElementById('datos-observaciones');
const bloqueSolicitud = document.getElementById('bloque-solicitud');
const datosSolicitud = document.getElementById('datos-solicitud');
const bloqueAnterior = document.getElementById('bloque-anterior');
const datosAnterior = document.getElementById('datos-anterior');
const bloquePropuesta = document.getElementById('bloque-propuesta');
const formulario = document.getElementById('formulario-propuesta');
const botonCompletar = document.getElementById('boton-completar');
const bloqueResultado = document.getElementById('bloque-resultado');
const tituloResultado = document.getElementById('titulo-resultado');
const vistaPrevia = document.getElementById('vista-previa-pdf');
const sinVisor = document.getElementById('sin-visor');
const enlaceAbrir = document.getElementById('abrir-pdf');
const botonDescargar = document.getElementById('boton-descargar');

// Lectura, validación, precarga y marcas de error de los campos (también desmarca al corregir).
const campos = crearFormularioPropuesta(formulario);

// Se completan en iniciar() y los usa el envío del formulario.
let tareaActual = null;
let valores = {};

/**
 * Descarga el PDF sin perder la pantalla si falla: la tarea ya está completada.
 * Copia de registrar-propuesta.js (cambia solo el texto del aviso); se extrae a un
 * módulo común (R3) después de la entrega.
 * Recibe: el objeto devuelto por armarPdfPropuesta. No devuelve nada.
 */
function descargarPdf(pdf) {
  try {
    pdf.descargar();
  } catch (error) {
    console.error(error);
    mostrarAviso(aviso, 'La propuesta revisada quedó registrada, pero no se pudo descargar el PDF. Probá de nuevo con el botón.', 'error');
  }
}

/**
 * Muestra el PDF en la vista previa y deja listos los botones de descargar y abrir.
 * Copia de registrar-propuesta.js; se extrae a un módulo común (R3) después de la entrega.
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
  // La versión la escribe el motor en el evento "Modificaciones" (2, 3, ...). El 2 es solo
  // un respaldo por si la variable no llega.
  const version = valores.versionPropuesta ?? 2;

  // El PDF se arma antes de completar: si falla, la tarea sigue pendiente.
  let pdf;
  try {
    pdf = armarPdfPropuesta({
      empresa: valores.empresaNombre,
      tematica: valores.tematica,
      ...propuesta,
      version,
    });
  } catch (error) {
    console.error(error);
    mostrarAviso(aviso, 'No se pudo preparar el PDF. La propuesta revisada no se registró.', 'error');
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
  tituloResultado.textContent = `PDF de la propuesta, versión ${version}`;
  mostrarVistaPrevia(pdf);
  // El botón pulsado desapareció con el formulario: el foco pasa al título del PDF.
  tituloResultado.focus();
  mostrarAviso(aviso,
    `La propuesta revisada (versión ${version}) quedó registrada y se envía de nuevo a la empresa. Revisá el PDF y descargalo antes de salir: no queda guardado.`,
    'exito');
}

/**
 * Carga la pantalla: busca la tarea, la recuerda en la URL, muestra las
 * observaciones, la solicitud y la propuesta anterior, y precarga el formulario.
 * No recibe nada. Devuelve: promesa sin valor.
 */
async function iniciar() {
  try {
    const busqueda = await buscarTarea({
      clave: tareas.revisarPropuesta,
      textoSinTareas: 'No hay ninguna propuesta pendiente de revisar.',
    });
    if (!busqueda.tarea) {
      mostrarAviso(aviso, busqueda.texto, busqueda.tipo);
      return;
    }
    tareaActual = busqueda.tarea;
    valores = await obtenerVariables(tareaActual.instanciaId);
  } catch (error) {
    mostrarError(aviso, error);
    return;
  }

  recordarTareaEnUrl(tareaActual.id);
  mostrarDatos(datosObservaciones, camposObservaciones, valores);
  mostrarDatos(datosSolicitud, camposSolicitud, valores);
  mostrarDatos(datosAnterior, camposAnterior, valores);
  campos.precargar(valores);
  bloqueObservaciones.hidden = false;
  bloqueSolicitud.hidden = false;
  bloqueAnterior.hidden = false;
  bloquePropuesta.hidden = false;
}

formulario.addEventListener('submit', alEnviar);
iniciar();
