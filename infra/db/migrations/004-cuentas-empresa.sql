BEGIN;

-- Cuenta de acceso de cada empresa (escenario 10). La crea "Habilitar acceso de la empresa" la primera
-- vez que se acepta una propuesta de esa empresa; las siguientes la reutilizan. La clave nunca se guarda
-- en texto plano: clave_hash tiene el formato de shared.PasswordHash (PBKDF2).
CREATE TABLE IF NOT EXISTS cuentas_empresa (
  empresa_id BIGINT PRIMARY KEY REFERENCES empresas (id),
  usuario TEXT NOT NULL UNIQUE,
  clave_hash TEXT NOT NULL,
  creada_en timestamptz NOT NULL DEFAULT now()
);

INSERT INTO schema_version (version)
VALUES (4)
ON CONFLICT (version) DO NOTHING;

COMMIT;
