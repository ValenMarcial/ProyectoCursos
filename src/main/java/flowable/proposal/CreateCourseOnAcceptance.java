package flowable.proposal;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.function.Supplier;

import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.api.FlowableIllegalArgumentException;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import shared.PasswordHash;

/**
 * Actividad "Habilitar acceso de la empresa": crea el curso de la propuesta aceptada, guarda su ID
 * en la solicitud y crea la cuenta de la empresa si todavía no tiene una. Todo va en una sola
 * transacción y es idempotente ante reintentos.
 */
public class CreateCourseOnAcceptance implements JavaDelegate {
    private static final Logger LOG = LoggerFactory.getLogger(CreateCourseOnAcceptance.class);
    private static final int LARGO_CLAVE = 12;
    private static final String SELECT_SOLICITUD =
            "SELECT empresa_id, tematica, descripcion, curso_id FROM solicitudes WHERE id = ? FOR UPDATE";
    private static final String SELECT_CURSO_POR_INSTANCIA =
            "SELECT id FROM cursos WHERE instancia_flowable_id = ?";
    private static final String INSERT_CURSO =
            "INSERT INTO cursos (empresa_id, nombre, descripcion, programa, presupuesto, fecha_inicio, fecha_fin, "
                    + "instancia_flowable_id, estado) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'en_preparacion')";
    private static final String UPDATE_SOLICITUD = "UPDATE solicitudes SET curso_id = ? WHERE id = ?";
    private static final String SELECT_CONTACTO =
            "SELECT s.empresa_id, s.contacto_email, c.usuario FROM solicitudes s "
                    + "LEFT JOIN cuentas_empresa c ON c.empresa_id = s.empresa_id WHERE s.id = ?";
    private static final String SELECT_USUARIO = "SELECT 1 FROM cuentas_empresa WHERE usuario = ?";
    private static final String INSERT_CUENTA = "INSERT INTO cuentas_empresa (empresa_id, usuario, clave_hash) "
            + "VALUES (?, ?, ?) ON CONFLICT DO NOTHING";

    private final Supplier<Connection> connections;

    public CreateCourseOnAcceptance() {
        this(CreateCourseOnAcceptance::connectFromEnvironment);
    }

    CreateCourseOnAcceptance(Supplier<Connection> connections) {
        this.connections = connections;
    }

    @Override
    public void execute(DelegateExecution execution) {
        Long solicitudId = asLong(execution.getVariable("solicitudId"));
        Long budget = asLong(execution.getVariable("presupuesto"));
        Date start = asSqlDate(execution.getVariable("fechaInicio"));
        java.util.Date end = (java.util.Date) execution.getVariable("fechaFinPropuesta");
        String program = (String) execution.getVariable("programa");
        if (solicitudId == null || budget == null || start == null || end == null || program == null) {
            throw new FlowableException("Faltan datos de la solicitud o de la propuesta para crear el curso.");
        }
        if (start.toLocalDate().isAfter(new Date(end.getTime()).toLocalDate())) {
            throw new FlowableIllegalArgumentException("La fecha de inicio acordada (" + start
                    + ") no puede ser posterior a la fecha de finalización propuesta (" + new Date(end.getTime()) + ").");
        }

        try (Connection connection = connections.get()) {
            connection.setAutoCommit(false);
            try {
                long cursoId = crearYVincular(connection, execution.getProcessInstanceId(), solicitudId,
                        budget, start, end, program);
                CuentaNueva cuenta = crearCuentaSiFalta(connection, solicitudId);
                connection.commit();
                execution.setVariable("cursoId", cursoId);
                if (cuenta != null) {
                    // Simulación del envío: la demo no manda emails, las credenciales quedan en el log de Flowable.
                    LOG.info("Cuenta de empresa creada (envío simulado a {}): empresa {}, usuario {}, clave {}",
                            cuenta.destino, cuenta.empresaId, cuenta.usuario, cuenta.clave);
                }
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new FlowableException("No se pudo crear el curso de la solicitud " + solicitudId + ".", exception);
        }
    }

    private long crearYVincular(Connection connection, String processInstanceId, long solicitudId, long budget,
            Date start, java.util.Date end, String program) throws SQLException {
        long empresaId;
        String tematica;
        String descripcion;
        try (PreparedStatement select = connection.prepareStatement(SELECT_SOLICITUD)) {
            select.setLong(1, solicitudId);
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    throw new FlowableException("No existe la solicitud " + solicitudId + ".");
                }
                long existente = rs.getLong("curso_id");
                if (!rs.wasNull()) {
                    return existente;
                }
                empresaId = rs.getLong("empresa_id");
                tematica = rs.getString("tematica");
                descripcion = rs.getString("descripcion");
            }
        }

