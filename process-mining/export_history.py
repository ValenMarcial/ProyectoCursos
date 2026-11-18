"""Exporta el historial de actividades de Flowable a un CSV (log de eventos).

  - PROCESS_KEY y ACTIVIDADES (elementos de la etapa de negociación) son los del BPMN real.
  - No se filtra por instancias terminadas: también se exportan las que siguen en curso. La
    columna "instancia_terminada" permite distinguirlas después.
  - Solo se exportan las actividades POR LAS QUE PASÓ el caso. Flowable deja con endTime a todos los
    eventos de una compuerta basada en eventos ("Esperar respuesta": 5 días, Aceptación, Rechazo,
    Modificaciones), aunque solo uno haya ocurrido. Se conservan las actividades de las que salió un
    flujo realmente recorrido (misma idea que resumen() de infra/scripts/recorrido.py).
"""
import csv
import xml.etree.ElementTree as ET
from datetime import datetime
from getpass import getpass

import requests


# Configuración
BASE_URL = "http://localhost:8080/flowable-ui/process-api"

# ID del elemento <process id="..."> del BPMN.
PROCESS_KEY = "process-proyecto-curso"

# IDs BPMN de las actividades y eventos de la etapa de negociación (previa a "Preparar curso").
# Las compuertas y el evento de inicio no se incluyen: no aportan información propia.
ACTIVIDADES = {
    "sid-C279BA20-BEB4-4850-B878-FAC01445ABE0",  # Evaluar Viabilidad
    "sid-D4A460A6-6451-48B2-BD5D-F76FDEC3887F",  # Notificar Decisión (solicitud no viable)
    "sid-F96A6968-13D6-4EA4-929E-69230127C208",  # Registrar y Elaborar propuesta
    "sid-E8F551D6-A49F-44CF-82E5-69F2EF55DBB3",  # Enviar propuesta
    "sid-AE90424C-2654-4C64-909C-BD085A34654C",  # 5 días (evento de tiempo)
    "sid-BCDCCC94-87B1-4A9C-AA42-860B0CB20553",  # Aceptación (mensaje)
    "sid-2EDEE778-CCB8-4D86-B10B-F0CA6DE47860",  # Rechazo (mensaje)
    "sid-B1226B89-72C5-427D-85EF-783C6E99CC5D",  # Modificaciones (mensaje)
    "sid-BE500ABC-83C7-4A8A-9C83-23183950A2F6",  # Revisar propuesta
    "sid-1F2A59D8-79CE-4E8B-88FF-9FDF0B2CF6EB",  # Enviar recordatorio
    "sid-DA4178CD-28E4-4E74-A085-B6D7BE829D62",  # Archivar solicitud
    "sid-78883D13-C7C2-49D2-BE16-8D4D46141AD3",  # Habilitar acceso de la empresa
}

ARCHIVO_SALIDA = "eventos.csv"

NS_BPMN = "{http://www.omg.org/spec/BPMN/20100524/MODEL}"


def consultar_todos(session, recurso, filtros):
    """Recupera todas las páginas de una consulta REST."""
    inicio = 0

    while True:
        respuesta = session.get(
            f"{BASE_URL}/{recurso}",
            params={
                **filtros,
                "start": inicio,
                "size": 100,
            },
            timeout=30,
        )
        respuesta.raise_for_status()
        pagina = respuesta.json()
        registros = pagina["data"]

        if not registros:
            break

        yield from registros
        inicio += len(registros)

        if inicio >= pagina["total"]:
            break


def origenes_de_flujos(session, definicion_id, cache):
    """Devuelve {id del flujo: id del elemento de origen} leyendo el BPMN desplegado.

    El historial registra cada flujo recorrido solo con el id del flujo; el elemento del que
    sale está únicamente en el XML del diagrama."""
    if definicion_id not in cache:
        respuesta = session.get(
            f"{BASE_URL}/repository/process-definitions/{definicion_id}/resourcedata",
            timeout=30,
        )
        respuesta.raise_for_status()
        modelo = ET.fromstring(respuesta.content)
        cache[definicion_id] = {
            flujo.get("id"): flujo.get("sourceRef")
            for flujo in modelo.iter(NS_BPMN + "sequenceFlow")
        }
    return cache[definicion_id]


def fecha_iso(valor):
    return datetime.fromisoformat(valor.replace("Z", "+00:00"))


def main():
    usuario = input("Usuario de Flowable: ")
    clave = getpass("Contraseña: ")

    eventos = []
    cache_flujos = {}

    with requests.Session() as session:
        session.auth = (usuario, clave)

        instancias = consultar_todos(
            session,
            "history/historic-process-instances",
            {"processDefinitionKey": PROCESS_KEY},
        )

        for instancia in instancias:
            # Excluir instancias eliminadas administrativamente.
            if instancia.get("deleteReason"):
                continue

            historial = list(
                consultar_todos(
                    session,
                    "history/historic-activity-instances",
                    {"processInstanceId": instancia["id"]},
                )
            )

            # Flujos recorridos, identificados por (ejecución, elemento de origen). Incluir la
            # ejecución evita un error en los lazos: si "5 días" disparó en una ronda y en otra
            # no, el elemento sería el mismo pero la ejecución es distinta.
            origen = origenes_de_flujos(
                session, instancia["processDefinitionId"], cache_flujos
            )
            recorridos = {
                (f.get("executionId"), origen.get(f["activityId"]))
                for f in historial
                if f["activityType"] == "sequenceFlow"
            }

            for actividad in historial:
                # No exportar elementos sin fecha de finalización.
                if not actividad.get("endTime"):
                    continue

                if actividad["activityId"] not in ACTIVIDADES:
                    continue

                # Descartar los eventos de una compuerta que no se dispararon.
                if (
                    actividad.get("executionId"),
                    actividad["activityId"],
                ) not in recorridos:
                    continue

                eventos.append({
                    "instancia": instancia["id"],
                    "instancia_terminada": (
                        "true" if instancia.get("endTime") else "false"
                    ),
                    "definicion": actividad["processDefinitionId"],
                    "evento_id": actividad["id"],
                    "actividad_id": actividad["activityId"],
                    "actividad": (
                        actividad.get("activityName")
                        or actividad["activityId"]
                    ),
                    "tipo": actividad["activityType"],
                    "inicio": actividad["startTime"],
                    "fecha_hora": actividad["endTime"],
                    "recurso": actividad.get("assignee") or "",
                })

    # Ordenar por instancia y fecha de finalización.
    # El ID da un orden estable si coinciden las marcas temporales.
    eventos.sort(
        key=lambda evento: (
            evento["instancia"],
            fecha_iso(evento["fecha_hora"]),
            fecha_iso(evento["inicio"]),
            evento["evento_id"],
        )
    )

    columnas = [
        "instancia", "instancia_terminada", "definicion", "evento_id",
        "actividad_id", "actividad", "tipo",
        "inicio", "fecha_hora", "recurso",
    ]

    with open(
        ARCHIVO_SALIDA, "w", newline="", encoding="utf-8"
    ) as archivo:
        escritor = csv.DictWriter(archivo, fieldnames=columnas)
        escritor.writeheader()
        escritor.writerows(eventos)

    cantidad_instancias = len({
        evento["instancia"] for evento in eventos
    })

    print(
        f"Generado {ARCHIVO_SALIDA}: "
        f"{len(eventos)} eventos de {cantidad_instancias} instancias."
    )


if __name__ == "__main__":
    main()