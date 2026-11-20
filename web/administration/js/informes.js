/**
 * Módulo informes.js
 *
 * Pantalla "Informes de cursos finalizados": lista los cursos finalizados de todas las
 * empresas (GET /api/cursos/finalizados) con un enlace al PDF de cada informe
 * (GET /api/cursos/{id}/informe). Solo para el grupo Administracion: lo controla el backend.
 */

import { mostrarAviso } from '../../common/js/avisos.js';

const aviso = document.getElementById('aviso');
const lista = document.getElementById('lista-cursos');
const sinCursos = document.getElementById('sin-cursos');

/** Crea un elemento con clase y texto (textContent: un dato nunca se interpreta como HTML). */
function crear(etiqueta, clase, texto) {
  const elemento = document.createElement(etiqueta);
  if (clase) elemento.className = clase;
  if (texto !== undefined) elemento.textContent = texto;
  return elemento;
}

/** "2026-11-20" -> "20/11/2026". */
function formatearFecha(iso) {
  if (!iso) return '—';
  const [anio, mes, dia] = iso.split('-');
  return `${dia}/${mes}/${anio}`;
}

function crearTarjeta(curso) {
  const tarjeta = crear('article', 'tarjeta');
  tarjeta.append(crear('h3', '', curso.nombre));

  const datos = crear('dl', 'datos');
  datos.append(
    crear('dt', '', 'Empresa'), crear('dd', '', curso.empresa),
    crear('dt', '', 'Inicio'), crear('dd', '', formatearFecha(curso.fechaInicio)),
    crear('dt', '', 'Fin'), crear('dd', '', formatearFecha(curso.fechaFin)),
  );
  tarjeta.append(datos);

  if (curso.informeDisponible) {
    const enlace = crear('a', 'boton boton-secundario', 'Ver informe (PDF)');
    enlace.href = `/api/cursos/${encodeURIComponent(curso.id)}/informe`;
    enlace.target = '_blank';
    enlace.rel = 'noopener';
    tarjeta.append(enlace);
  } else {
    tarjeta.append(crear('p', '', 'Este curso no tiene el PDF del informe.'));
  }
  return tarjeta;
}

async function cargar() {
  let respuesta;
  try {
    respuesta = await fetch('/api/cursos/finalizados', { credentials: 'same-origin' });
  } catch {
    mostrarAviso(aviso, 'No se pudo conectar con el servidor.', 'error');
    return;
  }
  if (respuesta.status === 401) return; // guardia.js ya manda al login
  const cuerpo = await respuesta.json().catch(() => null);
  if (!respuesta.ok) {
    mostrarAviso(aviso, cuerpo?.message || 'No se pudieron cargar los cursos finalizados.', 'error');
    return;
  }
  if (cuerpo.length === 0) {
    sinCursos.hidden = false;
    return;
  }
  lista.replaceChildren(...cuerpo.map(crearTarjeta));
}

cargar();
