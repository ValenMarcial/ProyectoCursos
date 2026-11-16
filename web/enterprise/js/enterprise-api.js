// Capa de acceso a la API de empresas.

const RUTA_SESION = "/api/empresa/sesion";

// Funcion que hace el fetch, convierte la respuesta y transforma los errores HTTP
async function pedir(ruta, opciones = {}) {
    // credentials: "same-origin" hace que el navegador guarde y envíe la cookie de sesión.
    const respuesta = await fetch(ruta, { credentials: "same-origin", ...opciones });

    const cuerpo = await respuesta.json().catch(() => null);

    if (!respuesta.ok) {
        // El campo del mensaje puede llamarse "mensaje" o "message": se aceptan ambos.
        const texto = (cuerpo && (cuerpo.mensaje || cuerpo.message)) || `Error ${respuesta.status}`;
        const error = new Error(texto);
        error.codigo = respuesta.status;
        throw error;
    }
    return cuerpo;
}

// POST /api/empresa/sesion -> { usuario, empresa }
export function iniciarSesion(usuario, clave) {
    return pedir(RUTA_SESION, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ usuario, clave }),
    });
}

// GET /api/empresa/sesion -> { usuario, empresa } o error 401 si no hay sesión
export function obtenerSesion() {
    return pedir(RUTA_SESION);
}

// DELETE /api/empresa/sesion -> 204 sin cuerpo
export function cerrarSesion() {
    return pedir(RUTA_SESION, { method: "DELETE" });
}

// Guardia de sesión para las pantallas que la necesitan.
// Devuelve los datos de la sesión, o null si hay que ir al login (y ya redirige).
// Cualquier otro error (backend caído, 500) se propaga para que la pantalla lo muestre.
export async function exigirSesion() {
    try {
        return await obtenerSesion();
    } catch (error) {
        if (error.codigo === 401) {
            location.replace("login.html");
            return null;
        }
        throw error;
    }
}

// GET /api/empresa/cursos -> lista de cursos de la empresa
export function listarCursos() {
    return pedir("/api/empresa/cursos");
}

// GET /api/empresa/cursos/{id} -> el curso con sus actividades en curso
export function obtenerCurso(id) {
    return pedir(`/api/empresa/cursos/${encodeURIComponent(id)}`);
}