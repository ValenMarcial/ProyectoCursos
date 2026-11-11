package flowable.course;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Set;

import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Execution listener que escribe un estado intermedio en cursos.estado, para que la empresa vea
 * en qué etapa está su curso (escenario 10). El estado llega como campo "estado" del listener en
 * el BPMN. Los estados finales los escribe RecordProcessResult.
 *
 * Usa la conexión del motor (la base es la misma), así el cambio se confirma o se deshace junto
 * con el paso del proceso.
 */
public class UpdateCourseState implements ExecutionListener {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(UpdateCourseState.class);

    static final Set<String> ESTADOS = Set.of(
            "confirmado", "pendiente_de_inicio", "en_desarrollo", "en_elaboracion_informe");

    private Expression estado;

    public void setEstado(Expression estado) {
        this.estado = estado;
    }

    @Override
    public void notify(DelegateExecution execution) {
        Object valor = estado == null ? null : estado.getValue(execution);
        if (!ESTADOS.contains(valor)) {
            throw new FlowableException("Estado intermedio de curso desconocido: " + valor);
        }
        Object cursoId = execution.getVariable("cursoId");
        if (!(cursoId instanceof Number)) {
            LOG.warn("La instancia {} no tiene cursoId; no se actualiza el estado a {}",
                    execution.getProcessInstanceId(), valor);
            return;
        }

        Connection connection = CommandContextUtil.getDbSqlSession().getSqlSession().getConnection();
        try (PreparedStatement update = connection.prepareStatement("UPDATE cursos SET estado = ? WHERE id = ?")) {
            update.setString(1, (String) valor);
            update.setLong(2, ((Number) cursoId).longValue());
            update.executeUpdate();
        } catch (SQLException exception) {
            throw new FlowableException("No se pudo actualizar el estado del curso " + cursoId + " a " + valor,
                    exception);
        }
    }
}
