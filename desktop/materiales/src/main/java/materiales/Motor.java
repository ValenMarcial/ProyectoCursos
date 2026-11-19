package materiales;

import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;

/** Arma el motor de Flowable sobre la base existente. */
public final class Motor implements AutoCloseable {
    private final ProcessEngine procesos;

    public Motor(ProcessEngine procesos) {
        this.procesos = procesos;
    }

    /** Se conecta a la base del motor sin tocar tablas y con el ejecutor asíncrono apagado. */
    public static Motor conectar(ConfigMotor config) {
        return new Motor(ProcessEngineConfiguration.createStandaloneProcessEngineConfiguration()
                .setJdbcUrl(config.url)
                .setJdbcUsername(config.usuario)
                .setJdbcPassword(config.clave)
                .setJdbcDriver("org.postgresql.Driver")
                .setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_FALSE)
                .setAsyncExecutorActivate(false)
                .buildProcessEngine());
    }

    public ProcessEngine procesos() {
        return procesos;
    }

    @Override
    public void close() {
        procesos.close();
    }
}
