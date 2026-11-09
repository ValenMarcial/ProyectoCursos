/**
 * Módulo api.js
 *
 * Adaptador entre las pantallas de Administración y la API. Las pantallas solo
 * usan listarTareas, reclamarTarea, completarTarea y obtenerVariables.
 *
 * La interfaz es la misma en modo mock y en modo real: lo único que cambia es
 * la función de transporte, elegida con modoApi en contrato.js. Las conversiones
 * y la traducción de errores corren igual en los dos modos.
 *
 * Este módulo no decide permisos ni guarda credenciales: quién puede reclamar
 * o completar lo controla el backend.
 */

import { mensajesNegocio, modoApi, rutasApi, tiposVariables } from './contrato.js';
import { enviarSimulado } from './mock.js';

/**
 * Error que reciben las pantallas. El campo message siempre se puede mostrar.
 *
 * Campos:
 * - tipo: 'entrada', 'negocio', 'conflicto', 'noEncontrado', 'sinSesion',
 *   'sinPermiso', 'interno' o 'red'.
 * - estado: status HTTP, o null si no hubo respuesta.
 * - detalle: texto técnico para la consola; no mostrar al usuario.
 */
export class ErrorApi extends Error {
  /**
   * Recibe: el mensaje para el usuario y un objeto { tipo, estado, detalle }.
   */
  constructor(mensaje, { tipo, estado = null, detalle = null }) {
    super(mensaje);
    this.name = 'ErrorApi';
    this.tipo = tipo;
    this.estado = estado;
    this.detalle = detalle;
  }
}

const patronFechaFormulario = /^(\d{2})\/(\d{2})\/(\d{4})$/;

/**
 * Convierte una fecha del formulario a ISO.
 * Recibe: texto dd/MM/yyyy.
 * Devuelve: texto ISO a medianoche UTC, por ejemplo 2026-12-10T00:00:00.000Z.
 * Lanza: ErrorApi de tipo 'entrada' si el formato o la fecha no son válidos.
 */
export function fechaAIso(fecha) {
  const coincidencia = patronFechaFormulario.exec(String(fecha ?? '').trim());
  if (!coincidencia) {
    throw new ErrorApi(`La fecha "${fecha}" debe tener el formato dd/MM/aaaa.`, { tipo: 'entrada' });
  }

  const dia = Number(coincidencia[1]);
  const mes = Number(coincidencia[2]);
  const anio = Number(coincidencia[3]);

  // Se trabaja en UTC para que la zona horaria del navegador no corra el día.
  const resultado = new Date(Date.UTC(anio, mes - 1, dia));

  // Date acomoda las fechas imposibles (31/02 pasa a marzo): si algún componente
  // cambió, la fecha no existía.
  const esValida = resultado.getUTCFullYear() === anio
    && resultado.getUTCMonth() === mes - 1
    && resultado.getUTCDate() === dia;
  if (!esValida) {
    throw new ErrorApi(`La fecha "${fecha}" no existe.`, { tipo: 'entrada' });
  }

  return resultado.toISOString();
}

/**
 * Convierte una fecha ISO al formato del formulario.
 * Recibe: texto ISO.
 * Devuelve: texto dd/MM/yyyy, tomando el día en UTC.
 * Lanza: ErrorApi de tipo 'entrada' si el texto no es una fecha.
 */
export function isoAFecha(iso) {
  // JavaScript no interpreta el desplazamiento sin dos puntos (+0000).
  const normalizada = String(iso ?? '').replace(/([+-]\d{2})(\d{2})$/, '$1:$2');
  const fecha = new Date(normalizada);
  if (Number.isNaN(fecha.getTime())) {
    throw new ErrorApi(`El valor "${iso}" no es una fecha válida.`, { tipo: 'entrada' });
  }

  const dia = String(fecha.getUTCDate()).padStart(2, '0');
  const mes = String(fecha.getUTCMonth() + 1).padStart(2, '0');
  return `${dia}/${mes}/${fecha.getUTCFullYear()}`;
}

