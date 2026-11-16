/**
 * Módulo elaborar-informe.js
 *
 * Pantalla "Elaborar informe de resultados" (1.4 y 4.1.3). El jefe del equipo
 * docente tiene dos caminos (docs/contrato.md, "Elaborar informe de resultados"):
 * - Escribe el informe: la web arma el PDF con el texto y los datos del curso, lo
 *   sube y completa con informeResultados y rutaInforme.
 * - Adjunta un PDF propio: la web lo sube y completa con informeAdjunto = true y
 *   rutaInforme; el texto es opcional y, si lo escribió, también se manda.
 *
 * La tarea llega asignada al jefe por el BPMN, así que no se reclama. El PDF se
 * sube antes de completar: si la subida falla no se completa nada, y si se sube
 * pero el motor rechaza la tarea, el archivo queda sin referencia (lo admite el
 * contrato) y se puede reintentar.
 */

import { completarTarea, obtenerVariables, subirInforme } from './api-docentes.js';
import { informe, tareasDocentes, variables } from './contrato-docentes.js';
import { armarPdfInforme } from './pdf-informe.js';
import {
  buscarTarea, camposCurso, formatearFechaAcordada, marcarModoSimulado, mostrarAviso, mostrarCurso,
  mostrarError, nombreDocente, ocultarAviso, recordarTareaEnUrl,
} from './pantalla-docentes.js';

const aviso = document.getElementById('aviso');
const bloqueCurso = document.getElementById('bloque-curso');
const datosCurso = document.getElementById('datos-curso');
const bloqueInforme = document.getElementById('bloque-informe');
const formulario = document.getElementById('formulario-informe');
const campoTexto = document.getElementById('texto-informe');
const campoArchivo = document.getElementById('archivo-informe');
const botonQuitar = document.getElementById('boton-quitar');
const botonGuardar = document.getElementById('boton-guardar');
const bloqueResultado = document.getElementById('bloque-resultado');
const tituloResultado = document.getElementById('titulo-resultado');
const abrirPdf = document.getElementById('abrir-pdf');

let tareaActual = null;
let valoresCurso = {};

/** Valor que muestra la pantalla para un campo del curso (participantes y recursos se calculan). */
function valorCurso(nombre) {
  const campo = camposCurso.find(([clave]) => clave === nombre);
  const valor = campo?.[3] ? campo[3](valoresCurso) : valoresCurso[nombre];
  return valor ? String(valor) : '';
}

/**
 * Revisa el PDF adjunto como el backend: tamaño y cabecera %PDF-.
 * Recibe: el File. Devuelve: promesa del texto de error, o null si sirve.
 */
async function problemaDelPdf(archivo) {
  if (archivo.size > informe.maximoMb * 1024 * 1024) return informe.muyGrande;
  const cabecera = new TextDecoder().decode(await archivo.slice(0, 5).arrayBuffer());
  return archivo.size === 0 || cabecera !== '%PDF-' ? informe.noEsPdf : null;
}

/** Marca o desmarca un campo con error y lo asocia al aviso. */
function marcarCampo(campo, ayuda, hayError) {
  if (hayError) {
    campo.setAttribute('aria-invalid', 'true');
    campo.setAttribute('aria-describedby', `${ayuda} aviso`);
  } else {
    campo.removeAttribute('aria-invalid');
    campo.setAttribute('aria-describedby', ayuda);
  }
}

/** Muestra un error de validación sobre un campo y le pasa el foco. */
function errorEnCampo(campo, ayuda, texto) {
  marcarCampo(campo, ayuda, true);
  mostrarAviso(aviso, texto, 'error');
  campo.focus();
}

/** Muestra u oculta "Quitar el PDF adjunto" según haya archivo elegido. */
function actualizarQuitar() {
  botonQuitar.hidden = campoArchivo.files.length === 0;
}

/**
 * Procesa el envío: valida, arma o toma el PDF, lo sube y completa la tarea.
 * Recibe: el evento submit. Devuelve: promesa sin valor.
 */
async function alEnviar(evento) {
  evento.preventDefault();
  ocultarAviso(aviso);
  marcarCampo(campoTexto, 'ayuda-texto', false);
  marcarCampo(campoArchivo, 'ayuda-archivo', false);

  const texto = campoTexto.value.trim();
  const adjunto = campoArchivo.files[0] ?? null;

  if (adjunto) {
    const problema = await problemaDelPdf(adjunto);
    if (problema) {
      errorEnCampo(campoArchivo, 'ayuda-archivo', problema);
      return;
    }
  } else if (!texto) {
    errorEnCampo(campoTexto, 'ayuda-texto', informe.sinTexto);
    return;
  }

  let pdf = adjunto;
  if (!pdf) {
    try {
      pdf = armarPdfInforme({
        empresa: valorCurso('empresaNombre'),
        tematica: valorCurso('tematica'),
        docente: valoresCurso[variables.docenteSeleccionado] ? nombreDocente(valoresCurso[variables.docenteSeleccionado]) : '',
        inicio: valoresCurso.fechaInicio ? formatearFechaAcordada(valoresCurso.fechaInicio) : '',
        participantes: valorCurso('participantes'),
        texto,
      });
    } catch (error) {
      mostrarError(aviso, error);
      return;
    }
  }

  botonGuardar.disabled = true;
  try {
    const rutaInforme = await subirInforme(tareaActual.id, pdf);
    const valores = { [variables.rutaInforme]: rutaInforme };
    if (texto) valores[variables.informeResultados] = texto;
    if (adjunto) valores[variables.informeAdjunto] = true;
    await completarTarea(tareaActual.id, valores);
  } catch (error) {
    // La tarea sigue activa: se conserva lo escrito y el archivo para reintentar.
    botonGuardar.disabled = false;
    mostrarError(aviso, error);
    return;
  }

  bloqueInforme.hidden = true;
  abrirPdf.href = URL.createObjectURL(pdf);
  bloqueResultado.hidden = false;
  mostrarAviso(aviso,
    'Listo: el informe quedó guardado. Ahora el jefe administrativo lo envía a la empresa y archiva el expediente.',
    'exito');
  tituloResultado.focus();
}

/**
 * Carga la pantalla: busca la tarea y muestra el curso y el formulario.
 * No recibe nada. Devuelve: promesa sin valor.
 */
async function iniciar() {
  marcarModoSimulado(document.getElementById('modo-simulado'));
  try {
    const busqueda = await buscarTarea(tareasDocentes.elaborarInforme, 'No tenés informes pendientes.');
    if (!busqueda.tarea) {
      mostrarAviso(aviso, busqueda.texto, busqueda.tipo);
      return;
    }
    tareaActual = busqueda.tarea;
    valoresCurso = await obtenerVariables(tareaActual.instanciaId);
  } catch (error) {
    mostrarError(aviso, error);
    return;
  }

  recordarTareaEnUrl(tareaActual.id);
  mostrarCurso(datosCurso, valoresCurso);
  bloqueCurso.hidden = false;
  bloqueInforme.hidden = false;
}

formulario.addEventListener('submit', alEnviar);
campoArchivo.addEventListener('change', () => {
  marcarCampo(campoArchivo, 'ayuda-archivo', false);
  actualizarQuitar();
});
campoTexto.addEventListener('input', () => marcarCampo(campoTexto, 'ayuda-texto', false));
botonQuitar.addEventListener('click', () => {
  campoArchivo.value = '';
  actualizarQuitar();
  campoArchivo.focus();
});
iniciar();
