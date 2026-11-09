package flowable.result;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Set;

import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.impl.cfg.TransactionState;
import org.flowable.common.engine.impl.context.Context;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Execution Listener de fin de la instancia: registra el resultado del proceso en la base
 * de la institución.
 */
public class RecordProcessResult implements ExecutionListener {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(RecordProcessResult.class);

    static final Set<String> RESULTADOS = Set.of(
            "finalizado", "cancelado", "rechazado", "cerrado_por_falta_de_respuesta", "no_viable");

    @Override
    public void notify(DelegateExecution execution) {
        Object resultado = execution.getVariable("resultado");
        if (resultado == null) {
            // camino de cierre sin resultado, se avisa sin trabar el fin de la instancia
            LOG.warn("La instancia {} terminó sin la variable resultado; no se actualiza el estado",
                    execution.getProcessInstanceId());
            return;
        }
        if (!RESULTADOS.contains(resultado)) {
            throw new FlowableException("Resultado de proceso desconocido: " + resultado);
        }

        String processInstanceId = execution.getProcessInstanceId();
        Long solicitudId = asLong(execution.getVariable("solicitudId"));
        Long cursoId = asLong(execution.getVariable("cursoId"));
        Connection connection = CommandContextUtil.getDbSqlSession().getSqlSession().getConnection();

        Context.getTransactionContext().addTransactionListener(TransactionState.COMMITTING, commandContext -> {
            try {
                if (solicitudId != null) {
                    update(connection, "UPDATE solicitudes SET resultado = ? WHERE id = ?", (String) resultado, solicitudId);
                }
                if (cursoId != null) {
                    update(connection, "UPDATE cursos SET estado = ? WHERE id = ?", (String) resultado, cursoId);
                }
            } catch (SQLException exception) {
                throw new FlowableException("No se pudo registrar el resultado de la instancia " + processInstanceId,
                        exception);
            }
        });
    }

    private static void update(Connection connection, String sql, String resultado, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, resultado);
            statement.setLong(2, id);
            statement.executeUpdate();
        }
    }

    private static Long asLong(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : null;
    }
}
