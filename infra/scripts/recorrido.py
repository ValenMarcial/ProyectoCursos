#!/usr/bin/env python3
"""Recorre el proceso de cursos de punta a punta contra el entorno local (backend + Flowable).

Hace lo que todavía no tiene pantalla: completar tareas con datos, mandar los mensajes de la
empresa y señalar la nómina. Sirve para probar a mano, para la demo y para generar ejecuciones
con cierres distintos para la minería (4.4).

Uso:
    python3 infra/scripts/recorrido.py ESCENARIO [--recursos fisica,virtual,equipamiento]

Escenarios:
    final           acepta, prepara el curso, lo desarrolla y cierra con el informe
    cancelar        acepta y cancela durante "Preparar curso" (libera lo conseguido)
    rechazo         la empresa rechaza la propuesta y se archiva la solicitud
    sin-respuesta   la empresa no responde: dos recordatorios y se archiva (~1 minuto)
    limite          completa hasta 5 cursos en ejecución y muestra que la siguiente solicitud no es viable
    limpiar-limite  cancela los cursos que dejó «limite» (los de "Empresa límite N")

Variables de entorno:
    FLOWABLE_USER / FLOWABLE_PASSWORD   cuenta con acceso a la API REST (por defecto admin / test)
    BACKEND_URL                         por defecto http://localhost:8081
    FLOWABLE_REST                       por defecto http://127.0.0.1:8080/flowable-ui/process-api

Solo usa la biblioteca estándar de Python 3.
"""

import argparse
import base64
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from datetime import datetime, timedelta, timezone

FLOWABLE = os.environ.get("FLOWABLE_REST", "http://127.0.0.1:8080/flowable-ui/process-api")
BACKEND = os.environ.get("BACKEND_URL", "http://localhost:8081")
CREDENCIALES = base64.b64encode("{}:{}".format(
    os.environ.get("FLOWABLE_USER", "admin"), os.environ.get("FLOWABLE_PASSWORD", "test")).encode()).decode()

# Ids de las actividades del BPMN (docs/contrato.md)
REGISTRAR_PROPUESTA = "sid-F96A6968-13D6-4EA4-929E-69230127C208"
ARCHIVAR_SOLICITUD = "sid-DA4178CD-28E4-4E74-A085-B6D7BE829D62"
PREPARAR_MATERIALES = "sid-809C7C67-70EC-4018-BBF7-6A27DD12D4BB"
RECIBIR_NOMINA = "sid-40B80C57-5A63-4D5C-A43B-710C8B140370"
LISTA_DEFINITIVA = "sid-67285B9F-672D-4038-A765-E67245B387D1"
EVALUAR_RECURSOS = "sid-F3381B5B-C506-4AC2-A09D-747447EFB31B"
AULA_VIRTUAL = "sid-C3399A6A-261E-4D23-9AF8-F9F58D4A81CB"
EQUIPAMIENTO = "sid-6AAC5E2F-BF84-4C7C-8963-7D1F6CBEF790"
DEFINIR_DOCENTE = "sid-A3B3F53F-1D5E-42AA-AAC0-C69120F53E85"
DESARROLLAR_CURSO = "sid-378C089F-415C-4EC6-B426-E2D989460B8C"
ELABORAR_INFORME = "sid-8EE040F3-0293-488C-AE3F-3BD79DB56897"
ENVIAR_INFORME = "sid-BA739FA3-D40A-4AFD-AD69-062B808ABF63"

PROCESO = "process-proyecto-curso"
LIMITE_CURSOS = 5          # mismo umbral que rules/viability.drl
EMPRESA_LIMITE = "Empresa límite"

ESPERA_TAREA = 30          # segundos para que aparezca una tarea que sigue en el mismo pedido
ESPERA_TIMER = 150         # segundos para los timers (el ejecutor de Flowable revisa cada ~10 s)


class Trabado(Exception):
    """El proceso no llegó adonde se esperaba."""


def pedir(metodo, url, cuerpo=None, flowable=True):
    datos = json.dumps(cuerpo).encode() if cuerpo is not None else None
    pedido = urllib.request.Request(url, data=datos, method=metodo)
    pedido.add_header("Content-Type", "application/json")
    if flowable:
        pedido.add_header("Authorization", "Basic " + CREDENCIALES)
    try:
        with urllib.request.urlopen(pedido) as respuesta:
            texto = respuesta.read().decode()
            return json.loads(texto) if texto else None
    except urllib.error.HTTPError as error:
        detalle = error.read().decode()
        raise SystemExit("{} {} -> HTTP {}: {}".format(metodo, url, error.code, detalle))
    except urllib.error.URLError as error:
        raise SystemExit("No se pudo conectar a {}: {}. ¿Corriste make dev-up?".format(url, error.reason))


