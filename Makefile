SHELL := /bin/bash
COMPOSE := docker compose
DEV_COMPOSE := $(COMPOSE) --profile dev

FLOWABLE_REST := http://127.0.0.1:8080/flowable-ui/process-api
FLOWABLE_USER ?= admin
FLOWABLE_PASSWORD ?= test

.PHONY: build backend-test dev-up dev-down dev-reset dev-logs dev-psql verify deploy

# Dejar en target/flowable-lib el .jar y las librerías de Drools que Flowable UI no trae
build:
	rm -rf target/flowable-lib
	mvn -q package dependency:copy-dependencies -DskipTests \
		-DincludeGroupIds=org.drools,org.kie,org.kie.soup,org.mvel,org.antlr,com.thoughtworks.xstream,io.github.x-stream,xmlpull \
		-DoutputDirectory=target/flowable-lib
	cp target/ProyectoCursos-*.jar target/flowable-lib/

backend-test:
	mvn -Pbackend clean test

dev-up: build
	$(DEV_COMPOSE) up -d --build --wait --wait-timeout 120

# Desplegar BPMN y reglas en el motor (cada despliegue crea una versión nueva).
# En un .zip, la API REST recibe un solo archivo por despliegue y el .drl va junto al BPMN.
deploy:
	rm -f target/deploy.zip
	zip -q -j target/deploy.zip src/main/resources/processes/ProyectoCursos.bpmn20.xml src/main/resources/rules/viability.drl
	curl -sS --fail-with-body -u $(FLOWABLE_USER):$(FLOWABLE_PASSWORD) \
		-F "file=@target/deploy.zip" \
		$(FLOWABLE_REST)/repository/deployments

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
