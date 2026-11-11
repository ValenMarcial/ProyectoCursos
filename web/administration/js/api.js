/**
 * Módulo api.js
 *
 * Adaptador entre las pantallas de Administración y la API del backend (/api/...).
 * Las pantallas usan listarTareas, reclamarTarea, completarTarea y obtenerVariables;
 * obtenerTarea queda disponible para la bandeja y la PR de reutilización.
 * iniciarSesion, usuarioActual y cerrarSesion son para el login (de Santos).
 *
 * La interfaz es la misma en modo mock y en modo real: lo único que cambia es
 * la función de transporte, elegida con modoApi en contrato.js. Las conversiones
 * y la traducción de errores corren igual en los dos modos.
 *
 * Este módulo no decide permisos ni guarda credenciales: quién puede reclamar
 * o completar lo controla el backend.
 */

import { alcances, modoApi, rutaLogin, rutasApi, tamanioPagina, tiposVariables } from './contrato.js';
import { enviarSimulado } from './mock.js';

/**
 * Error que reciben las pantallas. El campo message siempre se puede mostrar.
 *
 * Campos:
 * - tipo: 'entrada', 'negocio', 'conflicto', 'noEncontrado', 'sinSesion',
 *   'credenciales' (login rechazado), 'sinPermiso', 'noDisponible' (el backend
 *   no llega al motor, 502), 'interno' o 'red'.
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
 * Indica si un error es un rechazo de negocio que se puede mostrar tal cual.
 * Regla de docs/contrato.md: las validaciones del motor lanzan
 * FlowableIllegalArgumentException, que la API REST devuelve como 400 con el
 * texto en exception (el backend lo reenvía sin tocarlo). Un 400 sin ese texto
 * no es del motor.
 * Recibe: status HTTP y cuerpo. Devuelve: boolean.
 */
function esRechazoDeNegocio(estado, cuerpo) {
  return estado === 400 && typeof cuerpo?.exception === 'string' && cuerpo.exception.trim() !== '';
}

/**
 * Devuelve el mensaje si el error lo escribió el backend, o null si no.
 * El backend responde { message } en español, pensado para el usuario; el motor
 * siempre agrega exception, y el error estándar de Spring (405, 500) no trae message.
 * Recibe: cuerpo de la respuesta. Devuelve: texto o null.
 */
function mensajeDelBackend(cuerpo) {
  const esDelBackend = typeof cuerpo?.message === 'string' && cuerpo.message.trim() !== ''
    && cuerpo.exception === undefined;
  return esDelBackend ? cuerpo.message : null;
}

/**
 * Convierte una respuesta de error en un ErrorApi.
 * Recibe: status HTTP, cuerpo { message, exception } si lo hay, y { esLogin }:
 * en el login un 401 son credenciales incorrectas, no una sesión vencida.
 * Devuelve: el ErrorApi listo para lanzar.
 */