def api(metodo, ruta, cuerpo=None):
    return pedir(metodo, FLOWABLE + ruta, cuerpo)


class Fecha(str):
    """Fecha ISO que viaja a Flowable con tipo date (los listeners la leen como java.util.Date)."""


def variables(valores):
    tipos = {bool: "boolean", int: "long", str: "string", Fecha: "date"}
    return [{"name": nombre, "type": tipos[type(valor)], "value": valor} for nombre, valor in valores.items()]


def fecha(dias):
    return Fecha((datetime.now(timezone.utc) + timedelta(days=dias)).strftime("%Y-%m-%dT00:00:00.000Z"))


def paso(texto):
    print("  - " + texto)


def actividades_actuales(instancia):
    datos = api("GET", "/runtime/executions?size=100&processInstanceId=" + instancia)["data"]
    return sorted({e["activityId"] for e in datos if e["activityId"]})


def activa(instancia):
    return api("GET", "/runtime/process-instances?id=" + instancia)["total"] > 0


def esperar(condicion, segundos, que, instancia):
    limite = time.time() + segundos
    while time.time() < limite:
        valor = condicion()
        if valor:
            return valor
        time.sleep(2)
    actual = actividades_actuales(instancia) if activa(instancia) else "la instancia ya terminó"
    raise Trabado("No llegó a {} en {} s. Actividades actuales: {}".format(que, segundos, actual))


def tarea(instancia, clave, segundos=ESPERA_TAREA):
    def buscar():
        datos = api("GET", "/runtime/tasks?processInstanceId={}&taskDefinitionKey={}".format(instancia, clave))["data"]
        return datos[0] if datos else None
    return esperar(buscar, segundos, "la tarea " + clave, instancia)


def completar(instancia, clave, valores=None, segundos=ESPERA_TAREA):
    t = tarea(instancia, clave, segundos)
    cuerpo = {"action": "complete"}
    if valores:
        cuerpo["variables"] = variables(valores)
    api("POST", "/runtime/tasks/" + t["id"], cuerpo)
    paso("completada «{}»".format(t["name"]))


def ejecucion(instancia, mensaje=None, actividad=None):
    filtro = "&messageEventSubscriptionName=" + mensaje if mensaje else "&activityId=" + actividad
    def buscar():
        datos = api("GET", "/runtime/executions?processInstanceId=" + instancia + filtro)["data"]
        return datos[0]["id"] if datos else None
    return esperar(buscar, ESPERA_TAREA, mensaje or actividad, instancia)


def mensaje(instancia, nombre, valores=None):
    cuerpo = {"action": "messageEventReceived", "messageName": nombre}
    if valores:
        cuerpo["variables"] = variables(valores)
    api("PUT", "/runtime/executions/" + ejecucion(instancia, mensaje=nombre), cuerpo)
    paso("mensaje " + nombre)


def iniciar_solicitud(plazo=None, empresa=None):
    sello = datetime.now().strftime("%H%M%S")
    solicitud = {
        "empresaNombre": empresa or "Empresa recorrido " + sello,
        "contactoNombre": "Ana Pérez",
        "contactoEmail": "ana@example.com",
        "tematica": "BPMN " + sello,
        "descripcion": "Solicitud generada por infra/scripts/recorrido.py",
        "participantesEstimados": 12,
        "fechasPreferidas": "Por las tardes",
    }
    if plazo:
        solicitud["plazoRecordatorio"] = plazo
    respuesta = pedir("POST", BACKEND + "/api/solicitudes", solicitud, flowable=False)
    paso("solicitud iniciada ({}), instancia {}".format(respuesta["businessKey"], respuesta["processInstanceId"]))
    return respuesta["processInstanceId"]


def registrar_propuesta(instancia):
    try:
        tarea(instancia, REGISTRAR_PROPUESTA, 10)
    except Trabado:
        if not activa(instancia):
            raise Trabado("La solicitud no fue viable (ya hay 5 cursos en ejecución): terminó en Notificar Decisión.")
        raise
    completar(instancia, REGISTRAR_PROPUESTA, {
        "programa": "Modelado de procesos con BPMN",
        "presupuesto": 150000,
        "fechaInicioPosible": fecha(0),
        "fechaFinPropuesta": fecha(30),
    })


