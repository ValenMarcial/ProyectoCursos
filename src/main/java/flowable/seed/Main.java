package flowable.seed;

import org.flowable.idm.api.IdmIdentityService;
import org.flowable.idm.engine.IdmEngine;
import org.flowable.idm.engine.IdmEngineConfiguration;

public class Main {

    public static void main(String[] args) {
        System.out.println("Configurando el motor de Flowable ...");

        // 1. Crear la configuración para conectarse al motor de identidad de Flowable.
        IdmEngineConfiguration config =
                IdmEngineConfiguration.createStandaloneIdmEngineConfiguration();

        // 2. Indicar la conexión y las credenciales de la base Institucion.
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/Institucion");
        config.setJdbcDriver("org.postgresql.Driver");
        config.setJdbcUsername("UserPostgres");
        config.setJdbcPassword("Password");

        config.setDatabaseSchemaUpdate(IdmEngineConfiguration.DB_SCHEMA_UPDATE_FALSE);

        // 3. Construir el motor IDM, que proporciona acceso a usuarios, grupos y privilegios.
        IdmEngine idmEngine = config.buildIdmEngine();
        try {
            // 4. Obtener el servicio IDM e inyectarlo en la lógica de la seed.
            IdmIdentityService idmIdentityService = idmEngine.getIdmIdentityService();
            FlowableSeed seed = new FlowableSeed(idmIdentityService);

            // 5. Crear los usuarios, grupos, membresías y privilegios definidos en FlowableSeed.
            seed.seed();
        } finally {
            // 6. Cerrar el motor.
            idmEngine.close();
        }
    }
}
