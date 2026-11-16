import { cerrarSesion, exigirSesion, listarCursos, obtenerCurso } from "./enterprise-api.js";

const nombreEmpresa = document.getElementById("empresa-nombre");
const botonCerrarSesion = document.getElementById("cerrar-sesion");
const aviso = document.getElementById("notification");

function mostrarError(texto) {
    aviso.textContent = texto;
    aviso.className = "notification notification-error";
    aviso.hidden = false;
}

const listaCursos = document.getElementById("lista-cursos");
const sinCursos = document.getElementById("sin-cursos");

// Traduccion de estados para la vista
const ESTADOS = {
    en_preparacion: "En preparación",
    confirmado: "Confirmado",
    pendiente_de_inicio: "Pendiente de inicio",
    en_desarrollo: "En curso",
    en_elaboracion_informe: "En redacción del informe",
    finalizado: "Finalizado",
    cancelado: "Cancelado",
};

function textoEstado(codigo) {
    return ESTADOS[codigo] || codigo; // si aparece un estado nuevo, se muestra el código
}

// Crea un elemento con clase y texto. textContent evita que un dato con HTML se ejecute.
function crear(etiqueta, clase, texto) {
    const elemento = document.createElement(etiqueta);
    if (clase) elemento.className = clase;
    if (texto !== undefined) elemento.textContent = texto;
    return elemento;
}

// Formateo de fecha
function formatearFecha(iso) {
    if (!iso) return "—";
    const [anio, mes, dia] = iso.split("-");
    return `${dia}/${mes}/${anio}`;
}


// Tipo de Flowable -> texto. Son los tipos que el backend deja pasar (TIPOS_VISIBLES).
const TIPOS_ACTIVIDAD = {
    userTask: "En ejecución",
    serviceTask: "En ejecución",
    scriptTask: "En ejecución",
    receiveTask: "En espera",
    intermediateCatchEvent: "En espera",
};

function textoTipo(tipo) {
    return TIPOS_ACTIVIDAD[tipo] || tipo;
}


function formatearFechaHora(iso) {
    const fecha = new Date(iso);
    if (!iso || Number.isNaN(fecha.getTime())) return "—";
    return fecha.toLocaleString("es-AR", { dateStyle: "short", timeStyle: "short" });
}

function mensajeDeActividades(error) {
    if (error.codigo === 404) return "Este curso ya no está disponible.";
    if (error.codigo === 502) return "No pudimos consultar las actividades ahora. Intente de nuevo.";
    return "No se pudieron cargar las actividades. Intente de nuevo.";
}

function crearListaActividades(actividades) {
    if (actividades.length === 0) {
        return crear("p", "ayuda", "No hay actividades en curso en este momento.");
    }
    const lista = crear("ul", "lista-actividades");
    for (const actividad of actividades) {
        const fila = crear("li");
        fila.append(
            crear("span", "actividad-nombre", actividad.nombre),
            crear("span", "actividad-estado", textoTipo(actividad.tipo)),
            crear("span", "actividad-desde", "Desde " + formatearFechaHora(actividad.desde))
        );
        lista.append(fila);
    }
    return lista;
}

// Desplegable que consulta las actividades recién cuando se abre.
function crearDesplegable(cursoId) {
    const detalle = crear("details", "actividades");
    detalle.append(crear("summary", "", "Actividades en curso"));
    const cuerpo = crear("div", "actividades-cuerpo");
    detalle.append(cuerpo);

    detalle.addEventListener("toggle", async () => {
        if (!detalle.open) return; // al cerrarlo no hay nada que pedir
        cuerpo.replaceChildren(crear("p", "ayuda", "Consultando actividades…"));
        try {
            const curso = await obtenerCurso(cursoId);
            cuerpo.replaceChildren(crearListaActividades(curso.actividades));
        } catch (error) {
            if (error.codigo === 401) {
                location.replace("login.html");
                return;
            }
            cuerpo.replaceChildren(crear("p", "ayuda", mensajeDeActividades(error)));
        }
    });
    return detalle;
}

function crearTarjeta(curso) {
    const tarjeta = crear("article", "curso");

    const cabecera = crear("div", "curso-cabecera");
    const estado = crear("span", "estado", textoEstado(curso.estado));
    estado.dataset.estado = curso.estado; // el CSS pinta distinto los finales
    cabecera.append(crear("h2", "", curso.nombre), estado);

    const datos = crear("dl", "datos");
    datos.append(
        crear("dt", "", "Descripción"),
        crear("dd", "", curso.descripcion || "—"),
        crear("dt", "", "Fecha de inicio"),
        crear("dd", "", formatearFecha(curso.fechaInicio)),
        crear("dt", "", "Fecha de finalización"),
        crear("dd", "", formatearFecha(curso.fechaFin))
    );

    tarjeta.append(cabecera, datos);
    if (curso.estado !== "finalizado" && curso.estado !== "cancelado") {
        tarjeta.append(crearDesplegable(curso.id));
    }
    return tarjeta;
}

async function cargarCursos() {
    try {
        const cursos = await listarCursos();
        if (cursos.length === 0) {
            sinCursos.hidden = false;
            return;
        }
        listaCursos.replaceChildren(...cursos.map(crearTarjeta));
    } catch (error) {
        if (error.codigo === 401) { // la sesión venció con la página abierta
            location.replace("login.html");
            return;
        }
        mostrarError("No se pudieron cargar sus cursos. Intente de nuevo en unos minutos.");
    }
}

async function iniciar() {
    try {
        const sesion = await exigirSesion();
        if (sesion === null) {
            return; // exigirSesion ya redirigió al login
        }
        nombreEmpresa.textContent = sesion.empresa;
        await cargarCursos();
    } catch (error) {
        mostrarError("No se pudo cargar su sesión. Intente de nuevo en unos minutos.");
    }
}

botonCerrarSesion.addEventListener("click", async () => {
    botonCerrarSesion.disabled = true;
    try {
        await cerrarSesion();
        location.replace("login.html");
    } catch (error) {
        if (error.codigo === 401) {
            location.replace("login.html");
            return;
        }
        mostrarError("No se pudo cerrar la sesión. Intente de nuevo.");
        botonCerrarSesion.disabled = false;
    }
});

iniciar();