/*
 * Formulario público de solicitud de capacitación.
 *
 * Toma los datos del formulario, los envía a POST /api/solicitudes y muestra
 * el resultado.
 */
"use strict";

// Dirección del backend. Si el sitio lo sirve el propio backend (puerto 8081)
// alcanza con una ruta relativa. Si se abre desde otro servidor (por ejemplo,
// uno estático en el puerto 3000) hay que usar la dirección completa.
// Solo funciona para prueba local, (localhost)
const BASE_URL = window.location.port === "8081" ? "" : "http://localhost:8081";
const URL_SOLICITUDES = BASE_URL + "/api/solicitudes";

const form = document.getElementById("request-form");
const sendButton = document.getElementById("send-button");
const notification = document.getElementById("notification");

/** Muestra un mensaje. El tipo puede ser "exito", "error" o "" (neutro). */
function showNotification(texto, type) {
  notification.textContent = texto;
  notification.className = type ? "notification notification-" + type : "notification";
  notification.hidden = false;
  notification.scrollIntoView({ behavior: "smooth", block: "nearest" });
}

/** Toma los datos del formulario y arma el objeto que se le enviara al backend */
function readData() {
  const data = Object.fromEntries(new FormData(form));

  for (const field of Object.keys(data)) {
    data[field] = data[field].trim();
  }

  // Conversion de participantes a tipo numerico
  data.participantesEstimados = Number(data.participantesEstimados);

  // Telefono Opcional
  if (data.contactoTelefono === "") {
    delete data.contactoTelefono;
  }

  return data;
}

/** Hace el POST y devuelve el código HTTP y el cuerpo (o null si no es JSON). */
async function send(data) {
  const response = await fetch(URL_SOLICITUDES, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data),
  });
  const body = await response.json().catch(() => null);
  return { code: response.status, body };
}

/** De acuerdo al codigo de respuesta se muestra un mensaje en especifico */
function showResult(code, body) {
  const key = body && body.businessKey ? body.businessKey : null;

  if (code === 201) {
    form.hidden = true;
    showNotification(
      "Recibimos su solicitud." +
        (key ? " Su número de seguimiento es " + key + "." : ""),
      "success"
    );
  } else if (code === 502 && body && body.estado === "PENDIENTE") {
    // La solicitud ya quedó guardada: no hay que pedir que la reenvíe.
    form.hidden = true;
    showNotification(
      "Recibimos su solicitud, pero tuvimos un inconveniente al procesarla.\n" +
        "No necesita enviarla de nuevo: la revisaremos." +
        (key ? "\nNúmero de seguimiento: " + key + "." : ""),
      ""
    );
  } else if (code === 400) {
    showNotification("Revise los datos ingresados e intente nuevamente.", "error");
  } else if (code === 502 || code === 503) {
    showNotification(
      "No pudimos registrar su solicitud en este momento. Intente nuevamente más tarde.",
      "error"
    );
  } else {
    showNotification("Ocurrió un error inesperado (código " + code + ").", "error");
  }
}

//** Listener para detectar cuando se presiona el boton de envio del formulario  */
form.addEventListener("submit", async (event) => {
  // No recargar la vista al enviar
  event.preventDefault();

  // Evita envíos duplicados
  sendButton.disabled = true;
  notification.hidden = true;

  try {
    const { code, body } = await send(readData());
    showResult(code, body);
  } catch (error) {
    // fetch solo falla si no logra comunicarse con el servidor.
    showNotification(
      "No pudimos comunicarnos con el servidor. Revise su conexión e intente nuevamente.",
      "error"
    );
  } finally {
    sendButton.disabled = false;
  }
});