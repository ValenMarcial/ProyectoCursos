/**
 * Módulo pantalla-tarea.js
 *
 * Funciones que comparten las pantallas que resuelven una tarea del proceso:
 * buscar qué tarea abrir, recordarla en la URL, mostrar los datos de la
 * instancia y formatear el presupuesto.
 *
 * No muestra avisos ni decide qué hacer con la tarea (tomarla, completarla):
 * eso queda en cada pantalla. Los datos se muestran con textContent: nunca se
 * interpretan como HTML.
 */

import { listarTareas } from './api.js';
import { parametroTarea } from '../../common/js/pantallas.js';

/**
 * Busca la tarea que tiene que abrir una pantalla.
 * Usa el parámetro ?tarea=<id> de la URL o, si no viene, la primera de esa
 * pantalla (o la primera que cumpla preferir, si se indica).
 * Recibe: { clave, textoSinTareas, preferir }:
 * - clave: id de la tarea en el BPMN (de tareas en contrato.js);
 * - textoSinTareas: aviso neutro para cuando no hay ninguna pendiente;
 * - preferir: opcional, función que recibe una tarea y devuelve true si conviene
 *   abrirla antes que las demás (por ejemplo, las sin asignar). Si ninguna la
 *   cumple, se abre la primera.
 * Devuelve: promesa de { tarea } con la tarea normalizada o, si no se encontró,
 * de { tarea: null, texto, tipo } con el aviso que corresponde mostrar.
 */
export async function buscarTarea({ clave, textoSinTareas, preferir = null }) {
  const idPedido = new URLSearchParams(globalThis.location?.search ?? '').get(parametroTarea);
  // Sin alcance trae las propias y las sin asignar de sus grupos: la sesión dice quién es el usuario.
  const lista = await listarTareas();
  const deEstaPantalla = lista.filter((tarea) => tarea.claveTarea === clave);

  if (idPedido) {
    const pedida = deEstaPantalla.find((tarea) => tarea.id === idPedido);
    // Un id que no aparece es un error, aunque haya otras tareas pendientes.
    return pedida
      ? { tarea: pedida }
      : { tarea: null, texto: 'Esa tarea no existe o ya fue completada.', tipo: 'error' };
  }

  const elegida = (preferir && deEstaPantalla.find(preferir)) ?? deEstaPantalla[0];
  return elegida
    ? { tarea: elegida }
    : { tarea: null, texto: textoSinTareas, tipo: 'neutro' };
}

/**
 * Deja guardada la tarea en la URL, para que al recargar se abra la misma.
 * No agrega una entrada al historial: "Atrás" sigue llevando a la pantalla anterior.
 * Recibe: id de la tarea. No devuelve nada.
 */
export function recordarTareaEnUrl(id) {
  const url = new URL(globalThis.location.href);
  url.searchParams.set(parametroTarea, id);
  globalThis.history.replaceState(null, '', url);
}

/**
 * Dibuja una lista de datos (dt con la etiqueta y dd con el valor).
 * Recibe:
 * - lista: el elemento dl donde se dibuja (se reemplaza su contenido);
 * - campos: lista de [nombreVariable, etiqueta, formatear], donde formatear es
 *   opcional y recibe el valor y devuelve el texto a mostrar;
 * - valores: objeto { nombreVariable: valor }.
 * Un valor ausente o vacío se muestra como "No informado".
 * No devuelve nada.
 */
export function mostrarDatos(lista, campos, valores) {
  lista.replaceChildren(...campos.flatMap(([nombre, etiqueta, formatear]) => {
    const termino = document.createElement('dt');
    termino.textContent = etiqueta;
    const definicion = document.createElement('dd');
    // Con ?? y no con ||, para que un 0 se muestre como dato.
    const valor = valores[nombre] ?? '';
    if (valor === '') {
      definicion.textContent = 'No informado';
    } else {
      definicion.textContent = formatear ? formatear(valor) : String(valor);
    }
    return [termino, definicion];
  }));
}

/**
 * Formatea un presupuesto para mostrarlo.
 * Recibe: número entero. Devuelve: texto, por ejemplo "$ 150.000".
 */
export function formatearPresupuesto(presupuesto) {
  return `$ ${new Intl.NumberFormat('es-AR').format(presupuesto)}`;
}
