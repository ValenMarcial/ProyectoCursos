#!/usr/bin/env python3
"""Genera muchas instancias del proceso, variadas, para que 4.3 y 4.4 tengan datos con sentido.

A diferencia de recorrido.py (un camino por vez, todo como admin y sin pausas), cada corrida:

- Completa las tareas como las personas reales, por el backend y con su sesión: el que la
  tiene asignada o, si es de grupo, un administrativo al azar que primero la reclama. Así
  4.3.3 (tareas por persona) cuenta a cada uno.
- Hace pausas al azar entre pasos y prepara las tres ramas de "Preparar curso" en paralelo,
  así las duraciones de 4.3.2 son distintas entre ramas y entre cursos.
- Varía la negociación: cero, una o dos modificaciones (con "Revisar propuesta"), a veces deja
  pasar un recordatorio, y termina en aceptación, rechazo o falta de respuesta. Es lo que mira 4.4.
- Varía los recursos, el docente elegido y si el informe se escribe o se adjunta. El PDF del
  informe se sube de verdad, así "Enviar informe" lo puede mostrar.

Con varias corridas a la vez puede faltar aula física (hay dos): esa corrida termina cancelada
por SIN_AULAS, que también es un camino del modelo. Con --no-viables N, al final completa el
cupo de 5 cursos y manda N solicitudes que el motor rechaza por viabilidad.

Uso:
    python3 infra/scripts/corridas.py [--cantidad 40] [--paralelo 4] [--escala 1] [--semilla N] [--no-viables 2]

    --escala multiplica las pausas: 0 las saca (rápido, pero 4.3.2 queda con duraciones casi nulas).

Variables de entorno: las de recorrido.py (FLOWABLE_USER, FLOWABLE_PASSWORD, BACKEND_URL, FLOWABLE_REST).
Solo usa la biblioteca estándar de Python 3.
"""

import argparse
import http.cookiejar
import json
import os
import random
import sys
import threading
import time
import urllib.error
import urllib.request
import uuid
from collections import Counter
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timedelta, timezone

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import recorrido as r  # noqa: E402  (reutiliza la API de Flowable, los ids y los escenarios de límite)

REVISAR_PROPUESTA = "sid-BE500ABC-83C7-4A8A-9C83-23183950A2F6"

# Usuarios de make seed (flowable/seed/FlowableSeed.java).
CLAVES = {
    "jefeAdministrativo": "JefeA",
    "empleadoAdministrativo": "EmpleadoA",
    "jefeDeEquipo": "JefeD123",
    "docente1": "Docente1",
    "docente2": "Docente2",
}
ADMINISTRATIVOS = ["jefeAdministrativo", "empleadoAdministrativo"]
DOCENTES = ["jefeDeEquipo", "docente1", "docente2"]

EMPRESAS = [
    ("Metalúrgica Andina", "Laura Gómez", "lgomez@andina.example.com"),
    ("Logística del Sur", "Pablo Ruiz", "pruiz@logsur.example.com"),
    ("Agro Pampa", "Carla Medina", "cmedina@agropampa.example.com"),
    ("Clínica San Martín", "Diego Sosa", "dsosa@clinicasm.example.com"),
    ("Software Patagonia", "Julieta Ríos", "jrios@swpatagonia.example.com"),
    ("Banco Regional", "Martín Herrera", "mherrera@bancoregional.example.com"),
    ("Textil Norte", "Sofía Acosta", "sacosta@textilnorte.example.com"),
    ("Energía Cuyo", "Tomás Vera", "tvera@energiacuyo.example.com"),
]
TEMATICAS = [
    "Modelado de procesos con BPMN", "Excel avanzado", "Seguridad informática", "Liderazgo de equipos",
    "Atención al cliente", "Python para análisis de datos", "Gestión de proyectos ágiles", "Higiene y seguridad",
]

# Destino de la negociación y su peso en el sorteo.
DESTINOS = {"final": 45, "cancelar": 15, "rechazo": 25, "sin-respuesta": 15}

impresion = threading.Lock()
contexto = threading.local()


def paso(texto):
    with impresion:
        print("[{:>2}] {}".format(getattr(contexto, "numero", "--"), texto), flush=True)


r.paso = paso  # los mensajes de recorrido.py también llevan el número de corrida


class Terminada(Exception):
    """La instancia terminó antes de llegar a la tarea esperada (no viable, falta de aulas)."""


