/**
 * Módulo seccion.js
 *
 * Entrada de cada pantalla del personal (Administración y Equipo docente). Hace tres cosas:
 * - Exige sesión: sin ella lleva al login con ?volver= de la pantalla actual (exigirSesion).
 * - Muestra quién tiene la sesión, con enlaces a la bandeja y al menú de la sección y el
 *   botón "Cerrar sesión", al pie de la cabecera.
 * - Si la persona no es del grupo de la sección, reemplaza el contenido por un aviso con un
 *   enlace a la suya, en lugar de dejar la pantalla de otro grupo a medio funcionar.
 *
 * Es una regla de la interfaz: los datos los protege el backend (ver secciones.js). Si no se
 * puede consultar la sesión (servidor caído), no bloquea: la pantalla muestra su propio error.
 *
 * Todo el texto se escribe con textContent: nunca se interpreta como HTML.
 */

import { rutaLogin } from '../../administration/js/contrato.js';
import { rutaBandeja } from './navegacion.js';
import { destinoPorGrupo, puedeEntrar, secciones } from './secciones.js';
import { exigirSesion } from './sesion.js';

/**
 * Crea un elemento con clase y texto.
 * Recibe: el documento, la etiqueta, la clase (o '') y el texto. Devuelve: el elemento.
 */
function crear(documento, etiqueta, clase, texto) {
  const elemento = documento.createElement(etiqueta);
  if (clase) elemento.className = clase;
  elemento.textContent = texto;
  return elemento;
}

/**
 * Crea el botón que cierra la sesión y lleva al login.
 * Recibe: el texto del botón, la función que cierra la sesión, la ubicación, el documento
 * y la función que avisa de un error (recibe el texto).
 * Devuelve: el botón.
 */
function botonCerrarSesion(texto, cerrar, ubicacion, documento, avisarError) {
  const boton = crear(documento, 'button', 'boton boton-secundario', texto);
  boton.type = 'button';
  boton.addEventListener('click', async () => {
    boton.disabled = true;
    try {
      await cerrar();
      // replace: con "atrás" no se vuelve a una pantalla que pediría sesión otra vez.
      ubicacion.replace(rutaLogin);
    } catch {
      avisarError('No se pudo cerrar la sesión. Probá de nuevo.');
      boton.disabled = false;
    }
  });
  return boton;
}

/**
 * Crea un enlace con el aspecto del botón secundario. Si lleva a la pantalla en la que ya
 * se está, la marca con aria-current="page" (los lectores de pantalla lo anuncian).
 * Recibe: el documento, el texto, la ruta absoluta y la ubicación actual (puede faltar).
 * Devuelve: el elemento <a>.
 */
function enlaceSecundario(documento, texto, ruta, ubicacion) {
  const enlace = crear(documento, 'a', 'boton boton-secundario', texto);
  enlace.href = ruta;
  if (ubicacion?.pathname === ruta) enlace.setAttribute('aria-current', 'page');
  return enlace;
}

/**
 * Crea el enlace al menú (pantalla de inicio) de una sección: "Menú de <nombre>".
 * La usan la barra de sesión y la bandeja común.
 * Recibe: la clave de la sección ('administracion' o 'docentes') y, opcionalmente, el
 * documento y la ubicación (para probarla sin navegador).
 * Devuelve: el elemento <a>, o null si la clave no es de ninguna sección.
 */
export function enlaceAlMenu(clave, documento = globalThis.document, ubicacion = globalThis.location) {
  if (!Object.hasOwn(secciones, clave)) return null;
  const { nombre, inicio } = secciones[clave];
  return enlaceSecundario(documento, `Menú de ${nombre}`, inicio, ubicacion);
}

/**
 * Agrega al final de la cabecera quién tiene la sesión, los enlaces a la bandeja y al menú
 * de la sección, y el botón para cerrarla.
 * Recibe: el usuario, la clave de la sección, la función que cierra la sesión, la ubicación
 * y el documento. No hace nada si la página no tiene cabecera. No devuelve nada.
 */
