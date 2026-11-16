/**
 * Módulo evaluar-recursos.js
 *
 * Pantalla 4, "Evaluar Recursos": muestra la propuesta aceptada, reclama la
 * tarea de grupo y registra qué recursos requiere el curso.
 *
 * Si la tarea ya es de esta persona, el formulario aparece directo; si está sin
 * asignar, aparece después de reclamarla. Si otra persona la reclamó en el medio,
 * el reclamo responde 409. Abrir la página no reclama nada, para no reservar
 * tareas sin querer.
 *
 * Toda la comunicación pasa por api.js. Los datos se muestran con textContent:
 * nunca se interpretan como HTML.
 */

import { completarTarea, ErrorApi, obtenerVariables, reclamarTarea } from './api.js';
import { mensajeSinRecursos, tareas, variables } from './contrato.js';
import { buscarTarea, formatearPresupuesto, mostrarDatos, recordarTareaEnUrl } from './pantalla-tarea.js';
import { mostrarAviso, mostrarError } from '../../common/js/avisos.js';

/** Datos de la propuesta que se muestran: variable, etiqueta y formato opcional. */
const camposPropuesta = [
  ['empresaNombre', 'Empresa'],
  ['tematica', 'Temática'],
  ['descripcion', 'Descripción'],
  ['participantesEstimados', 'Participantes estimados'],
  [variables.programa, 'Programa del curso'],
  [variables.presupuesto, 'Presupuesto', formatearPresupuesto],
  ['fechaInicio', 'Fecha de inicio acordada', formatearFechaAcordada],
  [variables.fechaInicioPosible, 'Fecha de inicio propuesta'],
  [variables.fechaFinPropuesta, 'Fecha de finalización propuesta'],
];

/** Casillas del formulario: id del elemento y variable que representa. */
const casillasRecursos = [
  ['recurso-aula-fisica', variables.aulaFisica],
  ['recurso-aula-virtual', variables.aulaVirtual],
  ['recurso-equipamiento', variables.equipamiento],
];

/**
 * Errores al reclamar que no se arreglan reintentando: la tarea no se puede tomar
 * (la tomó otra persona, ya no existe o no es de tu grupo).
 */
const erroresDefinitivosAlReclamar = new Set(['conflicto', 'noEncontrado', 'sinPermiso']);

const descripcionBase = 'ayuda-recursos';
const descripcionConError = 'ayuda-recursos aviso';

const aviso = document.getElementById('aviso');
const bloquePropuesta = document.getElementById('bloque-propuesta');
const datosPropuesta = document.getElementById('datos-propuesta');
const bloqueReclamo = document.getElementById('bloque-reclamo');
const botonTomar = document.getElementById('boton-tomar');
const bloqueRecursos = document.getElementById('bloque-recursos');
const formulario = document.getElementById('formulario-recursos');
const grupoRecursos = document.getElementById('grupo-recursos');
const botonConfirmar = document.getElementById('boton-confirmar');
const casillas = casillasRecursos.map(([id]) => document.getElementById(id));

// Se completa en iniciar() y lo usan el reclamo y el envío del formulario.
let tareaActual = null;

/**
 * Formatea la fecha que acordó la empresa.
 * Recibe: texto ISO-8601, como 2027-03-08T09:00:00.
 * Devuelve: texto dd/MM/yyyy, o el valor original si no tiene ese formato.
 */
function formatearFechaAcordada(fecha) {
  // Se toma el texto tal cual: convertirlo a Date podría correr el día por la zona horaria.
  const coincidencia = /^(\d{4})-(\d{2})-(\d{2})/.exec(String(fecha));
  return coincidencia ? `${coincidencia[3]}/${coincidencia[2]}/${coincidencia[1]}` : String(fecha);
}

/**
 * Oculta el bloque de reclamo y muestra el formulario.
 * Recibe: { enfocar }, true para llevar el foco a la primera casilla (después de
 * tomar, porque el botón pulsado desaparece). No devuelve nada.
 */
function mostrarFormulario({ enfocar = false } = {}) {
  recordarTareaEnUrl(tareaActual.id);
  bloqueReclamo.hidden = true;
  bloqueRecursos.hidden = false;
  if (enfocar) casillas[0].focus();
}

/**
 * Reclama la tarea y, si se pudo, muestra el formulario.
 * No recibe nada. Devuelve: promesa sin valor.
 */
async function alTomar() {
  aviso.hidden = true;
  botonTomar.disabled = true;

  try {
    await reclamarTarea(tareaActual.id);
  } catch (error) {
    mostrarError(aviso, error);
    if (error instanceof ErrorApi && erroresDefinitivosAlReclamar.has(error.tipo)) {
      bloqueReclamo.hidden = true;
    } else {
      botonTomar.disabled = false;
    }
    return;
  }

  // El botón pulsado desapareció: sin el foco, el teclado quedaría perdido.
  mostrarFormulario({ enfocar: true });
}

