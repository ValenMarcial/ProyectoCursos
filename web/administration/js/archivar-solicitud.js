/**
 * Módulo archivar-solicitud.js
 *
 * Pantalla 3, "Archivar solicitud": muestra por qué se cierra la solicitud
 * (la empresa la rechazó o no respondió) y sus datos, y completa la tarea.
 *
 * La tarea está asignada a jefeAdministrativo en el BPMN: no se reclama.
 * Se completa sin variables: el motivo (resultado) ya lo dejó el flujo previo.
 * No hay diálogo de confirmación: una línea avisa que no se puede deshacer y el
 * botón queda deshabilitado mientras dura el pedido.
 *
 * Toda la comunicación pasa por api.js. Los datos se muestran con textContent:
 * nunca se interpretan como HTML.
 */

import { completarTarea, obtenerVariables } from './api.js';
import { tareas } from './contrato.js';
import { buscarTarea, camposSolicitud, mostrarDatos, recordarTareaEnUrl } from './pantalla-tarea.js';
import { mostrarAviso, mostrarError } from '../../common/js/avisos.js';

/**
 * Texto de cada valor de resultado que lleva a "Archivar solicitud".
 * Los valores son los del BPMN: el evento "Rechazo" setea rechazado y el flujo
 * "Sí" de "¿Se enviaron 2 recordatorios?" setea cerrado_por_falta_de_respuesta.
 */
const motivosPorResultado = {
  rechazado: 'La empresa rechazó la propuesta.',
  cerrado_por_falta_de_respuesta: 'La empresa no respondió la propuesta después de dos recordatorios.',
};

/** El motivo se muestra con mostrarDatos, como un dato más traducido a texto. */
const camposMotivo = [
  ['resultado', 'Motivo', textoDelMotivo],
];

const aviso = document.getElementById('aviso');
const bloqueMotivo = document.getElementById('bloque-motivo');
const datosMotivo = document.getElementById('datos-motivo');
const bloqueSolicitud = document.getElementById('bloque-solicitud');
const datosSolicitud = document.getElementById('datos-solicitud');
const bloqueArchivar = document.getElementById('bloque-archivar');
const botonArchivar = document.getElementById('boton-archivar');

// Se completa en iniciar() y lo usa el botón de archivar.
let tareaActual = null;

/**
 * Traduce el valor de resultado al texto que ve el usuario.
 * Recibe: el valor de la variable (ya no vacío: mostrarDatos resuelve el vacío).
 * Devuelve: el texto del motivo; un valor desconocido se muestra tal cual para
 * que se note, en vez de esconderlo detrás de un texto genérico.
 */
function textoDelMotivo(resultado) {
  return motivosPorResultado[resultado] ?? `Resultado no esperado: ${resultado}`;
}

/**
 * Completa la tarea sin variables y, si se pudo, oculta el botón.
 * No recibe nada. Devuelve: promesa sin valor.
 */
async function alArchivar() {
  aviso.hidden = true;
  // Deshabilitado mientras dura el pedido: evita completar dos veces con un doble clic.
  botonArchivar.disabled = true;

  try {
    // Objeto vacío y no undefined: completarTarea recorre las variables con Object.entries.
    await completarTarea(tareaActual.id, {});
  } catch (error) {
    // Si falla, la tarea sigue activa: se puede reintentar.
    botonArchivar.disabled = false;
    mostrarError(aviso, error);
    return;
  }

  bloqueArchivar.hidden = true;
  mostrarAviso(aviso, 'La solicitud quedó archivada.', 'exito');
}

/**
 * Carga la pantalla: busca la tarea, la recuerda en la URL y muestra el motivo,
 * los datos de la solicitud y el botón de archivar.
 * No recibe nada. Devuelve: promesa sin valor.
 */
async function iniciar() {
  let valores;
  try {
    const busqueda = await buscarTarea({
      clave: tareas.archivarSolicitud,
      textoSinTareas: 'No hay ninguna solicitud pendiente de archivar.',
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
  mostrarDatos(datosMotivo, camposMotivo, valores);
  mostrarDatos(datosSolicitud, camposSolicitud, valores);
  bloqueMotivo.hidden = false;
  bloqueSolicitud.hidden = false;
  bloqueArchivar.hidden = false;
}

botonArchivar.addEventListener('click', alArchivar);
iniciar();