        Long cursoId = buscarPorInstancia(connection, processInstanceId);
        if (cursoId == null) {
            try (PreparedStatement insert = connection.prepareStatement(INSERT_CURSO,
                    new String[] {"id"})) {
                insert.setLong(1, empresaId);
                insert.setString(2, tematica);
                insert.setString(3, descripcion);
                insert.setString(4, program);
                insert.setInt(5, Math.toIntExact(budget));
                insert.setDate(6, start);
                insert.setDate(7, new Date(end.getTime()));
                insert.setString(8, processInstanceId);
                insert.executeUpdate();
                try (ResultSet rs = insert.getGeneratedKeys()) {
                    rs.next();
                    cursoId = rs.getLong(1);
                }
            }
        }

        try (PreparedStatement update = connection.prepareStatement(UPDATE_SOLICITUD)) {
            update.setLong(1, cursoId);
            update.setLong(2, solicitudId);
            update.executeUpdate();
        }
        return cursoId;
    }

    /**
     * Crea la cuenta de la empresa de la solicitud si no tiene una y devuelve las credenciales nuevas;
     * devuelve null si ya existía. El usuario es el email de contacto; si falta o ya lo usa otra
     * empresa, es "empresa-ID".
     */
    private CuentaNueva crearCuentaSiFalta(Connection connection, long solicitudId) throws SQLException {
        long empresaId;
        String email;
        try (PreparedStatement select = connection.prepareStatement(SELECT_CONTACTO)) {
            select.setLong(1, solicitudId);
            try (ResultSet rs = select.executeQuery()) {
                rs.next();
                if (rs.getString("usuario") != null) {
                    return null;
                }
                empresaId = rs.getLong("empresa_id");
                email = rs.getString("contacto_email");
            }
        }

        String usuario = email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
        if (usuario == null || usuarioOcupado(connection, usuario)) {
            usuario = "empresa-" + empresaId;
        }
        String clave = PasswordHash.generarClave(LARGO_CLAVE);
        try (PreparedStatement insert = connection.prepareStatement(INSERT_CUENTA)) {
            insert.setLong(1, empresaId);
            insert.setString(2, usuario);
            insert.setString(3, PasswordHash.hash(clave));
            // 0 filas: otra instancia de la misma empresa la creó al mismo tiempo; esa entrega las credenciales.
            if (insert.executeUpdate() == 0) {
                return null;
            }
        }
        return new CuentaNueva(empresaId, usuario, clave, email);
    }

    private static boolean usuarioOcupado(Connection connection, String usuario) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(SELECT_USUARIO)) {
            select.setString(1, usuario);
            try (ResultSet rs = select.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static final class CuentaNueva {
        final long empresaId;
        final String usuario;
        final String clave;
        final String destino;

        CuentaNueva(long empresaId, String usuario, String clave, String destino) {
            this.empresaId = empresaId;
            this.usuario = usuario;
            this.clave = clave;
            this.destino = destino == null ? "la empresa" : destino;
        }
    }

    private Long buscarPorInstancia(Connection connection, String processInstanceId) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(SELECT_CURSO_POR_INSTANCIA)) {
            select.setString(1, processInstanceId);
            try (ResultSet rs = select.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    private static Long asLong(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : null;
    }

    private static Date asSqlDate(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String)) {
            throw new FlowableException("fechaInicio debe ser un texto con formato ISO-8601.");
        }
        String dateValue = ((String) value).trim();
        try {
            LocalDate date;
            try {
                date = LocalDate.from(DateTimeFormatter.ISO_DATE_TIME.parse(dateValue));
            } catch (DateTimeParseException exception) {
                date = LocalDate.parse(dateValue, DateTimeFormatter.ISO_DATE);
            }
            return Date.valueOf(date);
        } catch (DateTimeParseException exception) {
            throw new FlowableException("fechaInicio debe tener un formato de fecha ISO-8601 válido.", exception);
        }
    }

    private static Connection connectFromEnvironment() {
        String url = System.getenv("SPRING_DATASOURCE_URL");
        if (url == null || url.isBlank()) {
            throw new FlowableException("SPRING_DATASOURCE_URL no está configurada para el motor de Flowable.");
        }
        try {
            return DriverManager.getConnection(url, System.getenv("SPRING_DATASOURCE_USERNAME"),
                    System.getenv("SPRING_DATASOURCE_PASSWORD"));
        } catch (SQLException exception) {
            throw new FlowableException("No se pudo conectar a la base institucional.", exception);
        }
    }
}