function traducirError(estado, cuerpo, { esLogin = false } = {}) {
  const detalle = cuerpo?.exception ?? cuerpo?.message ?? null;
  const delBackend = mensajeDelBackend(cuerpo);

  if (esRechazoDeNegocio(estado, cuerpo)) {
    return new ErrorApi(cuerpo.exception, { tipo: 'negocio', estado, detalle });
  }

  // Un 500 siempre cae en el mensaje genérico, aunque su texto parezca de negocio:
  // es un bug o una falla de infraestructura, y su detalle queda solo para la consola.
  switch (estado) {
    case 400:
      // Validación del backend (por ejemplo, una variable no permitida): se muestra tal cual.
      if (delBackend) return new ErrorApi(delBackend, { tipo: 'entrada', estado, detalle });
      break;
    case 401:
      if (esLogin) {
        return new ErrorApi(delBackend ?? 'Usuario o clave incorrectos.', { tipo: 'credenciales', estado, detalle });
      }
      return new ErrorApi('Tu sesión venció o no iniciaste sesión. Volvé a ingresar.', { tipo: 'sinSesion', estado, detalle });
    case 403:
      return new ErrorApi(delBackend ?? 'No tenés permiso para hacer esta operación.', { tipo: 'sinPermiso', estado, detalle });
    case 404:
      return new ErrorApi(delBackend ?? 'La tarea o instancia ya no existe, o ya fue completada.', { tipo: 'noEncontrado', estado, detalle });
    case 409:
      // El 409 viene del motor (cuerpo en inglés): se usa un texto propio.
      return new ErrorApi('Otra persona ya reclamó esta tarea.', { tipo: 'conflicto', estado, detalle });
    case 502:
    case 503:
    case 504:
      return new ErrorApi(delBackend ?? 'El servidor no está disponible en este momento. Intentá de nuevo más tarde.', { tipo: 'noDisponible', estado, detalle });
    default:
      break;
  }
  // El texto técnico queda en detalle: un bug nunca se le muestra al usuario.
  return new ErrorApi('Ocurrió un error inesperado. Intentá de nuevo más tarde.', { tipo: 'interno', estado, detalle });
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
  // same-origin es el valor por defecto; va explícito porque la sesión depende de la
  // cookie: la web y la API se sirven desde el mismo backend.
  const opciones = { method: metodo, credentials: 'same-origin' };
  if (cuerpo !== undefined) {
    opciones.headers = { 'Content-Type': 'application/json' };
    opciones.body = JSON.stringify(cuerpo);
  }

  const respuesta = await fetch(ruta, opciones);
  // Reclamar y completar responden 204 sin cuerpo, y un error puede no ser JSON.
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
 * Recibe: función enviar(metodo, ruta, cuerpo) que devuelve { estado, cuerpo }, y
 * { alPerderSesion }: qué hacer cuando una operación responde 401 (por defecto,
 * nada; el ErrorApi 'sinSesion' se lanza igual para que la pantalla lo muestre).
 * Devuelve: objeto con las operaciones de tareas y de sesión.
 */
export function crearApi(enviar, { alPerderSesion = () => {} } = {}) {
  /**
   * Hace un pedido y unifica el manejo de errores.
   * Recibe: método, ruta, cuerpo opcional y { esLogin, sin401 }:
   * - esLogin: un 401 son credenciales incorrectas.
   * - sin401: el 401 es una respuesta esperada (consultar o cerrar la sesión) y no
   *   dispara alPerderSesion; si no, una pantalla de login que pregunta por la
   *   sesión se redirigiría a sí misma.
   * Devuelve: el cuerpo de la respuesta.
   * Lanza: ErrorApi si no hubo conexión o el status no es 2xx.
   */
  async function pedir(metodo, ruta, cuerpo, { esLogin = false, sin401 = false } = {}) {
    let respuesta;
    try {
      respuesta = await enviar(metodo, ruta, cuerpo);
    } catch (error) {
      throw new ErrorApi('No se pudo conectar con el servidor.', { tipo: 'red', detalle: String(error) });
    }
    if (respuesta.estado < 200 || respuesta.estado >= 300) {
      const error = traducirError(respuesta.estado, respuesta.cuerpo, { esLogin });
      if (error.tipo === 'sinSesion' && !sin401) alPerderSesion(error);
      throw error;
    }
    return respuesta.cuerpo;
  }

  /**
   * Trae todas las tareas de un alcance, recorriendo las páginas del backend.
   * Recibe: alcance ('mias' o 'grupo').
   * Devuelve: lista de tareas en el formato de Flowable.
   */
  async function todasLasPaginas(alcance) {
    const encontradas = [];
    let total = Infinity;
    // Se corta también si una página vuelve vacía, para no quedar en un bucle si el
    // total cambia mientras se recorre (alguien completó una tarea en el medio).
    while (encontradas.length < total) {
      const pagina = await pedir('GET', rutasApi.tareas({ alcance, start: encontradas.length, size: tamanioPagina }));
      total = pagina.total;
      if (pagina.data.length === 0) break;
      encontradas.push(...pagina.data);
    }
    return encontradas;
  }

  return {
    /**
     * Inicia la sesión del personal con su usuario y clave de Flowable.
     * Recibe: usuario y clave. La clave no se guarda: solo viaja en este pedido.
     * Devuelve: { usuario, nombre, grupos }.
     * Lanza: ErrorApi 'credenciales' si el backend los rechaza (401), 'entrada' si
     * faltan datos (400) o 'noDisponible' si Flowable no responde (502).
     */
    async iniciarSesion(usuario, clave) {
      return pedir('POST', rutasApi.sesion, { usuario, clave }, { esLogin: true });
    },

    /**
     * Consulta quién tiene la sesión iniciada.
     * No recibe nada. Devuelve: { usuario, nombre, grupos }, o null si no hay sesión.
     */
    async usuarioActual() {
      try {
        return await pedir('GET', rutasApi.sesion, undefined, { sin401: true });
      } catch (error) {
        if (error instanceof ErrorApi && error.tipo === 'sinSesion') return null;
        throw error;
      }
    },

    /**
     * Cierra la sesión. Si ya estaba cerrada (401) no es un error.
     * No recibe ni devuelve nada.
     */
    async cerrarSesion() {
      try {
        await pedir('DELETE', rutasApi.sesion, undefined, { sin401: true });
      } catch (error) {
        if (!(error instanceof ErrorApi && error.tipo === 'sinSesion')) throw error;
      }
    },

    /**
     * Lista tareas del usuario de la sesión.
     * Recibe: { alcance } con 'mias' (asignadas) o 'grupo' (sin asignar, de sus
     * grupos). Sin alcance trae las dos, primero las propias y sin repetir: es lo
     * que usan las pantallas, que después filtran por tarea del BPMN.
     * Devuelve: lista de tareas normalizadas.
     */
    async listarTareas({ alcance } = {}) {
      const pedidos = alcance ? [alcance] : [alcances.mias, alcances.grupo];
      const vistas = new Set();
      const resultado = [];
      for (const cadaAlcance of pedidos) {
        for (const tarea of await todasLasPaginas(cadaAlcance)) {
          // Una tarea puede cambiar de alcance entre los dos pedidos (alguien la reclamó).
          if (vistas.has(tarea.id)) continue;
          vistas.add(tarea.id);
          resultado.push(normalizarTarea(tarea));
        }
      }
      return resultado;
    },

    /**
     * Lee una tarea del usuario o de uno de sus grupos.
     * Recibe: id de la tarea. Devuelve: la tarea normalizada.
     * Lanza: ErrorApi 'noEncontrado' si no existe o no la puede ver.
     */
    async obtenerTarea(id) {
      return normalizarTarea(await pedir('GET', rutasApi.tarea(id)));
    },

    /**
     * Reclama una tarea de grupo.
     * Recibe: id de la tarea. Devuelve: nada.
     * Va sin cuerpo: el backend toma el assignee de la sesión, no del navegador, así
     * nadie puede reclamar a nombre de otro, y nunca le manda a Flowable un claim
     * sin usuario (que libera la tarea en vez de tomarla).
     */
    async reclamarTarea(id) {
      await pedir('POST', rutasApi.reclamar(id));
    },

    /**
     * Completa una tarea asignada al usuario de la sesión.
     * Recibe: id y objeto { nombreVariable: valor }, con fechas en dd/MM/yyyy.
     * Devuelve: nada.
     */
    async completarTarea(id, valores) {
      await pedir('POST', rutasApi.completar(id), { variables: armarVariables(valores) });
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

/**
 * Manda al usuario a la pantalla de login, con la pantalla actual en ?volver=
 * para que el login lo devuelva. Mientras rutaLogin sea null no hace nada: el
 * aviso de sesión vencida lo muestra la pantalla con el ErrorApi.
 * No recibe ni devuelve nada.
 */
function redirigirALogin() {
  if (rutaLogin === null || typeof window === 'undefined') return;
  const volver = window.location.pathname + window.location.search;
  window.location.assign(`${rutaLogin}?${new URLSearchParams({ volver })}`);
}

export const {
  iniciarSesion, usuarioActual, cerrarSesion,
  listarTareas, obtenerTarea, reclamarTarea, completarTarea, obtenerVariables,
} = crearApi(elegirTransporte(), { alPerderSesion: redirigirALogin });