# --- Backend con la sesión de cada persona -------------------------------------------------

class Sesion:
    """Sesión del personal en el backend, con su cookie, como la de la web."""

    def __init__(self, usuario):
        self.usuario = usuario
        self.abridor = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.pedir("POST", "/api/sesion", json.dumps({"usuario": usuario, "clave": CLAVES[usuario]}).encode(),
                   "application/json")

    def pedir(self, metodo, ruta, datos=None, tipo=None):
        pedido = urllib.request.Request(r.BACKEND + ruta, data=datos, method=metodo)
        if tipo:
            pedido.add_header("Content-Type", tipo)
        try:
            with self.abridor.open(pedido) as respuesta:
                texto = respuesta.read().decode()
                return json.loads(texto) if texto else None
        except urllib.error.HTTPError as error:
            raise RuntimeError("{} {} como {} -> HTTP {}: {}".format(
                metodo, ruta, self.usuario, error.code, error.read().decode()[:300]))

    def reclamar(self, tarea):
        self.pedir("POST", "/api/tareas/{}/reclamar".format(tarea))

    def completar(self, tarea, valores=None):
        cuerpo = {"variables": r.variables(valores)} if valores else {}
        self.pedir("POST", "/api/tareas/{}/completar".format(tarea), json.dumps(cuerpo).encode(), "application/json")

    def subir_informe(self, tarea, pdf):
        limite = uuid.uuid4().hex
        cuerpo = (("--{0}\r\nContent-Disposition: form-data; name=\"archivo\"; filename=\"informe.pdf\"\r\n"
                   "Content-Type: application/pdf\r\n\r\n").format(limite).encode()
                  + pdf + "\r\n--{}--\r\n".format(limite).encode())
        return self.pedir("POST", "/api/tareas/{}/informe".format(tarea), cuerpo,
                          "multipart/form-data; boundary=" + limite)["rutaInforme"]


def sesion(usuario):
    """Una sesión por persona y por corrida (cada hilo tiene las suyas)."""
    sesiones = contexto.__dict__.setdefault("sesiones", {})
    if usuario not in sesiones:
        sesiones[usuario] = Sesion(usuario)
    return sesiones[usuario]


# --- Pasos -------------------------------------------------------------------------------------

def pausa(minimo, maximo):
    time.sleep(random.uniform(minimo, maximo) * contexto.escala)


def buscar_tarea(instancia, clave, segundos=r.ESPERA_TAREA):
    limite = time.time() + segundos
    while time.time() < limite:
        datos = r.api("GET", "/runtime/tasks?processInstanceId={}&taskDefinitionKey={}".format(instancia, clave))["data"]
        if datos:
            return datos[0]
        if not r.activa(instancia):
            raise Terminada()
        time.sleep(2)
    raise r.Trabado("No apareció la tarea {}. Actividades actuales: {}".format(clave, r.actividades_actuales(instancia)))


def hacer(instancia, clave, valores=None, quien=None, segundos=r.ESPERA_TAREA):
    """Completa la tarea como su responsable. Si es de grupo, la reclama antes un administrativo."""
    tarea = buscar_tarea(instancia, clave, segundos)
    usuario = tarea["assignee"]
    if not usuario:
        usuario = quien or random.choice(ADMINISTRATIVOS)
        sesion(usuario).reclamar(tarea["id"])
    sesion(usuario).completar(tarea["id"], valores)
    paso("«{}» por {}".format(tarea["name"], usuario))
    return tarea


def propuesta(final):
    # Para el fin normal la fecha de fin es hoy, así "Desarrollar curso" se puede completar.
    dias = 0 if final else random.randint(20, 60)
    return {
        "programa": "Programa de {} ({} encuentros)".format(contexto.tematica, random.randint(4, 12)),
        "presupuesto": random.randrange(80000, 400000, 5000),
        "fechaInicioPosible": r.fecha(0),
        "fechaFinPropuesta": r.fecha(dias),
    }


