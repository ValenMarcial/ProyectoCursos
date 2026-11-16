import { cerrarSesion } from "./enterprise-api.js";

const botonCerrarSesion = document.getElementById("cerrar-sesion");
const aviso = document.getElementById("notification");

botonCerrarSesion.addEventListener("click", async () => {
    botonCerrarSesion.disabled = true; // evita doble clic mientras se espera
    try {
        await cerrarSesion();
        location.replace("login.html");
    } catch (error) {
        if (error.codigo === 401) {
            // La sesión ya había vencido: la persona quería salir y ya está afuera.
            location.replace("login.html");
            return;
        }
        // Error de red o del servidor: la sesión podría seguir abierta, así que se avisa.
        aviso.textContent = "No se pudo cerrar la sesión. Intente de nuevo.";
        aviso.className = "notification notification-error";
        aviso.hidden = false;
        botonCerrarSesion.disabled = false;
    }
});