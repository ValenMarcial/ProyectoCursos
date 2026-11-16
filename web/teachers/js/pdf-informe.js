/**
 * Módulo pdf-informe.js
 *
 * Arma con jsPDF el PDF del informe de resultados cuando el jefe docente lo
 * escribe en el sitio (si adjunta un PDF propio, este módulo no se usa).
 *
 * Mismo criterio que pdf-propuesta.js de Administración: se arma antes de subir
 * y completar, así una falla de jsPDF no deja la tarea completada sin su PDF.
 * jsPDF se carga antes con un script clásico en el HTML y queda en window.jspdf.
 */

import { quitarAcentos } from '../../administration/js/texto.js';

/** Margen de la hoja, en milímetros. */
const margen = 20;

/** Pasa de puntos tipográficos (tamaño de letra) a milímetros (unidad de la hoja). */
const milimetrosPorPunto = 0.3528;

/** Interlineado, como múltiplo del tamaño de letra. */
const interlineado = 1.35;

/**
 * Arma el nombre del archivo a partir de la empresa.
 * Recibe: nombre de la empresa. Devuelve: por ejemplo informe-banco-central-sur.pdf.
 */
function nombreArchivo(empresa) {
  const base = quitarAcentos(empresa ?? '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
  return `informe-${base || 'curso'}.pdf`;
}

/**
 * Arma el PDF del informe, sin mostrarlo ni subirlo.
 * Recibe: { empresa, tematica, docente, inicio, participantes, texto }. Los datos
 * del curso pueden faltar (se escribe "No informado"); el texto es obligatorio.
 * Devuelve: un File con el PDF, listo para subir, y su nombre.
 * Lanza: Error si jsPDF no se cargó.
 */
export function armarPdfInforme({ empresa, tematica, docente, inicio, participantes, texto }) {
  if (!window.jspdf) {
    throw new Error('jsPDF no está cargado: revisar el script de js/vendor en el HTML.');
  }

  const documento = new window.jspdf.jsPDF({ unit: 'mm', format: 'a4' });
  const anchoUtil = documento.internal.pageSize.getWidth() - margen * 2;
  const limiteInferior = documento.internal.pageSize.getHeight() - margen;
  let posicionY = margen;

  /** Escribe un texto con ajuste de línea y agrega hojas si hace falta. */
  function escribir(contenido, { tamanio = 11, negrita = false, espacioDespues = 0 } = {}) {
    documento.setFont('helvetica', negrita ? 'bold' : 'normal');
    documento.setFontSize(tamanio);
    const altoLinea = tamanio * milimetrosPorPunto * interlineado;
    for (const linea of documento.splitTextToSize(String(contenido), anchoUtil)) {
      if (posicionY + altoLinea > limiteInferior) {
        documento.addPage();
        posicionY = margen;
      }
      documento.text(linea, margen, posicionY);
      posicionY += altoLinea;
    }
    posicionY += espacioDespues;
  }

  /** Escribe un dato con su etiqueta en negrita. */
  function escribirDato(etiqueta, valor) {
    escribir(etiqueta, { negrita: true });
    escribir(valor || 'No informado', { espacioDespues: 4 });
  }

  escribir('Informe de resultados', { tamanio: 18, negrita: true, espacioDespues: 8 });
  escribirDato('Empresa', empresa);
  escribirDato('Temática', tematica);
  escribirDato('Docente', docente);
  escribirDato('Inicio del curso', inicio);
  escribirDato('Participantes', participantes);
  escribir('Resultados', { tamanio: 13, negrita: true, espacioDespues: 2 });
  escribir(texto);

  const nombre = nombreArchivo(empresa);
  return new File([documento.output('blob')], nombre, { type: 'application/pdf' });
}
