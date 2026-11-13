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
import { tareas, variables } from './contrato.js';
import { buscarTarea, mostrarDatos } from './pantalla-tarea.js';
import { armarPdfPropuesta } from './pdf-propuesta.js';
import { mostrarAviso, mostrarError } from '../../common/js/avisos.js';

/** Variables de la solicitud que se muestran, con su etiqueta. */
const camposSolicitud = [
  ['empresaNombre', 'Empresa'],
  ['tematica', 'Temática'],
  ['descripcion', 'Descripción'],
  ['contactoNombre', 'Contacto'],
  ['contactoEmail', 'Correo de contacto'],
  ['contactoTelefono', 'Teléfono de contacto'],
  ['participantesEstimados', 'Participantes estimados'],
  ['fechasPreferidas', 'Fechas preferidas'],
];

/** Nombres de los campos que se validan, en el orden en que aparecen en el formulario. */
const camposValidados = ['programa', 'presupuesto', 'fecha-inicio', 'fecha-fin'];

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

// Se completan en iniciar() y los usa el envío del formulario.
let tareaActual = null;
let variablesSolicitud = {};

/**
 * Lee el formulario.
 * No recibe nada.
 * Devuelve: { programa, presupuesto, inicio, fin }, todos texto y con las fechas
 * como las entrega el input (yyyy-MM-dd, o vacío si está incompleta).
 */
function leerFormulario() {
  return {
    programa: formulario.elements.programa.value.trim(),
    presupuesto: formulario.elements.presupuesto.value.trim(),
    inicio: formulario.elements['fecha-inicio'].value,
    fin: formulario.elements['fecha-fin'].value,
  };
}

/**
 * Aplica las validaciones del cliente, que anticipan las del listener.
 * Recibe: el resultado de leerFormulario.
 * Devuelve: lista de { campos, mensaje }, donde campos son los nombres de los
 * campos afectados; vacía si está todo bien.
 */
function validarPropuesta({ programa, presupuesto, inicio, fin }) {
  const errores = [];

  if (programa === '') errores.push({ campos: ['programa'], mensaje: 'Completá el programa del curso.' });

  const numero = Number(presupuesto);
  if (presupuesto === '') {
    errores.push({ campos: ['presupuesto'], mensaje: 'Completá el presupuesto.' });
  } else if (!Number.isSafeInteger(numero)) {
    errores.push({ campos: ['presupuesto'], mensaje: 'El presupuesto debe ser un número entero.' });
  } else if (numero < 0) {
    errores.push({ campos: ['presupuesto'], mensaje: 'El presupuesto debe ser mayor o igual a 0.' });
  }

  // Un solo mensaje para las dos fechas, pero se marca solo la que falta.
  const fechasVacias = [];
  if (inicio === '') fechasVacias.push('fecha-inicio');
  if (fin === '') fechasVacias.push('fecha-fin');

  if (fechasVacias.length > 0) {
    errores.push({ campos: fechasVacias, mensaje: 'Completá las fechas de inicio y de finalización.' });
  } else if (fin < inicio) {
    // En formato yyyy-MM-dd, el orden alfabético coincide con el cronológico.
    errores.push({
      campos: ['fecha-fin'],
      mensaje: 'La fecha de finalización debe ser posterior o igual a la fecha de inicio.',
    });
  }

  return errores;
}

/**
 * Junta los campos afectados por una lista de errores.
 * Recibe: el resultado de validarPropuesta.
 * Devuelve: Set con los nombres de esos campos.
 */
function camposConError(errores) {
  return new Set(errores.flatMap((error) => error.campos));
}

/**
 * Deja aria-invalid="true" solo en los campos con error.
 * Recibe: el resultado de validarPropuesta. No devuelve nada.
 */
function marcarCampos(errores) {
  const conError = camposConError(errores);
  for (const nombre of camposValidados) {
    const campo = formulario.elements[nombre];
    if (conError.has(nombre)) {
      campo.setAttribute('aria-invalid', 'true');
    } else {
      campo.removeAttribute('aria-invalid');
    }
  }
}

/**
 * Saca aria-invalid de los campos que ya están bien, sin marcar ninguno nuevo:
 * mientras se escribe no hay que señalar un campo que todavía no se envió.
 * Valida todo el formulario porque corregir la fecha de inicio también puede
 * resolver el error marcado en la de finalización.
 * No recibe ni devuelve nada.
 */
function desmarcarCorregidos() {
  const conError = camposConError(validarPropuesta(leerFormulario()));
  for (const nombre of camposValidados) {
    if (!conError.has(nombre)) formulario.elements[nombre].removeAttribute('aria-invalid');
  }
}

/**
 * Lleva el foco al primer campo con error, en el orden del formulario.
 * Recibe: el resultado de validarPropuesta. No devuelve nada.
 */
function enfocarPrimerError(errores) {
  const conError = camposConError(errores);
  const primero = camposValidados.find((nombre) => conError.has(nombre));
  formulario.elements[primero].focus();
}

/**
 * Pasa una fecha del input al formato que recibe api.js.
 * Recibe: texto yyyy-MM-dd. Devuelve: texto dd/MM/yyyy.
 */
function aFormatoFormulario(fecha) {
  const [anio, mes, dia] = fecha.split('-');
  return `${dia}/${mes}/${anio}`;
}

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

  const datos = leerFormulario();
  const errores = validarPropuesta(datos);
  marcarCampos(errores);
  if (errores.length > 0) {
    // En este orden: enfocar desplaza la página y el aviso tiene que quedar a la vista.
    enfocarPrimerError(errores);
    mostrarAviso(aviso, errores.map((error) => error.mensaje).join('\n'), 'error');
    return;
  }

  const fechaInicio = aFormatoFormulario(datos.inicio);
  const fechaFin = aFormatoFormulario(datos.fin);
  const presupuesto = Number(datos.presupuesto);

  // El PDF se arma antes de completar: si falla, la tarea sigue pendiente.
  let pdf;
  try {
    pdf = armarPdfPropuesta({
      empresa: variablesSolicitud.empresaNombre,
      tematica: variablesSolicitud.tematica,
      programa: datos.programa,
      presupuesto,
      fechaInicio,
      fechaFin,
    });
  } catch (error) {
    console.error(error);
    mostrarAviso(aviso, 'No se pudo preparar el PDF. La propuesta no se registró.', 'error');
    return;
  }

  // Con el botón deshabilitado tampoco se puede reenviar con Enter.
  botonCompletar.disabled = true;
  try {
    await completarTarea(tareaActual.id, {
      [variables.programa]: datos.programa,
      [variables.presupuesto]: presupuesto,
      [variables.fechaInicioPosible]: fechaInicio,
      [variables.fechaFinPropuesta]: fechaFin,
    });
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
formulario.addEventListener('input', desmarcarCorregidos);
iniciar();
