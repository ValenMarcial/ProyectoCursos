import { iniciarSesion } from "./enterprise-api.js";

const formulario = document.getElementById("form-login");
const boton = document.getElementById("ingresar");
const aviso = document.getElementById("notification");

function mostrarError(texto) {
    aviso.textContent = texto;
    aviso.className = "notification notification-error";
    aviso.hidden = false;
}

function mensajeDe(error) {
    switch (error.codigo) {
        case undefined: return "No se pudo conectar con el servidor. Revise su conexión e intente de nuevo.";
        case 400: return "Ingrese su usuario y su contraseña.";
        case 401: return "Usuario o contraseña incorrectos.";
        default: return "Ocurrió un problema en el servidor. Intente de nuevo en unos minutos.";
    }
}

formulario.addEventListener("submit", async (evento) => {
    evento.preventDefault();
    aviso.hidden = true;

    const usuario = document.getElementById("usuario").value.trim();
    const clave = document.getElementById("clave").value; 

    boton.disabled = true; // desactivar boton para enviar dos veces
    try {
        await iniciarSesion(usuario, clave);
        // replace en vez de href: el login no queda en el historial del botón "atrás"
        location.replace("course.html");
    } catch (error) {
        mostrarError(mensajeDe(error));
    } finally {
        boton.disabled = false;
    }
});