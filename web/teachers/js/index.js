/**
 * Módulo index.js
 *
 * Bandeja del equipo docente: lista las tareas asignadas al usuario de la sesión
 * (4.1.4). El jefe ve "Definir Docente" y "Elaborar informe de resultados"; cada
 * docente, y también el jefe si se eligió a sí mismo, ve sus "Desarrollar curso".
 *
 * Todas estas tareas llegan asignadas (por el BPMN o por la elección del jefe), así
 * que no se reclaman: cada tarjeta lleva directo a su pantalla.
 */

import { enlaceDeTarea } from '../../common/js/pantallas.js';
import { exigirSesion } from '../../common/js/sesion.js';
import { listarTareas, modoActual, obtenerVariables, usuarioActual } from './api-docentes.js';
import { integrantesDocentes, parametroUsuarioSimulado, tareasDocentes } from './contrato-docentes.js';
import {
  formatearFechaAcordada, marcarModoSimulado, mostrarAviso, mostrarError,
} from './pantalla-docentes.js';

/**
 * Texto del botón de cada tarea docente. La pantalla a la que lleva sale del mapa
 * común (enlaceDeTarea de common/js/pantallas.js), el mismo que usa la bandeja común.
 */
const acciones = {
  [tareasDocentes.definirDocente]: 'Elegir docente',
  [tareasDocentes.desarrollarCurso]: 'Registrar fin del dictado',
  [tareasDocentes.elaborarInforme]: 'Escribir o adjuntar el informe',
};

const aviso = document.getElementById('aviso');
const sesion = document.getElementById('sesion');
const bloqueUsuario = document.getElementById('bloque-usuario-simulado');
const selectorUsuario = document.getElementById('usuario-simulado');
const bloqueTareas = document.getElementById('bloque-tareas');
const listaTareas = document.getElementById('lista-tareas');

/**
 * Arma la tarjeta de una tarea.
 * Recibe: la tarea normalizada y las variables de su instancia. Devuelve: el <li>.
 */
function tarjetaTarea(tarea, valores) {
  const item = document.createElement('li');
  item.className = 'tarjeta';

  const titulo = document.createElement('h3');
  titulo.textContent = tarea.nombre;

  const curso = document.createElement('p');
  curso.className = 'detalle-tarea';
  curso.textContent = [valores.empresaNombre, valores.tematica].filter(Boolean).join(' · ') || 'Curso sin datos';

  const inicio = document.createElement('p');
  inicio.textContent = valores.fechaInicio
    ? `Inicio acordado: ${formatearFechaAcordada(valores.fechaInicio)}`
    : 'Inicio acordado: no informado';

  item.append(titulo, curso, inicio);

  const destino = enlaceDeTarea(tarea);
  if (destino) {
    const enlace = document.createElement('a');
    enlace.className = 'boton';
    enlace.href = destino;
    enlace.textContent = acciones[tarea.claveTarea];
    item.append(enlace);
  }
  return item;
}

/**
 * En modo simulado, ofrece cambiar de integrante del equipo para probar las dos vistas.
 * Recibe: el usuario de la sesión simulada.
 */
function mostrarSelectorUsuario(usuario) {
  selectorUsuario.replaceChildren(...integrantesDocentes.map((integrante) => {
    const opcion = document.createElement('option');
    opcion.value = integrante.usuario;
    opcion.textContent = `${integrante.nombre} (${integrante.usuario})`;
    opcion.selected = integrante.usuario === usuario;
    return opcion;
  }));
  selectorUsuario.addEventListener('change', () => {
    const url = new URL(window.location.href);
    url.searchParams.set(parametroUsuarioSimulado, selectorUsuario.value);
    window.location.assign(url);
  });
  bloqueUsuario.hidden = false;
}

/**
 * Carga la bandeja: quién tiene la sesión y sus tareas docentes, con los datos del curso.
 * Sin sesión va al login común con ?volver= de esta pantalla (exigirSesion).
 * No recibe nada. Devuelve: promesa sin valor.
 */
async function iniciar() {
  marcarModoSimulado(document.getElementById('modo-simulado'));
  try {
    // usuarioActual de docentes: en modo simulado devuelve un integrante del grupo Docentes.
    const usuario = await exigirSesion({ consultarUsuario: usuarioActual });
    if (!usuario) return;
    sesion.textContent = `${usuario.nombre} (${usuario.usuario})`;
    sesion.hidden = false;
    if (modoActual === 'mock') mostrarSelectorUsuario(usuario.usuario);

    const claves = Object.keys(acciones);
    const tareas = (await listarTareas()).filter((tarea) => claves.includes(tarea.claveTarea));
    if (tareas.length === 0) {
      mostrarAviso(aviso, 'No tenés tareas pendientes.', 'neutro');
      return;
    }
    // Las variables se piden en paralelo: una consulta por curso.
    const variables = await Promise.all(tareas.map((tarea) => obtenerVariables(tarea.instanciaId)));
    listaTareas.replaceChildren(...tareas.map((tarea, indice) => tarjetaTarea(tarea, variables[indice])));
    bloqueTareas.hidden = false;
  } catch (error) {
    mostrarError(aviso, error);
  }
}

iniciar();
