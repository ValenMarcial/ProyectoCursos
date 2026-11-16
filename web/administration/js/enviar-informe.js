/**
 * Módulo enviar-informe.js
 *
 * Pantalla 6, "Enviar informe a empresa y Archivar expediente": muestra el
 * informe de resultados que elaboró el jefe del equipo docente, los datos del
 * curso y de la solicitud, y completa la tarea.
 *
 * El informe llega por uno de dos caminos (pantalla "Elaborar informe"):
 * - texto escrito: informeResultados tiene el texto y la web generó el PDF;
 * - PDF propio adjunto: informeAdjunto es true y informeResultados queda vacío.
 * En los dos hay un PDF y su ruta está en rutaInforme.
 *
 * La tarea está asignada a jefeAdministrativo en el BPMN: no se reclama.
 * Se completa sin variables. El envío a la empresa no lo hace el sistema: el
 * botón registra que se hizo por fuera. No hay diálogo de confirmación: una
 * línea avisa que no se puede deshacer y el botón queda deshabilitado mientras
 * dura el pedido (igual que en "Archivar solicitud").
 *
 * Toda la comunicación pasa por api.js. Los datos se muestran con textContent:
 * nunca se interpretan como HTML.
 */

import { completarTarea, obtenerVariables } from './api.js';
import { rutasApi, tareas, variables } from './contrato.js';
import { buscarTarea, camposSolicitud, mostrarDatos, recordarTareaEnUrl } from './pantalla-tarea.js';
import { formatearFechaAcordada } from './texto.js';
import { mostrarAviso, mostrarError } from '../../common/js/avisos.js';

/**
 * PENDIENTE: pasar a true cuando el backend tenga GET /api/tareas/{id}/informe
 * (pedido a Juan). Mientras sea false, el enlace para ver el PDF no se muestra:
 * hoy esa ruta solo acepta POST (la subida del informe).
 */
const descargaInformeDisponible = false;

/** Datos del curso que se muestran: variable y etiqueta. */
const camposCurso = [
  [variables.programa, 'Programa'],
  // fechaInicio la manda la empresa al aceptar (texto ISO); fechaFinPropuesta es la de la
  // propuesta y obtenerVariables ya la devuelve en dd/MM/yyyy.
  ['fechaInicio', 'Fecha de inicio', formatearFechaAcordada],
  [variables.fechaFinPropuesta, 'Fecha de fin'],
  [variables.listaDefinitiva, 'Lista definitiva de participantes'],
];

/** Fila del texto del informe. */
const campoTextoInforme = [variables.informeResultados, 'Resultados del curso'];

/** Fila de la ruta del PDF: siempre se muestra (si falta, dice "No informado"). */
const campoRutaInforme = [variables.rutaInforme, 'PDF del informe'];

const aviso = document.getElementById('aviso');
const bloqueInforme = document.getElementById('bloque-informe');
const avisoAdjunto = document.getElementById('aviso-adjunto');
const datosInforme = document.getElementById('datos-informe');
const descargaInforme = document.getElementById('descarga-informe');
const enlaceInforme = document.getElementById('enlace-informe');
const bloqueCurso = document.getElementById('bloque-curso');
const datosCurso = document.getElementById('datos-curso');
const bloqueSolicitud = document.getElementById('bloque-solicitud');
const datosSolicitud = document.getElementById('datos-solicitud');
const bloqueEnviar = document.getElementById('bloque-enviar');
const botonEnviar = document.getElementById('boton-enviar');

// Se completa en iniciar() y lo usa el botón de enviar.
let tareaActual = null;

/**
 * Dice si el docente adjuntó su propio PDF sin escribir texto.
 * Recibe: valores de la instancia. Devuelve: true o false.
 * Como ValidateReport, solo cuenta el booleano true: ausente, false o de otro
 * tipo es "sin PDF adjunto".
 */
function soloPdfAdjunto(valores) {
  const texto = String(valores[variables.informeResultados] ?? '').trim();
  return valores[variables.informeAdjunto] === true && texto === '';
}

/**
 * Muestra el informe: el texto o el aviso de PDF adjunto, la ruta del PDF y,
 * si ya existe el GET, el enlace para verlo.
 * Recibe: valores de la instancia. No devuelve nada.
 */
function mostrarInforme(valores) {
  const adjunto = soloPdfAdjunto(valores);
  avisoAdjunto.hidden = !adjunto;
  // Con PDF adjunto y sin texto, la fila del texto diría "No informado": se reemplaza
  // por el aviso de arriba. En cualquier otro caso se muestra (vacía, si faltara).
  const campos = adjunto ? [campoRutaInforme] : [campoTextoInforme, campoRutaInforme];
  mostrarDatos(datosInforme, campos, valores);

  const hayPdf = Boolean(valores[variables.rutaInforme]);
  descargaInforme.hidden = !(descargaInformeDisponible && hayPdf);
  if (!descargaInforme.hidden) {
    // El backend busca el PDF por la tarea (lee rutaInforme de la instancia): la ruta
    // del archivo nunca viaja desde el navegador.
    enlaceInforme.href = rutasApi.informe(tareaActual.id);
  }
}

/**
 * Completa la tarea sin variables y, si se pudo, oculta el botón.
 * No recibe nada. Devuelve: promesa sin valor.
 */
async function alEnviar() {
  aviso.hidden = true;
  // Deshabilitado mientras dura el pedido: evita completar dos veces con un doble clic.
  botonEnviar.disabled = true;

  try {
    await completarTarea(tareaActual.id);
  } catch (error) {
    // Si falla, la tarea sigue activa: se puede reintentar.
    botonEnviar.disabled = false;
    mostrarError(aviso, error);
    return;
  }

  bloqueEnviar.hidden = true;
  mostrarAviso(aviso, 'Se registró el envío del informe y el expediente quedó archivado. El curso está finalizado.', 'exito');
}

/**
 * Carga la pantalla: busca la tarea, la recuerda en la URL y muestra el informe,
 * los datos del curso y de la solicitud, y el botón de enviar.
 * No recibe nada. Devuelve: promesa sin valor.
 */
async function iniciar() {
  let valores;
  try {
    const busqueda = await buscarTarea({
      clave: tareas.enviarInforme,
      textoSinTareas: 'No hay ningún informe pendiente de enviar.',
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
  mostrarInforme(valores);
  mostrarDatos(datosCurso, camposCurso, valores);
  mostrarDatos(datosSolicitud, camposSolicitud, valores);
  bloqueInforme.hidden = false;
  bloqueCurso.hidden = false;
  bloqueSolicitud.hidden = false;
  bloqueEnviar.hidden = false;
}

botonEnviar.addEventListener('click', alEnviar);
iniciar();
