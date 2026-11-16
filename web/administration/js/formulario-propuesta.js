/**
 * Módulo formulario-propuesta.js
 *
 * Formulario de la propuesta (programa, presupuesto y fechas posibles) que
 * comparten "Registrar y Elaborar propuesta" y "Revisar propuesta": leerlo,
 * validarlo, marcar los errores por campo y convertir los valores a los que
 * reciben api.js y pdf-propuesta.js.
 *
 * No muestra avisos, no toma ni completa la tarea y no arma el PDF: eso queda en
 * cada pantalla. Espera un form con los campos programa, presupuesto,
 * fecha-inicio y fecha-fin (atributo name), como el de registrar-propuesta.html.
 */

import { variables } from './contrato.js';

/** Nombres de los campos que se validan, en el orden en que aparecen en el formulario. */
const camposValidados = ['programa', 'presupuesto', 'fecha-inicio', 'fecha-fin'];

/**
 * Aplica las validaciones del cliente, que anticipan las del listener.
 * Recibe: los datos leídos del formulario ({ programa, presupuesto, inicio, fin }).
 * Devuelve: lista de { campos, mensaje }, donde campos son los nombres de los
 * campos afectados; vacía si está todo bien.
 */
function validarPropuesta({ programa, presupuesto, inicio, fin }) {
  const errores = [];

  if (programa === '') errores.push({ campos: ['programa'], mensaje: 'Completá el programa del curso.' });

  const numero = Number(presupuesto);
  if (presupuesto === '') {
    errores.push({ campos: ['presupuesto'], mensaje: 'Completá el presupuesto.' });
  } else if (!Number.isSafeInteger(numero)) {
    errores.push({ campos: ['presupuesto'], mensaje: 'El presupuesto debe ser un número entero.' });
  } else if (numero < 0) {
    errores.push({ campos: ['presupuesto'], mensaje: 'El presupuesto debe ser mayor o igual a 0.' });
  }

  // Un solo mensaje para las dos fechas, pero se marca solo la que falta.
  const fechasVacias = [];
  if (inicio === '') fechasVacias.push('fecha-inicio');
  if (fin === '') fechasVacias.push('fecha-fin');

  if (fechasVacias.length > 0) {
    errores.push({ campos: fechasVacias, mensaje: 'Completá las fechas de inicio y de finalización.' });
  } else if (fin < inicio) {
    // En formato yyyy-MM-dd, el orden alfabético coincide con el cronológico.
    errores.push({
      campos: ['fecha-fin'],
      mensaje: 'La fecha de finalización debe ser posterior o igual a la fecha de inicio.',
    });
  }

  return errores;
}

/**
 * Junta los campos afectados por una lista de errores.
 * Recibe: el resultado de validarPropuesta.
 * Devuelve: Set con los nombres de esos campos.
 */
function camposConError(errores) {
  return new Set(errores.flatMap((error) => error.campos));
}

/**
 * Pasa una fecha del input al formato que recibe api.js.
 * Recibe: texto yyyy-MM-dd. Devuelve: texto dd/MM/yyyy.
 */
function aFormatoFormulario(fecha) {
  const [anio, mes, dia] = fecha.split('-');
  return `${dia}/${mes}/${anio}`;
}

/**
 * Pasa una fecha de api.js al formato del input: la inversa de aFormatoFormulario.
 * Recibe: texto dd/MM/yyyy (como lo devuelve obtenerVariables), o un valor ausente.
 * Devuelve: texto yyyy-MM-dd, o vacío si el valor no tiene esa forma (el campo
 * queda vacío y la validación normal lo reclama).
 */
function aFormatoInput(fecha) {
  const partes = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(String(fecha ?? ''));
  if (!partes) return '';
  const [, dia, mes, anio] = partes;
  return `${anio}-${mes}-${dia}`;
}

/**
 * Prepara el formulario de la propuesta de una pantalla.
 * Además deja escuchando el evento input del formulario: al corregir un campo se
 * le saca la marca de error (ver desmarcarCorregidos).
 * Recibe: el elemento form.
 * Devuelve: { leer, validar, precargar }:
 * - leer(): { programa, presupuesto, inicio, fin }, todos texto y con las fechas
 *   como las entrega el input (yyyy-MM-dd, o vacío si está incompleta);
 * - validar(datos): recibe el resultado de leer, deja aria-invalid solo en los
 *   campos con error, lleva el foco al primero y devuelve la lista de
 *   { campos, mensaje } (vacía si está todo bien). El aviso lo muestra la pantalla,
 *   después de llamarla: enfocar desplaza la página y el aviso tiene que quedar a la vista;
 * - precargar(valores): llena los campos con la propuesta guardada en la instancia
 *   (lo usa "Revisar propuesta"). Ver precargar() más abajo.
 */
