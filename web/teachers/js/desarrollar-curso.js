/**
 * Módulo desarrollar-curso.js
 *
 * Pantalla "Desarrollar curso" (1.4 y 4.1.4). El docente que eligió el jefe ve el
 * curso y registra la finalización del dictado, que completa la tarea.
 *
 * La tarea existe recién cuando llega la fecha de inicio (timer "Fecha programada")
 * y está asignada al docente elegido, así que no se reclama. No lleva variables:
 * el BPMN no define formulario para esta tarea.
 */

import { completarTarea, obtenerVariables } from './api-docentes.js';
import { tareasDocentes } from './contrato-docentes.js';
import {
  buscarTarea, marcarModoSimulado, mostrarAviso, mostrarCurso, mostrarError, recordarTareaEnUrl,
} from './pantalla-docentes.js';

const aviso = document.getElementById('aviso');
const bloqueCurso = document.getElementById('bloque-curso');
const datosCurso = document.getElementById('datos-curso');
const bloqueFinalizar = document.getElementById('bloque-finalizar');
const botonFinalizar = document.getElementById('boton-finalizar');
const confirmacion = document.getElementById('confirmacion');
const botonConfirmar = document.getElementById('boton-confirmar');
const botonCancelar = document.getElementById('boton-cancelar');

let tareaActual = null;

/** Muestra la confirmación en lugar del botón y lleva el foco a "Sí, terminó". */
function pedirConfirmacion() {
  aviso.hidden = true;
  botonFinalizar.hidden = true;
  confirmacion.hidden = false;
  botonConfirmar.focus();
}

/** Vuelve al botón inicial sin hacer nada. */
function cancelar() {
  confirmacion.hidden = true;
  botonFinalizar.hidden = false;
  botonFinalizar.focus();
}

/**
 * Completa la tarea.
 * No recibe nada. Devuelve: promesa sin valor.
 */
async function confirmar() {
  botonConfirmar.disabled = true;
  botonCancelar.disabled = true;
  try {
    await completarTarea(tareaActual.id, {});
  } catch (error) {
    botonConfirmar.disabled = false;
    botonCancelar.disabled = false;
    mostrarError(aviso, error);
    return;
  }
  bloqueFinalizar.hidden = true;
  mostrarAviso(aviso, 'Quedó registrada la finalización del dictado. El jefe del equipo ya puede elaborar el informe.', 'exito');
}

/**
 * Carga la pantalla: busca la tarea y muestra el curso.
 * No recibe nada. Devuelve: promesa sin valor.
 */
async function iniciar() {
  marcarModoSimulado(document.getElementById('modo-simulado'));
  let valores;
  try {
    const busqueda = await buscarTarea(tareasDocentes.desarrollarCurso, 'No tenés cursos para dictar en este momento.');
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
  bloqueCurso.hidden = false;
  bloqueFinalizar.hidden = false;
}

botonFinalizar.addEventListener('click', pedirConfirmacion);
botonCancelar.addEventListener('click', cancelar);
botonConfirmar.addEventListener('click', confirmar);
iniciar();
