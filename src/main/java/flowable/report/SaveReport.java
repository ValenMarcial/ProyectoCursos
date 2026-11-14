package flowable.report;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.regex.Pattern;

import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.task.service.delegate.DelegateTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Guarda en cursos.resultados la ruta relativa del PDF del informe. La ruta la arma la web al
 * subir el archivo (cursos/&lt;instancia&gt;/&lt;uuid&gt;.pdf); acá se valida que corresponda a esta
 * instancia, que no salga de su carpeta y que sea un PDF, y se persiste con la conexión del motor,
 * en la misma transacción de la tarea.
 */
public class SaveReport implements TaskListener {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(SaveReport.class);
    private static final int LARGO_MAXIMO_RUTA = 255;
    private static final Pattern RUTA_VALIDA = Pattern.compile("cursos/[^/\\\\]+/[^/\\\\]+\\.pdf");

    @Override
    public void notify(DelegateTask task) {
        Object valor = task.getVariable("rutaInforme");
        if (!(valor instanceof String) || ((String) valor).isBlank()) {
            throw new FlowableException("No se pudo determinar la ruta del PDF del informe.");
        }
        String ruta = ((String) valor).trim();
        String prefijo = "cursos/" + task.getProcessInstanceId() + "/";
        if (!ruta.startsWith(prefijo)) {
            throw new FlowableException("La ruta del PDF del informe no corresponde a este curso.");
        }
        if (!RUTA_VALIDA.matcher(ruta).matches()) {
            throw new FlowableException("La ruta del PDF del informe no es válida.");
        }
        if (ruta.length() > LARGO_MAXIMO_RUTA) {
            throw new FlowableException("La ruta del PDF del informe supera el largo permitido.");
        }

        Object cursoId = task.getVariable("cursoId");
        if (!(cursoId instanceof Number)) {
            LOG.warn("La instancia {} no tiene cursoId; no se guarda la ruta del informe",
                    task.getProcessInstanceId());
            return;
        }

        Connection connection = CommandContextUtil.getDbSqlSession().getSqlSession().getConnection();
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE cursos SET resultados = ? WHERE id = ?")) {
            update.setString(1, ruta);
            update.setLong(2, ((Number) cursoId).longValue());
            update.executeUpdate();
        } catch (SQLException exception) {
            throw new FlowableException("No se pudo guardar la ruta del informe del curso " + cursoId, exception);
        }
    }
}