export function crearFormularioPropuesta(formulario) {
  /** Lee el formulario. Ver leer() en la descripción de crearFormularioPropuesta. */
  function leer() {
    return {
      programa: formulario.elements.programa.value.trim(),
      presupuesto: formulario.elements.presupuesto.value.trim(),
      inicio: formulario.elements['fecha-inicio'].value,
      fin: formulario.elements['fecha-fin'].value,
    };
  }

  /**
   * Deja aria-invalid="true" solo en los campos con error.
   * Recibe: el resultado de validarPropuesta. No devuelve nada.
   */
  function marcarCampos(errores) {
    const conError = camposConError(errores);
    for (const nombre of camposValidados) {
      const campo = formulario.elements[nombre];
      if (conError.has(nombre)) {
        campo.setAttribute('aria-invalid', 'true');
      } else {
        campo.removeAttribute('aria-invalid');
      }
    }
  }

  /**
   * Lleva el foco al primer campo con error, en el orden del formulario.
   * Recibe: el resultado de validarPropuesta, con al menos un error. No devuelve nada.
   */
  function enfocarPrimerError(errores) {
    const conError = camposConError(errores);
    const primero = camposValidados.find((nombre) => conError.has(nombre));
    formulario.elements[primero].focus();
  }

  /**
   * Saca aria-invalid de los campos que ya están bien, sin marcar ninguno nuevo:
   * mientras se escribe no hay que señalar un campo que todavía no se envió.
   * Valida todo el formulario porque corregir la fecha de inicio también puede
   * resolver el error marcado en la de finalización.
   * No recibe ni devuelve nada.
   */
  function desmarcarCorregidos() {
    const conError = camposConError(validarPropuesta(leer()));
    for (const nombre of camposValidados) {
      if (!conError.has(nombre)) formulario.elements[nombre].removeAttribute('aria-invalid');
    }
  }

  /** Valida y marca los errores. Ver validar() en la descripción de crearFormularioPropuesta. */
  function validar(datos) {
    const errores = validarPropuesta(datos);
    marcarCampos(errores);
    if (errores.length > 0) enfocarPrimerError(errores);
    return errores;
  }

  /**
   * Llena el formulario con una propuesta ya guardada, para editarla.
   * Recibe: objeto { nombreVariable: valor } como lo devuelve obtenerVariables
   * (nombres de contrato.js: programa, presupuesto, fechaInicioPosible y
   * fechaFinPropuesta, con las fechas en dd/MM/yyyy). Una variable ausente deja el
   * campo vacío. No marca errores: se marcan recién al enviar.
   * No devuelve nada.
   */
  function precargar(valores) {
    // Con ?? y no con ||, para que un presupuesto 0 se precargue.
    formulario.elements.programa.value = valores[variables.programa] ?? '';
    formulario.elements.presupuesto.value = String(valores[variables.presupuesto] ?? '');
    formulario.elements['fecha-inicio'].value = aFormatoInput(valores[variables.fechaInicioPosible]);
    formulario.elements['fecha-fin'].value = aFormatoInput(valores[variables.fechaFinPropuesta]);
  }

  formulario.addEventListener('input', desmarcarCorregidos);
  return { leer, validar, precargar };
}

/**
 * Convierte los datos ya validados a los valores de la propuesta.
 * Recibe: el resultado de leer(), sin errores de validar().
 * Devuelve: { programa, presupuesto (número), fechaInicio y fechaFin (texto
 * dd/MM/yyyy) }: lo que reciben armarPdfPropuesta y variablesPropuesta.
 */
export function convertirPropuesta(datos) {
  return {
    programa: datos.programa,
    presupuesto: Number(datos.presupuesto),
    fechaInicio: aFormatoFormulario(datos.inicio),
    fechaFin: aFormatoFormulario(datos.fin),
  };
}

/**
 * Arma las variables con las que se completa la tarea, con los nombres de contrato.js.
 * Recibe: el resultado de convertirPropuesta.
 * Devuelve: objeto { nombreVariable: valor } para completarTarea.
 */
export function variablesPropuesta({ programa, presupuesto, fechaInicio, fechaFin }) {
  return {
    [variables.programa]: programa,
    [variables.presupuesto]: presupuesto,
    [variables.fechaInicioPosible]: fechaInicio,
    [variables.fechaFinPropuesta]: fechaFin,
  };
}