/**
 * Adapta un valor del formulario al tipo que espera Flowable.
 * Recibe: nombre de la variable, valor y tipo de Flowable.
 * Devuelve: el valor convertido.
 * Lanza: ErrorApi de tipo 'entrada' si el valor no corresponde al tipo.
 */
function convertirValor(nombre, valor, tipo) {
  switch (tipo) {
    case 'long': {
      // Los inputs entregan texto. Un texto vacío queda como está y se rechaza abajo.
      const numero = typeof valor === 'string' && valor.trim() !== '' ? Number(valor) : valor;
      if (!Number.isSafeInteger(numero)) {
        throw new ErrorApi(`El campo ${nombre} debe ser un número entero.`, { tipo: 'entrada' });
      }
      return numero;
    }
    case 'date':
      return fechaAIso(valor);
    case 'boolean':
    case 'string':
      if (typeof valor !== tipo) {
        throw new ErrorApi(`El campo ${nombre} tiene un valor inválido.`, { tipo: 'entrada' });
      }
      return valor;
    default:
      throw new ErrorApi(`El campo ${nombre} tiene un tipo desconocido.`, { tipo: 'entrada' });
  }
}

/**
 * Arma la lista de variables en el formato de Flowable.
 * Recibe: objeto { nombreVariable: valor }.
 * Devuelve: lista de { name, type, value }, siempre con el tipo explícito.
 * Lanza: ErrorApi de tipo 'entrada' si la variable no está en contrato.js.
 */
function armarVariables(valores) {
  return Object.entries(valores).map(([nombre, valor]) => {
    // Sin este corte, una variable nueva saldría sin tipo y fallaría recién en el motor.
    if (!Object.hasOwn(tiposVariables, nombre)) {
      throw new ErrorApi(`La variable ${nombre} no está definida en contrato.js.`, { tipo: 'entrada' });
    }
    const tipo = tiposVariables[nombre];
    return { name: nombre, type: tipo, value: convertirValor(nombre, valor, tipo) };
  });
}

/**
 * Convierte una respuesta de error en un ErrorApi.
 * Recibe: status HTTP y cuerpo { message, exception }, si lo hay.
 * Devuelve: el ErrorApi listo para lanzar.
 */
function traducirError(estado, cuerpo) {
  const detalle = cuerpo?.exception ?? cuerpo?.message ?? null;

  // Va primero porque el status no distingue validaciones de bugs: ambos son 500.
  if (mensajesNegocio.has(cuerpo?.exception)) {
    return new ErrorApi(cuerpo.exception, { tipo: 'negocio', estado, detalle });
  }

  switch (estado) {
    case 401:
      return new ErrorApi('Tu sesión no es válida. Volvé a ingresar.', { tipo: 'sinSesion', estado, detalle });
    case 403:
      return new ErrorApi('No tenés permiso para hacer esta operación.', { tipo: 'sinPermiso', estado, detalle });
    case 404:
      return new ErrorApi('La tarea o instancia ya no existe, o ya fue completada.', { tipo: 'noEncontrado', estado, detalle });
    case 409:
      return new ErrorApi('Otra persona ya reclamó esta tarea.', { tipo: 'conflicto', estado, detalle });
    default:
      // El texto técnico queda en detalle: un bug nunca se le muestra al usuario.
      return new ErrorApi('Ocurrió un error inesperado. Intentá de nuevo más tarde.', { tipo: 'interno', estado, detalle });
  }
}

/**
 * Pasa una tarea del formato de Flowable al que usan las pantallas.
 * Recibe: tarea de Flowable.
 * Devuelve: { id, nombre, asignado, claveTarea, instanciaId, fechaCreacion }.
 */
function normalizarTarea(tarea) {
  return {
    id: tarea.id,
    nombre: tarea.name,
    asignado: tarea.assignee,
    claveTarea: tarea.taskDefinitionKey,
    instanciaId: tarea.processInstanceId,
    fechaCreacion: tarea.createTime,
  };
}

