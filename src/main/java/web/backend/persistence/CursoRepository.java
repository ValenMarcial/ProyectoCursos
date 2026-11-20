package web.backend.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import web.backend.api.dto.CursoFinalizadoResponse;

/** Cursos finalizados de todas las empresas, para que Administración consulte sus informes. */
@Repository
public class CursoRepository {
    private static final String COLUMNAS = "SELECT c.id, c.nombre, e.nombre AS empresa, c.fecha_inicio, c.fecha_fin, "
            + "c.instancia_flowable_id, c.resultados FROM cursos c JOIN empresas e ON e.id = c.empresa_id "
            + "WHERE c.estado = '" + EmpresaRepository.ESTADO_FINALIZADO + "' ";
    private static final String FINALIZADOS = COLUMNAS + "ORDER BY c.fecha_fin DESC, c.id DESC";
    private static final String FINALIZADO = COLUMNAS + "AND c.id = ?";

    private final JdbcTemplate jdbc;

    public CursoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CursoFinalizadoResponse> finalizados() {
        return jdbc.query(FINALIZADOS, (rs, fila) -> curso(rs).curso);
    }

    /** El curso, solo si está finalizado. */
    public Optional<CursoConInforme> finalizado(long cursoId) {
        return jdbc.query(FINALIZADO, (rs, fila) -> curso(rs), cursoId).stream().findFirst();
    }

    private static CursoConInforme curso(ResultSet rs) throws SQLException {
        String rutaInforme = rs.getString("resultados");
        return new CursoConInforme(new CursoFinalizadoResponse(rs.getLong("id"), rs.getString("nombre"),
                rs.getString("empresa"), rs.getDate("fecha_inicio").toLocalDate(),
                rs.getDate("fecha_fin").toLocalDate(), rutaInforme != null),
                rs.getString("instancia_flowable_id"), rutaInforme);
    }

    /** El curso con el id de su instancia y la ruta del informe, que no se exponen en la respuesta. */
    public static final class CursoConInforme {
        public final CursoFinalizadoResponse curso;
        public final String instanciaId;
        public final String rutaInforme;

        public CursoConInforme(CursoFinalizadoResponse curso, String instanciaId, String rutaInforme) {
            this.curso = curso;
            this.instanciaId = instanciaId;
            this.rutaInforme = rutaInforme;
        }
    }
}
