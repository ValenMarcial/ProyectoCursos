/**
 * Módulo lista-definitiva.js
 *
 * Pantalla 5, "Confeccionar lista definitiva": muestra la nómina que envió la
 * empresa, permite agregar, editar y quitar nombres, valida la lista y completa
 * la tarea.
 *
 * La tarea suele llegar asignada por un listener del motor, y entonces no se
 * reclama. Si llega sin asignar (el listener no encontró a nadie en el grupo y
 * la dejó para Administracion), se reclama antes de mostrar el formulario.
 * Toda la comunicación pasa por api.js. Los datos vienen de afuera: se muestran
 * con textContent o value y nunca se interpretan como HTML.
 */

import { completarTarea, ErrorApi, listarTareas, obtenerVariables, reclamarTarea } from './api.js';
import { tareas, variables } from './contrato.js';
import { quitarAcentos } from './texto.js';

/** Variables del curso que se muestran, con su etiqueta. */
const camposCurso = [
  ['empresaNombre', 'Empresa'],
  ['tematica', 'Temática'],
];

/** Separa los nombres en la lista que se envía, con el mismo formato que la nómina recibida. */
const separadorSalida = ', ';

const mensajeGenerico = 'Ocurrió un error inesperado. Intentá de nuevo más tarde.';

const aviso = document.getElementById('aviso');
const bloqueCurso = document.getElementById('bloque-curso');
const datosCurso = document.getElementById('datos-curso');
const bloqueLista = document.getElementById('bloque-lista');
const formulario = document.getElementById('formulario-lista');
const filasParticipantes = document.getElementById('filas-participantes');
const resumenLista = document.getElementById('resumen-lista');
const botonAgregar = document.getElementById('boton-agregar');
const botonConfirmar = document.getElementById('boton-confirmar');

// Se completan en iniciar() y los usan el envío del formulario y el resumen.
let tareaActual = null;
let participantesEstimados = null;

/**
 * Muestra un aviso y lo trae a la vista.
 * Recibe: texto y tipo ('error', 'exito' o 'neutro'). No devuelve nada.
 */
function mostrarAviso(texto, tipo) {
  aviso.textContent = texto;
  aviso.className = `aviso aviso-${tipo}`;
  aviso.hidden = false;
  aviso.scrollIntoView({ block: 'nearest' });
}

/**
 * Muestra un error atrapado, sin exponer texto técnico al usuario.
 * Recibe: el error. No devuelve nada.
 */
function mostrarError(error) {
  // Solo el message de un ErrorApi es apto para el usuario; el resto va a la consola.
  console.error(error, error?.detalle ?? '');
  mostrarAviso(error instanceof ErrorApi ? error.message : mensajeGenerico, 'error');
}

/**
 * Busca la tarea a mostrar.
 * Usa el parámetro ?tarea=<id> de la URL o, si no viene, la primera disponible.
 * No recibe nada.
 * Devuelve: promesa de { tarea } con la tarea normalizada o, si no se encontró,
 * de { tarea: null, texto, tipo } con el aviso que corresponde mostrar.
 */
async function buscarTarea() {
  const idPedido = new URLSearchParams(window.location.search).get('tarea');
  // Sin filtro porque todavía no hay login: quién es el usuario lo resuelve la sesión.
  const lista = await listarTareas();
  const deEstaPantalla = lista.filter((tarea) => tarea.claveTarea === tareas.confeccionarLista);

  if (idPedido) {
    const pedida = deEstaPantalla.find((tarea) => tarea.id === idPedido);
    // Un id que no aparece es un error, aunque haya otras tareas pendientes.
    return pedida
      ? { tarea: pedida }
      : { tarea: null, texto: 'Esa tarea no existe o ya fue completada.', tipo: 'error' };
  }

  return deEstaPantalla.length > 0
    ? { tarea: deEstaPantalla[0] }
    : { tarea: null, texto: 'No hay ninguna tarea pendiente de confeccionar lista definitiva.', tipo: 'neutro' };
}

/**
 * Dibuja los datos del curso.
 * Recibe: objeto { nombreVariable: valor }. No devuelve nada.
 */
function mostrarCurso(valores) {
  datosCurso.replaceChildren(...camposCurso.flatMap(([nombre, etiqueta]) => {
    const termino = document.createElement('dt');
    termino.textContent = etiqueta;
    const definicion = document.createElement('dd');
    const valor = valores[nombre] ?? '';
    definicion.textContent = valor === '' ? 'No informado' : String(valor);
    return [termino, definicion];
  }));
}

/**
 * Normaliza un nombre para mostrarlo y enviarlo.
 * Recibe: texto. Devuelve: el texto sin espacios en los extremos ni repetidos.
 */
function limpiarNombre(nombre) {
  return nombre.trim().replace(/\s+/g, ' ');
}

/**
 * Arma la clave con la que se detectan duplicados.
 * Recibe: nombre ya limpio.
 * Devuelve: el nombre sin acentos y en minúsculas ("Ana Pérez" da "ana perez").
 */
