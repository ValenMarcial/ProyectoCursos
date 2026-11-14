package flowable.course;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;

import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.api.FlowableIllegalArgumentException;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;

/**
 * Impide registrar la finalización del dictado antes de la fecha de fin acordada en la propuesta.
 * La fecha se compara por día y en UTC, igual que las fechas guardadas en cursos.fecha_fin.
 */
public class ValidateCourseEndDate implements TaskListener {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT);

    @Override
    public void notify(DelegateTask task) {
        Object valor = task.getVariable("fechaFinPropuesta");
        if (valor == null) {
            throw new FlowableException("La fecha de fin del curso es obligatoria.");
        }
        if (!(valor instanceof Date)) {
            throw new FlowableException("La fecha de fin del curso no es válida.");
        }

        LocalDate fechaFin = ((Date) valor).toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate hoy = LocalDate.now(ZoneOffset.UTC);
        if (hoy.isBefore(fechaFin)) {
            throw new FlowableIllegalArgumentException(
                    "El curso no puede finalizarse antes del "
                            + fechaFin.format(FORMATO_FECHA) + " (fecha de fin programada).");
        }
    }
}