function mostrarBarraDeSesion(usuario, clave, cerrar, ubicacion, documento) {
  const cabecera = documento.querySelector('header.cabecera');
  if (!cabecera) return;
  const quien = crear(documento, 'span', 'barra-sesion-texto',
    `${usuario.nombre} (${usuario.usuario}) · ${secciones[clave].nombre}`);
  // Enlaces y botón van juntos en un <span>: la barra (flex con space-between) deja así
  // el texto a la izquierda y las acciones a la derecha, sin CSS nuevo.
  const acciones = crear(documento, 'span', '', '');
  acciones.append(
    enlaceSecundario(documento, 'Mi bandeja', rutaBandeja, ubicacion), ' ',
    enlaceAlMenu(clave, documento, ubicacion), ' ',
    botonCerrarSesion('Cerrar sesión', cerrar, ubicacion, documento,
      (texto) => { quien.textContent = texto; }),
  );
  const barra = crear(documento, 'p', 'barra-sesion', '');
  barra.id = 'barra-sesion';
  barra.append(quien, acciones);
  cabecera.append(barra);
}

/**
 * Reemplaza el contenido de la pantalla por el aviso de que la sección es de otro grupo.
 * Recibe: el usuario, la clave de la sección pedida, la función que cierra la sesión, la
 * ubicación y el documento. No hace nada si la página no tiene <main>. No devuelve nada.
 */
function mostrarAccesoDenegado(usuario, clave, cerrar, ubicacion, documento) {
  const principal = documento.querySelector('main');
  if (!principal) return;
  const propio = destinoPorGrupo(usuario.grupos, rutaBandeja);
  const enlace = crear(documento, 'a', 'boton',
    propio === rutaBandeja ? 'Ir a mi bandeja de tareas' : 'Ir a mi sección');
  enlace.href = propio;
  const detalle = crear(documento, 'p', 'aviso', '');
  const acciones = crear(documento, 'p', 'bloque', '');
  acciones.append(enlace, ' ', botonCerrarSesion('Ingresar con otro usuario', cerrar, ubicacion, documento,
    (texto) => { detalle.textContent = texto; detalle.hidden = false; }));
  detalle.hidden = true;
  principal.replaceChildren(
    crear(documento, 'h2', '', `Esta sección es de ${secciones[clave].nombre}`),
    crear(documento, 'p', '',
      `Ingresaste como ${usuario.nombre} (${usuario.usuario}) y tu usuario no pertenece al grupo ${secciones[clave].grupo}.`),
    detalle,
    acciones,
  );
}

/**
 * Protege una pantalla de la sección: exige sesión, muestra la barra de sesión y, si la
 * persona no es del grupo, reemplaza la pantalla por el aviso.
 * Recibe: la clave de la sección ('administracion' o 'docentes') y un objeto con
 * consultarUsuario y cerrarSesion del adaptador de la sección (en modo simulado cada sección
 * tiene sus usuarios), y opcionalmente ubicacion y documento para probarla sin navegador.
 * Devuelve: promesa del usuario si puede entrar; de null si no (sin sesión, de otro grupo o
 * si no se pudo consultar la sesión).
 */
export async function protegerSeccion(clave, {
  consultarUsuario,
  cerrarSesion,
  ubicacion = globalThis.location,
  documento = globalThis.document,
}) {
  let usuario;
  try {
    usuario = await exigirSesion({ consultarUsuario, ubicacion });
  } catch {
    return null; // la pantalla ya muestra el error de conexión con sus propios avisos
  }
  if (!usuario) return null;
  if (!puedeEntrar(usuario.grupos, clave)) {
    mostrarAccesoDenegado(usuario, clave, cerrarSesion, ubicacion, documento);
    return null;
  }
  mostrarBarraDeSesion(usuario, clave, cerrarSesion, ubicacion, documento);
  return usuario;
}