function claveDeNombre(nombre) {
  return quitarAcentos(nombre).toLocaleLowerCase('es');
}

/**
 * Separa la nómina recibida en nombres.
 * Recibe: la variable participantes, texto con nombres separados por coma, o
 * undefined si la instancia no la tiene.
 * Devuelve: lista de nombres limpios, sin los tramos vacíos. Los duplicados se
 * conservan para que los resuelva quien confecciona la lista.
 */
function separarNomina(participantes) {
  return String(participantes ?? '')
    .split(',')
    .map(limpiarNombre)
    .filter((nombre) => nombre !== '');
}

/**
 * Devuelve los campos de texto de la lista, en el orden en que se ven.
 * No recibe nada. Devuelve: lista de inputs.
 */
function camposDeLista() {
  return [...filasParticipantes.querySelectorAll('input')];
}

/**
 * Lee los nombres cargados.
 * No recibe nada.
 * Devuelve: lista de nombres limpios, con una posición por fila, vacía o no.
 */
function leerNombres() {
  return camposDeLista().map((campo) => limpiarNombre(campo.value));
}

/**
 * Valida la lista.
 * Recibe: el resultado de leerNombres.
 * Devuelve: lista de { indices, mensaje }, donde indices son las filas afectadas;
 * vacía si está todo bien.
 */
function validarLista(nombres) {
  if (nombres.length === 0) {
    return [{ indices: [], mensaje: 'Agregá al menos un participante.' }];
  }

  const errores = [];

  /**
   * Busca las filas que cumplen una condición.
   * Recibe: función que evalúa un nombre. Devuelve: lista de índices.
   */
  const filasQue = (condicion) => nombres.flatMap((nombre, indice) => (condicion(nombre) ? [indice] : []));

  const vacios = filasQue((nombre) => nombre === '');
  if (vacios.length > 0) errores.push({ indices: vacios, mensaje: 'Completá o quitá los nombres vacíos.' });

  // La lista se envía separada por comas: una coma dentro de un nombre lo partiría en dos.
  const conComa = filasQue((nombre) => nombre.includes(','));
  if (conComa.length > 0) errores.push({ indices: conComa, mensaje: 'Los nombres no pueden tener comas.' });

  const filasPorClave = new Map();
  nombres.forEach((nombre, indice) => {
    if (nombre === '') return;
    const clave = claveDeNombre(nombre);
    filasPorClave.set(clave, [...(filasPorClave.get(clave) ?? []), indice]);
  });
  for (const indices of filasPorClave.values()) {
    if (indices.length > 1) {
      errores.push({ indices, mensaje: `El nombre "${nombres[indices[0]]}" está repetido.` });
    }
  }

  return errores;
}

/**
 * Junta las filas afectadas por una lista de errores.
 * Recibe: el resultado de validarLista. Devuelve: Set con los índices.
 */
function filasConError(errores) {
  return new Set(errores.flatMap((error) => error.indices));
}

/**
 * Deja aria-invalid="true" solo en las filas con error.
 * Recibe: el resultado de validarLista. No devuelve nada.
 */
function marcarFilas(errores) {
  const conError = filasConError(errores);
  camposDeLista().forEach((campo, indice) => {
    if (conError.has(indice)) {
      campo.setAttribute('aria-invalid', 'true');
    } else {
      campo.removeAttribute('aria-invalid');
    }
  });
}

/**
 * Saca aria-invalid de las filas que ya están bien, sin marcar ninguna nueva:
 * mientras se escribe no hay que señalar una fila que todavía no se envió.
 * Valida toda la lista porque corregir o quitar un nombre también puede resolver
 * el duplicado marcado en otra fila.
 * No recibe ni devuelve nada.
 */
function desmarcarCorregidas() {
  const conError = filasConError(validarLista(leerNombres()));
  camposDeLista().forEach((campo, indice) => {
    if (!conError.has(indice)) campo.removeAttribute('aria-invalid');
  });
}

/**
 * Lleva el foco a la primera fila con error o, si el error no es de una fila
 * (lista vacía), al botón de agregar.
 * Recibe: el resultado de validarLista. No devuelve nada.
 */
function enfocarPrimerError(errores) {
  const conError = filasConError(errores);
  if (conError.size === 0) {
    botonAgregar.focus();
    return;
  }
  camposDeLista()[Math.min(...conError)].focus();
}

/**
 * Actualiza los textos que dependen de la lista: numeración de las filas y resumen.
 * No recibe ni devuelve nada.
 */
function actualizarTextos() {
  [...filasParticipantes.children].forEach((fila, indice) => {
    const numero = indice + 1;
    fila.querySelector('input').setAttribute('aria-label', `Participante ${numero}`);
    fila.querySelector('button').setAttribute('aria-label', `Quitar participante ${numero}`);
  });

  const cargados = leerNombres().filter((nombre) => nombre !== '').length;
  // La cantidad estimada es solo una referencia: no limita ni valida la lista.
  const referencia = participantesEstimados === null ? '' : ` Participantes estimados: ${participantesEstimados}.`;
  resumenLista.textContent = `Nombres en la lista: ${cargados}.${referencia}`;
}

