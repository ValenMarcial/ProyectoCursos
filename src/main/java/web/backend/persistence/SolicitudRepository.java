package web.backend.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import web.backend.api.dto.IniciarSolicitudRequest;

@Repository
public class SolicitudRepository {
    private static final String INSERTAR_EMPRESA_SI_NO_EXISTE =
            "INSERT INTO empresas (nombre) VALUES (:nombre) ON CONFLICT DO NOTHING";
    private static final String BUSCAR_EMPRESA_POR_NOMBRE =
            "SELECT id FROM empresas WHERE lower(trim(nombre)) = lower(trim(:nombre))";
    private static final String VINCULAR_INSTANCIA =
            "UPDATE solicitudes SET instancia_flowable_id = :processInstanceId, "
                    + "integracion_flowable_estado = 'iniciada' "
                    + "WHERE id = :solicitudId AND instancia_flowable_id IS NULL";
    private static final String ELIMINAR_SOLICITUD =
            "DELETE FROM solicitudes WHERE id = :solicitudId AND instancia_flowable_id IS NULL";

    private final NamedParameterJdbcTemplate jdbc;
    private final SimpleJdbcInsert insertarSolicitud;

    public SolicitudRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.insertarSolicitud = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("solicitudes")
                .usingGeneratedKeyColumns("id")
                .usingColumns(
                        "empresa_id",
                        "business_key",
                        "tematica",
                        "descripcion",
                        "contacto_nombre",
                        "contacto_email",
                        "contacto_telefono",
                        "participantes_estimados",
                        "fechas_preferidas",
                        "integracion_flowable_estado");
    }

    @Transactional
    public SolicitudRegistrada registrar(String businessKey, IniciarSolicitudRequest request) {
        Long empresaId = resolverEmpresa(request.getEmpresaNombre());
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("empresa_id", empresaId)
                .addValue("business_key", businessKey)
                .addValue("tematica", request.getTematica().trim())
                .addValue("descripcion", request.getDescripcion().trim())
                .addValue("contacto_nombre", request.getContactoNombre().trim())
                .addValue("contacto_email", request.getContactoEmail().trim())
                .addValue("contacto_telefono", normalizarOpcional(request.getContactoTelefono()))
                .addValue("participantes_estimados", request.getParticipantesEstimados())
                .addValue("fechas_preferidas", request.getFechasPreferidas().trim())
                .addValue("integracion_flowable_estado", "pendiente");
        Number solicitudId = insertarSolicitud.executeAndReturnKey(params);
        return new SolicitudRegistrada(solicitudId.longValue(), empresaId);
    }

    @Transactional
    public void vincularInstancia(Long solicitudId, String processInstanceId) {
        int updatedRows = jdbc.update(VINCULAR_INSTANCIA,
                new MapSqlParameterSource()
                        .addValue("processInstanceId", processInstanceId)
                        .addValue("solicitudId", solicitudId));
        if (updatedRows != 1) {
            throw new IllegalStateException("No se pudo vincular la instancia Flowable a la solicitud.");
        }
    }

    @Transactional
    public void eliminarSolicitud(Long solicitudId) {
        jdbc.update(ELIMINAR_SOLICITUD,
                new MapSqlParameterSource("solicitudId", solicitudId));
    }

    private Long resolverEmpresa(String nombre) {
        String nombreNormalizado = nombre.trim();
        jdbc.update(INSERTAR_EMPRESA_SI_NO_EXISTE,
                new MapSqlParameterSource("nombre", nombreNormalizado));
        return jdbc.queryForObject(
                BUSCAR_EMPRESA_POR_NOMBRE,
                new MapSqlParameterSource("nombre", nombreNormalizado),
                Long.class);
    }

    private String normalizarOpcional(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    public static final class SolicitudRegistrada {
        private final Long solicitudId;
        private final Long empresaId;

        public SolicitudRegistrada(Long solicitudId, Long empresaId) {
            this.solicitudId = solicitudId;
            this.empresaId = empresaId;
        }

        public Long getSolicitudId() {
            return solicitudId;
        }

        public Long getEmpresaId() {
            return empresaId;
        }
    }
}
