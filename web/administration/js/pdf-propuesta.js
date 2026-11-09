/**
 * Módulo pdf-propuesta.js
 *
 * Arma el PDF de una propuesta con jsPDF. Lo usan las pantallas de registrar y
 * de revisar propuesta.
 *
 * Separa armar de descargar: la pantalla arma el documento antes de completar
 * la tarea y lo descarga solo si el motor la aceptó. Así un rechazo no deja un
 * PDF, y una falla de jsPDF no deja una tarea completada sin su PDF.
 *
 * jsPDF se carga antes con un script clásico en el HTML y queda en window.jspdf.
 */

/** Margen de la hoja, en milímetros. */
const margen = 20;

/** Pasa de puntos tipográficos (tamaño de letra) a milímetros (unidad de la hoja). */
const milimetrosPorPunto = 0.3528;

/** Interlineado, como múltiplo del tamaño de letra. */
const interlineado = 1.35;

/**
 * Formatea un presupuesto para mostrarlo.
 * Recibe: número entero. Devuelve: texto, por ejemplo "$ 150.000".
 */
function formatearPresupuesto(presupuesto) {
  return `$ ${new Intl.NumberFormat('es-AR').format(presupuesto)}`;
}

/**
 * Arma el nombre del archivo a partir de la empresa y la versión.
 * Recibe: nombre de la empresa y número de versión.
 * Devuelve: nombre sin acentos ni símbolos, por ejemplo propuesta-tech-solutions-s-a-v1.pdf.
 */
function nombreArchivo(empresa, version) {
  const base = String(empresa ?? '')
    .normalize('NFD')
    // NFD separa la letra de su acento, y este reemplazo borra los acentos.
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
  return `propuesta-${base || 'sin-empresa'}-v${version}.pdf`;
}

/**
 * Arma el PDF de una propuesta, sin descargarlo.
 * Recibe: { empresa, tematica, programa, presupuesto (número), fechaInicio y
 * fechaFin (texto dd/MM/yyyy), version (número, 1 si no se indica) }.
 * Devuelve: { descargar }, una función sin parámetros que baja el archivo y se
 * puede llamar más de una vez.
 * Lanza: Error si jsPDF no se cargó.
 */
export function armarPdfPropuesta({ empresa, tematica, programa, presupuesto, fechaInicio, fechaFin, version = 1 }) {
  if (!window.jspdf) {
    throw new Error('jsPDF no está cargado: revisar el script de js/vendor en el HTML.');
  }

  const documento = new window.jspdf.jsPDF({ unit: 'mm', format: 'a4' });
  const anchoUtil = documento.internal.pageSize.getWidth() - margen * 2;
  const limiteInferior = documento.internal.pageSize.getHeight() - margen;
  let posicionY = margen;

  /**
   * Escribe un texto con ajuste de línea y agrega hojas si hace falta.
   * Recibe: texto y opciones { tamanio en puntos, negrita, espacioDespues en mm }.
   * No devuelve nada: avanza posicionY.
   */
  function escribir(texto, { tamanio = 11, negrita = false, espacioDespues = 0 } = {}) {
    documento.setFont('helvetica', negrita ? 'bold' : 'normal');
    documento.setFontSize(tamanio);
    const altoLinea = tamanio * milimetrosPorPunto * interlineado;

    for (const linea of documento.splitTextToSize(String(texto), anchoUtil)) {
      if (posicionY + altoLinea > limiteInferior) {
        documento.addPage();
        posicionY = margen;
      }
      documento.text(linea, margen, posicionY);
      posicionY += altoLinea;
    }
    posicionY += espacioDespues;
  }

  /**
   * Escribe un dato con su etiqueta en negrita.
   * Recibe: etiqueta y valor. No devuelve nada.
   */
  function escribirDato(etiqueta, valor) {
    escribir(etiqueta, { negrita: true });
    escribir(valor, { espacioDespues: 5 });
  }

  escribir('Propuesta de curso', { tamanio: 18, negrita: true, espacioDespues: 1 });
  escribir(`Versión ${version}`, { espacioDespues: 8 });

  escribirDato('Empresa', empresa);
  escribirDato('Temática', tematica);
  escribirDato('Programa del curso', programa);
  escribirDato('Presupuesto', formatearPresupuesto(presupuesto));
  escribirDato('Fecha de inicio posible', fechaInicio);
  escribirDato('Fecha de finalización posible', fechaFin);

  return {
    descargar() {
      documento.save(nombreArchivo(empresa, version));
    },
  };
}