/**
 * Quita una fila y deja el foco en un lugar útil.
 * Recibe: el elemento de la fila. No devuelve nada.
 */
function quitarFila(fila) {
  const posicion = [...filasParticipantes.children].indexOf(fila);
  fila.remove();

  // El botón pulsado desapareció: sin esto, el foco del teclado se perdería.
  const vecina = filasParticipantes.children[posicion] ?? filasParticipantes.lastElementChild;
  (vecina?.querySelector('input') ?? botonAgregar).focus();

  desmarcarCorregidas();
  actualizarTextos();
}

/**
 * Crea una fila con su campo de texto y su botón para quitarla.
 * Recibe: nombre inicial. Devuelve: el elemento de la fila.
 */
function crearFila(nombre) {
  const fila = document.createElement('div');
  fila.className = 'campo fila-nombre';

  const campo = document.createElement('input');
  campo.type = 'text';
  campo.autocomplete = 'off';
  campo.value = nombre;

  const botonQuitar = document.createElement('button');
  botonQuitar.type = 'button';
  botonQuitar.className = 'boton boton-secundario';
  botonQuitar.textContent = 'Quitar';
  botonQuitar.addEventListener('click', () => quitarFila(fila));

  fila.append(campo, botonQuitar);
  return fila;
}

/**
 * Agrega una fila vacía al final y le da el foco.
 * No recibe ni devuelve nada.
 */
function agregarFila() {
  const fila = crearFila('');
  filasParticipantes.append(fila);
  fila.querySelector('input').focus();
  actualizarTextos();
}

/**
 * Procesa el envío: valida la lista y completa la tarea.
 * Recibe: el evento submit. Devuelve: promesa sin valor.
 */
async function alEnviar(evento) {
  evento.preventDefault();
  aviso.hidden = true;

  const nombres = leerNombres();
  const errores = validarLista(nombres);
  marcarFilas(errores);
  if (errores.length > 0) {
    // En este orden: enfocar desplaza la página y el aviso tiene que quedar a la vista.
    enfocarPrimerError(errores);
    mostrarAviso(errores.map((error) => error.mensaje).join('\n'), 'error');
    return;
  }

  // Con el botón deshabilitado tampoco se puede reenviar.
  botonConfirmar.disabled = true;
  try {
    await completarTarea(tareaActual.id, {
      [variables.listaDefinitiva]: nombres.join(separadorSalida),
    });
  } catch (error) {
    // Si falla, la tarea sigue activa: se conserva la lista para reintentar.
    botonConfirmar.disabled = false;
    mostrarError(error);
    return;
  }

  bloqueLista.hidden = true;
  mostrarAviso('La lista definitiva quedó confirmada.', 'exito');
}

/**
 * Evita que Enter en un nombre confirme la lista: completar la tarea no se
 * puede deshacer y Enter es lo natural después de escribir.
 * Recibe: el evento keydown. No devuelve nada.
 */
function alPresionarTecla(evento) {
  if (evento.key === 'Enter' && evento.target.matches('.fila-nombre input')) {
    evento.preventDefault();
  }
}

/**
 * Carga la pantalla: busca la tarea, la reclama si llegó sin asignar y muestra
 * sus datos y la nómina.
 * No recibe nada. Devuelve: promesa sin valor.
 */
async function iniciar() {
  let valores;
  try {
    const busqueda = await buscarTarea();
    if (!busqueda.tarea) {
      mostrarAviso(busqueda.texto, busqueda.tipo);
      return;
    }
    tareaActual = busqueda.tarea;
    // Sin asignar, la tarea es del grupo: se toma antes de mostrar el formulario para
    // que nadie más trabaje la misma lista. Si otra persona la tomó primero (409) o
    // ya no existe (404), el catch muestra el aviso y el formulario no aparece.
    if (!tareaActual.asignado) {
      await reclamarTarea(tareaActual.id);
    }
    valores = await obtenerVariables(tareaActual.instanciaId);
  } catch (error) {
    mostrarError(error);
    return;
  }

  participantesEstimados = valores.participantesEstimados ?? null;
  const nomina = separarNomina(valores.participantes);

  mostrarCurso(valores);
  filasParticipantes.replaceChildren(...nomina.map(crearFila));
  actualizarTextos();
  bloqueCurso.hidden = false;
  bloqueLista.hidden = false;

  if (nomina.length === 0) {
    mostrarAviso('La nómina llegó vacía. Agregá los participantes antes de confirmar.', 'neutro');
  }
}

formulario.addEventListener('submit', alEnviar);
formulario.addEventListener('keydown', alPresionarTecla);
formulario.addEventListener('input', () => {
  desmarcarCorregidas();
  actualizarTextos();
});
botonAgregar.addEventListener('click', agregarFila);
iniciar();