/**
 * Lee las casillas.
 * No recibe nada.
 * Devuelve: objeto { nombreVariable: boolean } con los tres recursos: el gateway
 * evalúa las tres variables, así que las no marcadas también se envían.
 */
function leerRecursos() {
  return Object.fromEntries(casillasRecursos.map(([id, variable], indice) => [variable, casillas[indice].checked]));
}

/**
 * Indica si hay al menos un recurso marcado.
 * Recibe: el resultado de leerRecursos. Devuelve: boolean.
 */
function algunRecursoMarcado(valores) {
  return Object.values(valores).includes(true);
}

/**
 * Marca o desmarca el error del grupo de casillas.
 * aria-invalid va en cada casilla porque ARIA no lo admite en un grupo; la clase
 * es la señal visual, y aria-describedby hace que se lea el aviso al entrar al grupo.
 * Recibe: boolean, true si el grupo tiene error. No devuelve nada.
 */
function marcarGrupo(hayError) {
  for (const casilla of casillas) {
    if (hayError) {
      casilla.setAttribute('aria-invalid', 'true');
    } else {
      casilla.removeAttribute('aria-invalid');
    }
  }
  grupoRecursos.classList.toggle('grupo-casillas-error', hayError);
  grupoRecursos.setAttribute('aria-describedby', hayError ? descripcionConError : descripcionBase);
}

/**
 * Procesa el envío: valida las casillas y completa la tarea.
 * Recibe: el evento submit. Devuelve: promesa sin valor.
 */
async function alEnviar(evento) {
  evento.preventDefault();
  aviso.hidden = true;

  const valores = leerRecursos();
  if (!algunRecursoMarcado(valores)) {
    marcarGrupo(true);
    // En este orden: enfocar desplaza la página y el aviso tiene que quedar a la vista.
    casillas[0].focus();
    mostrarAviso(aviso, mensajeSinRecursos, 'error');
    return;
  }
  marcarGrupo(false);

  // Con el botón deshabilitado tampoco se puede reenviar.
  botonConfirmar.disabled = true;
  try {
    await completarTarea(tareaActual.id, valores);
  } catch (error) {
    // Si falla, la tarea sigue activa: se conservan las casillas para reintentar.
    botonConfirmar.disabled = false;
    mostrarError(aviso, error);
    return;
  }

  bloqueRecursos.hidden = true;
  mostrarAviso(aviso, 'Los recursos quedaron registrados.', 'exito');
}

/**
 * Saca la marca de error cuando ya hay algún recurso marcado. No marca el error
 * si se destildan todas: eso se informa recién al confirmar.
 * No recibe ni devuelve nada.
 */
function alCambiarCasilla() {
  if (algunRecursoMarcado(leerRecursos())) marcarGrupo(false);
}

/**
 * Evita que Enter sobre una casilla confirme: completar la tarea no se puede
 * deshacer, y las casillas se marcan con la barra espaciadora.
 * Recibe: el evento keydown. No devuelve nada.
 */
function alPresionarTecla(evento) {
  if (evento.key === 'Enter' && evento.target.matches('.casilla input')) {
    evento.preventDefault();
  }
}

/**
 * Carga la pantalla: busca la tarea y muestra la propuesta y, según quién la
 * tenga, el formulario (ya es suya) o el botón para tomarla (sin asignar).
 * No recibe nada. Devuelve: promesa sin valor.
 */
async function iniciar() {
  let valores;
  try {
    const busqueda = await buscarTarea({
      clave: tareas.evaluarRecursos,
      textoSinTareas: 'No hay ninguna tarea pendiente de evaluar recursos.',
      // Sin ?tarea se prefiere una sin reclamar: es la que todavía nadie está trabajando.
      preferir: (tarea) => !tarea.asignado,
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

  mostrarDatos(datosPropuesta, camposPropuesta, valores);
  bloquePropuesta.hidden = false;
  // listarTareas solo trae las propias y las sin asignar del grupo: con asignado, es suya.
  // Si se pasa a obtenerTarea (también trae las de compañeros), comparar con usuarioActual().
  if (tareaActual.asignado) {
    mostrarFormulario();
  } else {
    bloqueReclamo.hidden = false;
  }
}

botonTomar.addEventListener('click', alTomar);
formulario.addEventListener('submit', alEnviar);
formulario.addEventListener('change', alCambiarCasilla);
formulario.addEventListener('keydown', alPresionarTecla);
iniciar();
