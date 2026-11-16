/**
 * Módulo definir-docente.js
 *
 * Pantalla "Definir Docente que desarrolla el curso" (1.4 y 4.1.4). El jefe del
 * equipo docente elige uno de los tres integrantes, incluido él mismo, y completa
 * la tarea. El elegido queda en docenteSeleccionado y el BPMN le asigna
 * "Desarrollar curso" (assignee ${docenteSeleccionado}).
 *
 * La tarea llega asignada al jefe por el BPMN, así que no se reclama. Que el
 * elegido sea del grupo Docentes lo valida el motor (ValidateSelectedTeacher);
 * la pantalla solo exige que se elija alguno.
 */

import { completarTarea, obtenerVariables } from './api-docentes.js';
import { integrantesDocentes, tareasDocentes, variables } from './contrato-docentes.js';
import {
  buscarTarea, marcarModoSimulado, mostrarAviso, mostrarCurso, mostrarError, nombreDocente, recordarTareaEnUrl,
} from './pantalla-docentes.js';

const mensajeSinEleccion = 'Elegí el docente que va a dictar el curso.';

const aviso = document.getElementById('aviso');
const bloqueCurso = document.getElementById('bloque-curso');
const datosCurso = document.getElementById('datos-curso');
const bloqueEleccion = document.getElementById('bloque-eleccion');
const formulario = document.getElementById('formulario-docente');
const opciones = document.getElementById('opciones-docente');
const botonConfirmar = document.getElementById('boton-confirmar');

let tareaActual = null;

/**
 * Agrega una opción por integrante del equipo docente.
 * No recibe nada. No devuelve nada.
 */
function dibujarOpciones() {
  for (const { usuario, nombre } of integrantesDocentes) {
    const etiqueta = document.createElement('label');
    etiqueta.className = 'opcion-docente';

    const radio = document.createElement('input');
    radio.type = 'radio';
    radio.name = 'docente';
    radio.value = usuario;

    const texto = document.createElement('span');
    texto.textContent = nombre;
    const id = document.createElement('span');
    id.className = 'usuario';
    id.textContent = usuario;

    etiqueta.append(radio, texto, id);
    opciones.append(etiqueta);
  }
}

/** Devuelve los radios del formulario. */
function radios() {
  return [...opciones.querySelectorAll('input[type="radio"]')];
}

/**
 * Marca o desmarca el error del grupo de opciones (misma idea que en Evaluar recursos).
 * Recibe: boolean, true si falta elegir.
 */
function marcarError(hayError) {
  for (const radio of radios()) {
    if (hayError) radio.setAttribute('aria-invalid', 'true');
    else radio.removeAttribute('aria-invalid');
  }
  opciones.classList.toggle('opciones-docente-error', hayError);
  opciones.setAttribute('aria-describedby', hayError ? 'ayuda-docente aviso' : 'ayuda-docente');
}

/**
 * Procesa el envío: exige una elección y completa la tarea.
 * Recibe: el evento submit. Devuelve: promesa sin valor.
 */
async function alEnviar(evento) {
  evento.preventDefault();
  aviso.hidden = true;

  const elegido = radios().find((radio) => radio.checked)?.value;
  if (!elegido) {
    marcarError(true);
    radios()[0].focus();
    mostrarAviso(aviso, mensajeSinEleccion, 'error');
    return;
  }
  marcarError(false);

  botonConfirmar.disabled = true;
  try {
    await completarTarea(tareaActual.id, { [variables.docenteSeleccionado]: elegido });
  } catch (error) {
    // Si falla, la tarea sigue activa: se conserva la elección para reintentar.
    botonConfirmar.disabled = false;
    mostrarError(aviso, error);
    return;
  }

  bloqueEleccion.hidden = true;
  mostrarAviso(aviso,
    `Listo: ${nombreDocente(elegido)} va a dictar el curso. La tarea "Desarrollar curso" le aparece cuando llegue la fecha de inicio.`,
    'exito');
}

/**
 * Carga la pantalla: busca la tarea, muestra el curso y las opciones.
 * No recibe nada. Devuelve: promesa sin valor.
 */
async function iniciar() {
  marcarModoSimulado(document.getElementById('modo-simulado'));
  let valores;
  try {
    const busqueda = await buscarTarea(tareasDocentes.definirDocente, 'No tenés cursos pendientes de asignar docente.');
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
  mostrarCurso(datosCurso, valores);
  dibujarOpciones();
  bloqueCurso.hidden = false;
  bloqueEleccion.hidden = false;
}

formulario.addEventListener('submit', alEnviar);
formulario.addEventListener('change', () => marcarError(false));
iniciar();