def aceptar(instancia, segundos_hasta_inicio):
    inicio = (datetime.now(timezone.utc) + timedelta(seconds=segundos_hasta_inicio)).strftime("%Y-%m-%dT%H:%M:%S")
    mensaje(instancia, "msgAceptacion", {"fechaInicio": inicio})
    paso("fecha de inicio acordada: {} UTC".format(inicio))


def preparar(instancia, recursos, completar_recursos=True):
    completar(instancia, PREPARAR_MATERIALES)
    api("PUT", "/runtime/executions/" + ejecucion(instancia, actividad=RECIBIR_NOMINA),
        {"action": "trigger", "variables": variables({"participantes": "Ana Pérez, Juan Gómez, Sofía Díaz"})})
    paso("nómina recibida")
    completar(instancia, LISTA_DEFINITIVA, {"listaDefinitiva": "Ana Pérez, Juan Gómez, Sofía Díaz"})
    completar(instancia, EVALUAR_RECURSOS, {
        "aulaFisica": "fisica" in recursos,
        "aulaVirtual": "virtual" in recursos,
        "equipamiento": "equipamiento" in recursos,
    })
    if "fisica" in recursos:
        aula = api("GET", "/runtime/process-instances/{}/variables/aulaAsignada".format(instancia))
        paso("aula reservada: {}".format(aula["value"]))
    if completar_recursos:
        if "virtual" in recursos:
            completar(instancia, AULA_VIRTUAL)
        if "equipamiento" in recursos:
            completar(instancia, EQUIPAMIENTO)


def escenario_final(instancia, recursos):
    registrar_propuesta(instancia)
    aceptar(instancia, 60)
    preparar(instancia, recursos)
    completar(instancia, DEFINIR_DOCENTE, {"docenteSeleccionado": "docente1"})
    paso("esperando la fecha de inicio (timer «Fecha programada»)...")
    completar(instancia, DESARROLLAR_CURSO, segundos=ESPERA_TIMER)
    completar(instancia, ELABORAR_INFORME)
    completar(instancia, ENVIAR_INFORME)


def escenario_cancelar(instancia, recursos):
    registrar_propuesta(instancia)
    aceptar(instancia, 3600)
    # Se cancela con los recursos ya conseguidos, para ver la compensación.
    completar(instancia, EVALUAR_RECURSOS, {
        "aulaFisica": "fisica" in recursos,
        "aulaVirtual": "virtual" in recursos,
        "equipamiento": "equipamiento" in recursos,
    })
    if "virtual" in recursos:
        completar(instancia, AULA_VIRTUAL)
    if "equipamiento" in recursos:
        completar(instancia, EQUIPAMIENTO)
    mensaje(instancia, "msgCancelacion")


def escenario_rechazo(instancia, _recursos):
    registrar_propuesta(instancia)
    mensaje(instancia, "msgRechazo")
    completar(instancia, ARCHIVAR_SOLICITUD)


def escenario_sin_respuesta(instancia, _recursos):
    registrar_propuesta(instancia)
    paso("esperando dos recordatorios y el cierre por falta de respuesta...")
    completar(instancia, ARCHIVAR_SOLICITUD, segundos=ESPERA_TIMER)


def cursos_en_ejecucion():
    """Instancias activas con cursoId: las que cuenta EvaluateViability (ya pasaron por "Habilitar acceso")."""
    return api("POST", "/query/process-instances?size=100", {
        "processDefinitionKey": PROCESO,
        "variables": [{"name": "cursoId", "operation": "greaterThan", "value": 0, "type": "long"}],
    })["data"]


def variable(instancia, nombre):
    valores = api("GET", "/runtime/process-instances/{}/variables".format(instancia))
    return next((v["value"] for v in valores if v["name"] == nombre), None)


def completar_cupos():
    """Lleva solicitudes nuevas hasta "Habilitar acceso" hasta que haya LIMITE_CURSOS cursos en ejecución.
    Se aceptan con inicio dentro de 30 días, así quedan esperando en "Preparar curso"."""
    actuales = len(cursos_en_ejecucion())
    print("Cursos en ejecución: {}".format(actuales))
    for numero in range(actuales + 1, LIMITE_CURSOS + 1):
        print("Curso {} de {}".format(numero, LIMITE_CURSOS))
        instancia = iniciar_solicitud(empresa="{} {}".format(EMPRESA_LIMITE, numero))
        registrar_propuesta(instancia)
        aceptar(instancia, 30 * 24 * 3600)
        curso = esperar(lambda: variable(instancia, "cursoId"), ESPERA_TAREA, "Habilitar acceso", instancia)
        paso("curso {} creado".format(curso))
    print("Cursos en ejecución: {}. Ahora una solicitud más:".format(len(cursos_en_ejecucion())))