def informe_pdf(texto):
    """PDF mínimo de una página, válido para el backend (empieza con %PDF-)."""
    linea = texto.encode("latin-1", "replace").replace(b"\\", b"\\\\").replace(b"(", b"\\(").replace(b")", b"\\)")
    contenido = b"BT /F1 12 Tf 60 780 Td (" + linea + b") Tj ET"
    objetos = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R "
        b"/Resources << /Font << /F1 5 0 R >> >> >>",
        b"<< /Length " + str(len(contenido)).encode() + b" >>\nstream\n" + contenido + b"\nendstream",
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
    ]
    pdf = b"%PDF-1.4\n"
    posiciones = []
    for numero, objeto in enumerate(objetos, 1):
        posiciones.append(len(pdf))
        pdf += str(numero).encode() + b" 0 obj\n" + objeto + b"\nendobj\n"
    xref = len(pdf)
    pdf += b"xref\n0 " + str(len(objetos) + 1).encode() + b"\n0000000000 65535 f \n"
    pdf += b"".join("{:010d} 00000 n \n".format(p).encode() for p in posiciones)
    pdf += b"trailer\n<< /Size " + str(len(objetos) + 1).encode() + b" /Root 1 0 R >>\nstartxref\n"
    return pdf + str(xref).encode() + b"\n%%EOF\n"


def preparar(instancia, recursos):
    """Las tres ramas de "Preparar curso" a la vez, cada una con sus tiempos."""
    def materiales():
        pausa(5, 60)
        hacer(instancia, r.PREPARAR_MATERIALES)

    def inscripciones():
        pausa(5, 40)
        api_nomina = "/runtime/executions/" + r.ejecucion(instancia, actividad=r.RECIBIR_NOMINA)
        r.api("PUT", api_nomina, {"action": "trigger", "variables": r.variables({"participantes": participantes()})})
        paso("nómina recibida")
        pausa(5, 40)
        hacer(instancia, r.LISTA_DEFINITIVA, {"listaDefinitiva": participantes()})

    def recursos_():
        pausa(5, 30)
        hacer(instancia, r.EVALUAR_RECURSOS, {
            "aulaFisica": "fisica" in recursos, "aulaVirtual": "virtual" in recursos,
            "equipamiento": "equipamiento" in recursos})
        for clave, recurso in ((r.AULA_VIRTUAL, "virtual"), (r.EQUIPAMIENTO, "equipamiento")):
            if recurso in recursos:
                pausa(5, 30)
                hacer(instancia, clave)

    errores = []
    numero, escala = contexto.numero, contexto.escala

    def rama(funcion):
        # Cada rama es un hilo: necesita su propio contexto (número, escala y sesiones).
        contexto.numero, contexto.escala, contexto.sesiones = numero, escala, {}
        try:
            funcion()
        except Terminada:
            pass  # la instancia terminó (por ejemplo, sin aulas): las demás ramas se cancelan solas
        except (Exception, SystemExit) as error:
            errores.append(error)

    hilos = [threading.Thread(target=rama, args=(f,)) for f in (materiales, inscripciones, recursos_)]
    for hilo in hilos:
        hilo.start()
    for hilo in hilos:
        hilo.join()
    if errores:
        raise errores[0]
    if not r.activa(instancia):
        raise Terminada()


def participantes():
    nombres = ["Ana Pérez", "Juan Gómez", "Sofía Díaz", "Lucas Romero", "Valeria Paz", "Nicolás Luna",
               "Camila Torres", "Mateo Silva", "Lucía Fernández", "Bruno Castro"]
    return ", ".join(random.sample(nombres, random.randint(4, 10)))


def recursos_al_azar():
    while True:
        elegidos = {nombre for nombre, prob in (("fisica", 0.6), ("virtual", 0.5), ("equipamiento", 0.4))
                    if random.random() < prob}
        if elegidos:
            return elegidos


