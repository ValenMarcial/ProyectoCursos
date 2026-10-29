SHELL := /bin/bash
COMPOSE := docker compose
DEV_COMPOSE := $(COMPOSE) --profile dev

.PHONY: dev-up dev-down dev-reset dev-logs dev-psql verify

dev-up:
	$(DEV_COMPOSE) up -d --wait --wait-timeout 120

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