def escenario_limite(instancia, _recursos):
    # Evaluar Viabilidad corre apenas inicia la solicitud: con 5 cursos va directo a "Notificar Decisión".
    paso("esperando la decisión de viabilidad...")


def limpiar_limite():
    canceladas = 0
    for instancia in cursos_en_ejecucion():
        if (variable(instancia["id"], "empresaNombre") or "").startswith(EMPRESA_LIMITE):
            mensaje(instancia["id"], "msgCancelacion")
            canceladas += 1
    print("Cursos de «limite» cancelados: {}. Cursos en ejecución: {}".format(
        canceladas, len(cursos_en_ejecucion())))


ESCENARIOS = {
    "final": (escenario_final, None),
    "cancelar": (escenario_cancelar, None),
    "rechazo": (escenario_rechazo, None),
    "sin-respuesta": (escenario_sin_respuesta, "PT20S"),
    "limite": (escenario_limite, None),
    "limpiar-limite": (None, None),
}


def resumen(instancia):
    historial = api("GET", "/history/historic-activity-instances?size=500&processInstanceId=" + instancia)["data"]
    # Un evento del gateway que no se disparó también queda terminado: solo se muestran las actividades
    # de las que salió un flujo recorrido (o los fines, que no tienen salida).
    definicion = api("GET", "/history/historic-process-instances/" + instancia)["processDefinitionId"]
    bpmn = urllib.request.Request(FLOWABLE + "/repository/process-definitions/{}/resourcedata".format(definicion))
    bpmn.add_header("Authorization", "Basic " + CREDENCIALES)
    with urllib.request.urlopen(bpmn) as respuesta:
        modelo = ET.fromstring(respuesta.read())
    origen = {f.get("id"): f.get("sourceRef") for f in modelo.iter("{http://www.omg.org/spec/BPMN/20100524/MODEL}sequenceFlow")}
    recorridas = {origen.get(a["activityId"]) for a in historial if a["activityType"] == "sequenceFlow"}
    historial = sorted((a for a in historial
                        if a.get("endTime") and a["activityType"] != "sequenceFlow"
                        and (a["activityId"] in recorridas or a["activityType"] == "endEvent")),
                       key=lambda a: (a["endTime"], a["startTime"]))
    nombres = []
    for actividad in historial:
        nombre = (actividad.get("activityName") or "").replace("\n", " ").strip()
        if nombre and (not nombres or nombres[-1] != nombre):
            nombres.append(nombre)
    print("\nRecorrido: " + " → ".join(nombres))
    resultado = api("GET", "/history/historic-variable-instances?processInstanceId={}&variableName=resultado"
                    .format(instancia))["data"]
    print("resultado = {}".format(resultado[0]["variable"]["value"] if resultado else "(sin definir)"))
    print("Instancia {}: {}".format(instancia, "sigue activa en " + str(actividades_actuales(instancia))
                                    if activa(instancia) else "terminada"))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("escenario", choices=sorted(ESCENARIOS))
    parser.add_argument("--recursos", default="fisica,virtual,equipamiento",
                        help="recursos que se eligen en Evaluar Recursos (por defecto los tres)")
    argumentos = parser.parse_args()
    recursos = {r.strip() for r in argumentos.recursos.split(",") if r.strip()}
    if not recursos or not recursos <= {"fisica", "virtual", "equipamiento"}:
        parser.error("--recursos acepta fisica, virtual y equipamiento separados por coma")

    ejecutar, plazo = ESCENARIOS[argumentos.escenario]
    print("Escenario «{}»".format(argumentos.escenario))
    try:
        if argumentos.escenario == "limpiar-limite":
            limpiar_limite()
            return
        if argumentos.escenario == "limite":
            completar_cupos()
    except Trabado as problema:
        sys.exit("\nATENCIÓN: " + str(problema))
    instancia = iniciar_solicitud(plazo)
    try:
        ejecutar(instancia, recursos)
        esperar(lambda: not activa(instancia), ESPERA_TAREA, "el fin del proceso", instancia)
    except Trabado as problema:
        print("\nATENCIÓN: " + str(problema))
        resumen(instancia)
        sys.exit(1)
    resumen(instancia)


if __name__ == "__main__":
    main()