def corrida(numero, destino, modificaciones, recordatorio, escala):
    """Una instancia completa. Devuelve un dict con lo que pasó."""
    contexto.numero = numero
    contexto.escala = escala
    contexto.sesiones = {}
    empresa, contacto, email = random.choice(EMPRESAS)
    contexto.tematica = random.choice(TEMATICAS)
    plazo = "PT20S" if destino == "sin-respuesta" else ("PT30S" if recordatorio else None)
    datos = {
        "empresaNombre": empresa, "contactoNombre": contacto, "contactoEmail": email,
        "tematica": contexto.tematica, "descripcion": "Capacitación in company generada por corridas.py",
        "participantesEstimados": random.randint(5, 30), "fechasPreferidas": random.choice(
            ["Por las mañanas", "Por las tardes", "Martes y jueves", "Sábados"]),
    }
    if plazo:
        datos["plazoRecordatorio"] = plazo
    instancia = r.pedir("POST", r.BACKEND + "/api/solicitudes", datos, flowable=False)["processInstanceId"]
    paso("{} · {} · destino {}, {} modificación(es){}".format(
        empresa, contexto.tematica, destino, modificaciones, ", con recordatorio" if recordatorio else ""))
    final = destino == "final"
    try:
        pausa(2, 15)
        hacer(instancia, r.REGISTRAR_PROPUESTA, propuesta(final), segundos=15)
        for _ in range(modificaciones):
            pausa(3, 15)
            r.mensaje(instancia, "msgModificaciones", {"observaciones": random.choice([
                "Pedimos bajar el presupuesto.", "Necesitamos terminar antes.", "Agregar un encuentro práctico.",
                "Preferimos modalidad virtual."])})
            pausa(3, 15)
            hacer(instancia, REVISAR_PROPUESTA, propuesta(final))
        if recordatorio:
            paso("dejando pasar un recordatorio...")
            time.sleep(45)  # el primero sale a los 30 s; el cierre recién después del segundo
        else:
            pausa(3, 20)

        if destino == "rechazo":
            r.mensaje(instancia, "msgRechazo")
            pausa(2, 10)
            hacer(instancia, r.ARCHIVAR_SOLICITUD)
        elif destino == "sin-respuesta":
            paso("esperando los dos recordatorios...")
            hacer(instancia, r.ARCHIVAR_SOLICITUD, segundos=r.ESPERA_TIMER)
        else:
            recursos = recursos_al_azar()
            inicio = datetime.now(timezone.utc) + timedelta(seconds=60 if final else 3600)
            r.mensaje(instancia, "msgAceptacion", {"fechaInicio": inicio.strftime("%Y-%m-%dT%H:%M:%S")})
            if final:
                preparar(instancia, recursos)
                pausa(2, 15)
                hacer(instancia, r.DEFINIR_DOCENTE, {"docenteSeleccionado": random.choice(DOCENTES)})
                paso("esperando la fecha de inicio...")
                hacer(instancia, r.DESARROLLAR_CURSO, segundos=r.ESPERA_TIMER)
                pausa(3, 20)
                tarea = buscar_tarea(instancia, r.ELABORAR_INFORME)
                texto = "Informe de resultados: {} para {}. Asistencia y evaluación satisfactorias.".format(
                    contexto.tematica, empresa)
                ruta = sesion("jefeDeEquipo").subir_informe(tarea["id"], informe_pdf(texto))
                valores = {"rutaInforme": ruta}
                if random.random() < 0.3:
                    valores["informeAdjunto"] = True
                else:
                    valores["informeResultados"] = texto
                sesion("jefeDeEquipo").completar(tarea["id"], valores)
                paso("«{}» por jefeDeEquipo (PDF {})".format(tarea["name"],
                                                             "adjunto" if "informeAdjunto" in valores else "generado"))
                pausa(2, 15)
                hacer(instancia, r.ENVIAR_INFORME)
            else:
                # Cancela durante la preparación, con parte de los recursos conseguidos.
                pausa(3, 15)
                hacer(instancia, r.EVALUAR_RECURSOS, {
                    "aulaFisica": "fisica" in recursos, "aulaVirtual": "virtual" in recursos,
                    "equipamiento": "equipamiento" in recursos})
                for clave, recurso in ((r.AULA_VIRTUAL, "virtual"), (r.EQUIPAMIENTO, "equipamiento")):
                    if recurso in recursos and random.random() < 0.6:
                        pausa(3, 15)
                        hacer(instancia, clave)
                pausa(3, 15)
                r.mensaje(instancia, "msgCancelacion")
        r.esperar(lambda: not r.activa(instancia), r.ESPERA_TAREA, "el fin del proceso", instancia)
    except Terminada:
        pass  # el motor la cerró antes (no viable o falta de aulas): se informa con su resultado
    return {"numero": numero, "instancia": instancia, "destino": destino, "modificaciones": modificaciones,
            "resultado": historica(instancia, "resultado"), "motivo": historica(instancia, "motivoCierre")}


def historica(instancia, nombre):
    datos = r.api("GET", "/history/historic-variable-instances?processInstanceId={}&variableName={}"
                  .format(instancia, nombre))["data"]
    return datos[0]["variable"]["value"] if datos else None


