SHELL := /bin/bash
COMPOSE := docker compose
DEV_COMPOSE := $(COMPOSE) --profile dev

FLOWABLE_REST := http://127.0.0.1:8080/flowable-ui/process-api
FLOWABLE_USER ?= admin
FLOWABLE_PASSWORD ?= test

.PHONY: build backend-test db-migrate dev-up dev-down dev-reset dev-logs dev-psql verify deploy seed import

# Dejar en flowable-lib/ el .jar y las librerías de Drools que Flowable UI no trae.
# Está fuera de target/ y se vacía sin borrar la carpeta: es la que monta el contenedor de Flowable,
# y si se borra (rm -rf o mvn clean) Flowable sigue apuntando a la carpeta vieja y arranca sin el .jar.
build:
	mkdir -p flowable-lib
	find flowable-lib -mindepth 1 -delete
	mvn -q package dependency:copy-dependencies -DskipTests \
		-DincludeGroupIds=org.drools,org.kie,org.kie.soup,org.mvel,org.antlr,com.thoughtworks.xstream,io.github.x-stream,xmlpull \
		-DoutputDirectory=flowable-lib
	cp target/ProyectoCursos-*.jar flowable-lib/

backend-test:
	mvn -Pbackend clean test

db-migrate:
	bash infra/db/migrate.sh

dev-up: build
	$(DEV_COMPOSE) up -d --build --wait --wait-timeout 120
	# Flowable carga el .jar solo al arrancar: se reinicia para que tome el que acaba de compilar build.
	$(DEV_COMPOSE) restart flowable
	$(DEV_COMPOSE) up -d --wait --wait-timeout 180 flowable

# Desplegar BPMN y reglas en el motor (cada despliegue crea una versión nueva).
# En un .zip, la API REST recibe un solo archivo por despliegue y el .drl va junto al BPMN.
deploy:
	rm -f target/deploy.zip
	zip -q -j target/deploy.zip src/main/resources/processes/ProyectoCursos.bpmn20.xml src/main/resources/rules/viability.drl
	curl -sS --fail-with-body -u $(FLOWABLE_USER):$(FLOWABLE_PASSWORD) \
		-F "file=@target/deploy.zip" \
		$(FLOWABLE_REST)/repository/deployments

# Crear usuarios y grupos, corregir las flechas de compensación del BPMN e importarlo al Modeler.
# Pide usuario y contraseña de Flowable para la importación.
seed:
	mvn -q compile exec:java -Dexec.mainClass=flowable.seed.Main

# Solo corregir el BPMN e importarlo al Modeler, sin tocar usuarios ni grupos.
import:
	mvn -q compile exec:java -Dexec.mainClass=flowable.seed.Main -Dexec.args="--no-seed"

dev-down:
	$(DEV_COMPOSE) down

dev-reset:
	$(DEV_COMPOSE) down -v
	$(MAKE) dev-up

dev-logs:
	$(DEV_COMPOSE) logs -f --tail=100

dev-psql:
	$(DEV_COMPOSE) exec postgres sh -c 'psql -U "$$POSTGRES_USER" -d "$$POSTGRES_DB"'

verify:
	./infra/scripts/verify-dev.sh --runtime
