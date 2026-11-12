/**
 * Módulo bandeja.js
 *
 * Bandeja común del personal (Administración y Docentes): muestra quién tiene la
 * sesión, sus tareas asignadas ("Mis tareas", con enlace a la pantalla que las
 * resuelve) y las tareas sin asignar de sus grupos ("Del grupo", con "Tomar").
 *
 * Quién puede ver o tomar cada tarea lo decide el backend con la sesión: esta
 * pantalla solo muestra lo que devuelve la API. Los datos van con textContent.
 */

import {
  cerrarSesion, isoAFecha, listarTareas, reclamarTarea, usuarioActual,
} from '../../administration/js/api.js';
import { alcances, rutaLogin } from '../../administration/js/contrato.js';
import { mostrarAviso, mostrarError, ocultarAviso } from './avisos.js';
import { urlLogin } from './navegacion.js';
import { enlaceDeTarea } from './pantallas.js';

/** Etiqueta de una tarea que todavía no tiene pantalla en el sitio. */
const textoSinPantalla = 'Sin pantalla en el sitio: se completa desde Flowable';

/** Errores de "Tomar" que indican que la lista quedó vieja: hay que refrescarla. */
const erroresQueRefrescan = ['conflicto', 'noEncontrado'];

const aviso = document.getElementById('aviso');
const textoSesion = document.getElementById('sesion');
const botonCerrarSesion = document.getElementById('cerrar-sesion');
const listas = {
  mias: {
    bloque: document.getElementById('bloque-mias'),
    vacio: document.getElementById('vacio-mias'),
    contenedor: document.getElementById('lista-mias'),
  },
  grupo: {
    bloque: document.getElementById('bloque-grupo'),
    vacio: document.getElementById('vacio-grupo'),
    contenedor: document.getElementById('lista-grupo'),
  },
};

// Número de la última carga pedida: si llegan dos respuestas, se dibuja solo la más nueva.
let ultimaCarga = 0;

/**
 * Pasa la fecha de creación al formato de la pantalla.
 * Recibe: texto ISO o null. Devuelve: dd/MM/yyyy, o null si no se puede leer.
 */
function fechaLegible(iso) {
  try {
    return iso ? isoAFecha(iso) : null;
  } catch {
    return null;
  }
}

/**
 * Arma la tarjeta de una tarea.
 * Recibe: la tarea normalizada y la acción: 'abrir' (mis tareas) o 'tomar' (del grupo).
 * Devuelve: el elemento <article>.
 */
function tarjetaTarea(tarea, accion) {
  const tarjeta = document.createElement('article');
  tarjeta.className = 'tarjeta';

  const titulo = document.createElement('h3');
  titulo.textContent = tarea.nombre;

  const detalle = document.createElement('p');
  const fecha = fechaLegible(tarea.fechaCreacion);
  detalle.textContent = fecha ? `Creada el ${fecha}` : 'Fecha de creación no informada';
  tarjeta.append(titulo, detalle);

  const enlace = enlaceDeTarea(tarea);
  if (accion === 'tomar') {
    const boton = document.createElement('button');
    boton.type = 'button';
    boton.className = 'boton';
    boton.textContent = 'Tomar';
    boton.addEventListener('click', () => tomar(tarea, boton));
    tarjeta.append(boton);
  } else if (enlace) {
    const abrir = document.createElement('a');
    abrir.className = 'boton';
    abrir.href = enlace;
    abrir.textContent = 'Abrir';
    tarjeta.append(abrir);
  }

  // Una tarea sin pantalla se muestra igual, con la etiqueta y sin enlace.
  if (!enlace) {
    const etiqueta = document.createElement('p');
    etiqueta.className = 'estado-tarjeta';
    etiqueta.textContent = textoSinPantalla;
    tarjeta.append(etiqueta);
  }
  return tarjeta;
}

/**
 * Dibuja una de las dos listas.
 * Recibe: la lista ({ bloque, vacio, contenedor }), las tareas y la acción de sus tarjetas.
 */
function dibujarLista(lista, tareas, accion) {
  lista.contenedor.replaceChildren(...tareas.map((tarea) => tarjetaTarea(tarea, accion)));
  lista.vacio.hidden = tareas.length > 0;
  lista.bloque.hidden = false;
}

/**
 * Pide las dos listas al backend y las dibuja.
 * No recibe nada. Devuelve: promesa sin valor. Los errores los muestra en el aviso.
 */
async function cargarListas() {
  ultimaCarga += 1;
  const estaCarga = ultimaCarga;
  try {
    const [mias, grupo] = await Promise.all([
      listarTareas({ alcance: alcances.mias }),
      listarTareas({ alcance: alcances.grupo }),
    ]);
    if (estaCarga !== ultimaCarga) return;
    dibujarLista(listas.mias, mias, 'abrir');
    dibujarLista(listas.grupo, grupo, 'tomar');
  } catch (error) {
    if (estaCarga === ultimaCarga) mostrarError(aviso, error);
  }
}

/**
 * Toma (reclama) una tarea del grupo y refresca las listas.
 * Recibe: la tarea y su botón, que queda deshabilitado mientras dura el pedido.
 * Devuelve: promesa sin valor.
 */
async function tomar(tarea, boton) {
  ocultarAviso(aviso);
  boton.disabled = true;
  try {
    await reclamarTarea(tarea.id);
    // El aviso va antes de recargar: si la recarga falla, su error lo reemplaza.
    mostrarAviso(aviso, `Tomaste la tarea "${tarea.nombre}". Está en Mis tareas.`, 'exito');
    await cargarListas();
  } catch (error) {
    mostrarError(aviso, error);
    if (erroresQueRefrescan.includes(error?.tipo)) {
      await cargarListas();
    } else {
      boton.disabled = false;
    }
  }
}

/**
 * Cierra la sesión y lleva al login.
 * No recibe nada. Devuelve: promesa sin valor.
 */
async function salir() {
  botonCerrarSesion.disabled = true;
  try {
    await cerrarSesion();
    window.location.assign(rutaLogin);
  } catch (error) {
    mostrarError(aviso, error);
    botonCerrarSesion.disabled = false;
  }
}

/**
 * Muestra los datos de la sesión en la cabecera.
 * Recibe: { usuario, nombre, grupos }.
 */
function mostrarSesion(usuario) {
  const grupos = usuario.grupos?.length ? usuario.grupos.join(', ') : 'sin grupos';
  textoSesion.textContent = `${usuario.nombre} (${usuario.usuario}) · Grupos: ${grupos}`;
  textoSesion.hidden = false;
  botonCerrarSesion.hidden = false;
}

/**
 * Arranca la bandeja: sin sesión va al login con ?volver= de esta pantalla; con
 * sesión muestra sus datos y las dos listas.
 * No recibe nada. Devuelve: promesa sin valor.
 */
async function iniciar() {
  botonCerrarSesion.addEventListener('click', salir);
  try {
    const usuario = await usuarioActual();
    if (!usuario) {
      window.location.replace(urlLogin(window.location.pathname + window.location.search));
      return;
    }
    mostrarSesion(usuario);
  } catch (error) {
    mostrarError(aviso, error);
    return;
  }
  await cargarListas();
}

iniciar();