def plan(cantidad):
    """Sorteo de destinos y negociación de cada corrida."""
    destinos = random.choices(list(DESTINOS), weights=list(DESTINOS.values()), k=cantidad)
    corridas = []
    for numero, destino in enumerate(destinos, 1):
        modificaciones = 0 if destino == "sin-respuesta" else random.choices([0, 1, 2], weights=[55, 30, 15])[0]
        recordatorio = destino != "sin-respuesta" and random.random() < 0.15
        corridas.append((numero, destino, modificaciones, recordatorio))
    return corridas


def no_viables(cantidad):
    """Completa el cupo de cursos en ejecución, manda solicitudes que no son viables y libera el cupo."""
    contexto.numero = "NV"
    r.completar_cupos()
    for _ in range(cantidad):
        instancia = r.iniciar_solicitud()
        r.esperar(lambda: not r.activa(instancia), r.ESPERA_TAREA, "Notificar Decisión", instancia)
        paso("resultado = {}".format(historica(instancia, "resultado")))
    r.limpiar_limite()


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--cantidad", type=int, default=40, help="cuántas instancias (por defecto 40)")
    parser.add_argument("--paralelo", type=int, default=4,
                        help="cuántas a la vez (por defecto 4; con 5 o más el motor empieza a rechazar por viabilidad)")
    parser.add_argument("--escala", type=float, default=1.0, help="multiplica las pausas (0 = sin pausas)")
    parser.add_argument("--semilla", type=int,
                        help="repite el mismo sorteo de destinos (las pausas y los usuarios siguen al azar)")
    parser.add_argument("--no-viables", type=int, default=0, help="solicitudes no viables al final (por defecto 0)")
    argumentos = parser.parse_args()
    if argumentos.cantidad < 0 or argumentos.paralelo < 1 or argumentos.escala < 0 or argumentos.no_viables < 0:
        parser.error("valores fuera de rango")
    random.seed(argumentos.semilla)
    corridas = plan(argumentos.cantidad)
    random.seed()  # el resto (pausas, usuarios, recursos) siempre al azar

    contexto.escala = argumentos.escala
    en_ejecucion = len(r.cursos_en_ejecucion())
    print("Cursos en ejecución antes de empezar: {}".format(en_ejecucion))
    if en_ejecucion + argumentos.paralelo >= r.LIMITE_CURSOS:
        print("Aviso: con {} en ejecución y {} a la vez, algunas solicitudes pueden salir no viables."
              .format(en_ejecucion, argumentos.paralelo))

    resultados, fallidas = [], []
    with ThreadPoolExecutor(max_workers=argumentos.paralelo) as pool:
        futuros = {}
        for datos in corridas:
            futuros[pool.submit(corrida, *datos, argumentos.escala)] = datos[0]
            time.sleep(random.uniform(0, 3))  # que no arranquen todas en el mismo segundo
        for futuro in as_completed(futuros):
            try:
                resultado = futuro.result()
                resultados.append(resultado)
                paso_global("corrida {} terminó: {}{}".format(
                    resultado["numero"], resultado["resultado"],
                    " ({})".format(resultado["motivo"]) if resultado["motivo"] else ""))
            except (Exception, SystemExit) as error:  # recorrido.pedir corta con SystemExit ante un HTTP de error
                fallidas.append(futuros[futuro])
                paso_global("corrida {} falló: {}".format(futuros[futuro], error))

    if argumentos.no_viables:
        try:
            no_viables(argumentos.no_viables)
        except (Exception, SystemExit) as error:
            print("No se pudieron generar las no viables: {}".format(error))

    print("\nResumen de {} corridas:".format(len(resultados)))
    for resultado, cantidad in Counter(x["resultado"] or "(sin resultado)" for x in resultados).most_common():
        print("  {:<32} {}".format(resultado, cantidad))
    sin_aulas = sum(1 for x in resultados if x["motivo"])
    if sin_aulas:
        print("  (de las canceladas, {} por falta de aulas)".format(sin_aulas))
    print("  con modificaciones: {}".format(sum(1 for x in resultados if x["modificaciones"])))
    if fallidas:
        print("Fallaron: {} (ver arriba)".format(", ".join(map(str, sorted(fallidas)))))
        sys.exit(1)


def paso_global(texto):
    with impresion:
        print("[==] " + texto, flush=True)


if __name__ == "__main__":
    main()
