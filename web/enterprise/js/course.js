import { cerrarSesion, exigirSesion, listarCursos } from "./enterprise-api.js";

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

// 
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