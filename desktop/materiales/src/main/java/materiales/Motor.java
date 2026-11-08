package materiales;

import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.idm.api.IdmIdentityService;
import org.flowable.idm.engine.IdmEngine;
import org.flowable.idm.engine.IdmEngineConfiguration;

/** Arma los motores de Flowable (procesos e identidades) sobre la base existente. */
public final class Motor implements AutoCloseable {
    private final ProcessEngine procesos;
    private final IdmEngine idm;

    public Motor(ProcessEngine procesos, IdmEngine idm) {
        this.procesos = procesos;
        this.idm = idm;
    }

    /**
     * Se conecta a la base del motor sin crear ni modificar tablas y con el ejecutor asíncrono apagado:
     * esta app solo consulta y completa tareas, los timers y trabajos los sigue ejecutando Flowable UI
     * (si los dos motores corrieran jobs sobre la misma base, se disputarían las tareas del proceso).
     */
    public static Motor conectar(ConfigMotor config) {
        ProcessEngine procesos = ProcessEngineConfiguration.createStandaloneProcessEngineConfiguration()
                .setJdbcUrl(config.url)
                .setJdbcUsername(config.usuario)
                .setJdbcPassword(config.clave)
                .setJdbcDriver("org.postgresql.Driver")
                .setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_FALSE)
                .setAsyncExecutorActivate(false)
                .buildProcessEngine();
        IdmEngineConfiguration idmConfig = IdmEngineConfiguration.createStandaloneIdmEngineConfiguration();
        idmConfig.setJdbcUrl(config.url);
        idmConfig.setJdbcUsername(config.usuario);
        idmConfig.setJdbcPassword(config.clave);
        idmConfig.setJdbcDriver("org.postgresql.Driver");
        idmConfig.setDatabaseSchemaUpdate(IdmEngineConfiguration.DB_SCHEMA_UPDATE_FALSE);
        return new Motor(procesos, idmConfig.buildIdmEngine());
    }

    public ProcessEngine procesos() {
        return procesos;
    }

    public IdmIdentityService identidades() {
        return idm.getIdmIdentityService();
    }

    @Override
    public void close() {
        procesos.close();
        idm.close();
    }
}