/**
 * Transporte real: llama al backend con fetch.
 * Recibe: método HTTP, ruta y cuerpo opcional.
 * Devuelve: promesa de { estado, cuerpo }; cuerpo es null si no hay JSON.
 */
export async function enviarReal(metodo, ruta, cuerpo) {
  const opciones = { method: metodo };
  if (cuerpo !== undefined) {
    opciones.headers = { 'Content-Type': 'application/json' };
    opciones.body = JSON.stringify(cuerpo);
  }

  const respuesta = await fetch(ruta, opciones);
  // Reclamar y completar responden 200 sin cuerpo, y un error puede no ser JSON.
  const texto = await respuesta.text();
  let datos = null;
  try {
    datos = texto ? JSON.parse(texto) : null;
  } catch {
    datos = null;
  }
  return { estado: respuesta.status, cuerpo: datos };
}

/**
 * Crea el adaptador sobre un transporte.
 * Recibe: función enviar(metodo, ruta, cuerpo) que devuelve { estado, cuerpo }.
 * Devuelve: objeto con las cuatro operaciones de la interfaz.
 */
export function crearApi(enviar) {
  /**
   * Hace un pedido y unifica el manejo de errores.
   * Recibe: método, ruta y cuerpo opcional.
   * Devuelve: el cuerpo de la respuesta.
   * Lanza: ErrorApi si no hubo conexión o el status no es 2xx.
   */
  async function pedir(metodo, ruta, cuerpo) {
    let respuesta;
    try {
      respuesta = await enviar(metodo, ruta, cuerpo);
    } catch (error) {
      throw new ErrorApi('No se pudo conectar con el servidor.', { tipo: 'red', detalle: String(error) });
    }
    if (respuesta.estado < 200 || respuesta.estado >= 300) {
      throw traducirError(respuesta.estado, respuesta.cuerpo);
    }
    return respuesta.cuerpo;
  }

  return {
    /**
     * Lista tareas.
     * Recibe: { usuario } para las asignadas o { grupo } para las disponibles.
     * Devuelve: lista de tareas normalizadas.
     */
    async listarTareas({ usuario, grupo } = {}) {
      const cuerpo = await pedir('GET', rutasApi.tareas({ usuario, grupo }));
      return cuerpo.data.map(normalizarTarea);
    },

    /**
     * Reclama una tarea de grupo.
     * Recibe: id de la tarea. Devuelve: nada.
     * No manda assignee: el backend lo toma de la sesión, no del navegador.
     */
    async reclamarTarea(id) {
      await pedir('POST', rutasApi.tarea(id), { action: 'claim' });
    },

    /**
     * Completa una tarea.
     * Recibe: id y objeto { nombreVariable: valor }, con fechas en dd/MM/yyyy.
     * Devuelve: nada.
     */
    async completarTarea(id, valores) {
      await pedir('POST', rutasApi.tarea(id), { action: 'complete', variables: armarVariables(valores) });
    },

    /**
     * Lee las variables de una instancia.
     * Recibe: id de la instancia.
     * Devuelve: objeto { nombreVariable: valor }, con fechas en dd/MM/yyyy.
     */
    async obtenerVariables(instanciaId) {
      const lista = await pedir('GET', rutasApi.variables(instanciaId));
      return Object.fromEntries(lista.map(({ name, type, value }) => [
        name,
        type === 'date' && value !== null ? isoAFecha(value) : value,
      ]));
    },
  };
}

/** Elige el transporte según el único flag de configuración. */
function elegirTransporte() {
  if (modoApi === 'mock') return enviarSimulado;
  if (modoApi === 'real') return enviarReal;
  throw new Error(`modoApi inválido en contrato.js: ${modoApi}`);
}

export const { listarTareas, reclamarTarea, completarTarea, obtenerVariables } = crearApi(elegirTransporte());
