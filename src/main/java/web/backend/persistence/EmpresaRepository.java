package web.backend.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import web.backend.api.dto.CursoEmpresaResponse;

/** Cuentas y cursos de las empresas en la base institucional. Toda consulta de cursos filtra por empresa. */
@Repository
public class EmpresaRepository {
    private static final String BUSCAR_CUENTA = "SELECT c.empresa_id, c.usuario, c.clave_hash, e.nombre "
            + "FROM cuentas_empresa c JOIN empresas e ON e.id = c.empresa_id WHERE c.usuario = ?";
    private static final String COLUMNAS_CURSO =
            "SELECT id, nombre, descripcion, estado, fecha_inicio, fecha_fin, instancia_flowable_id FROM cursos ";
    private static final String CURSOS = COLUMNAS_CURSO + "WHERE empresa_id = ? ORDER BY fecha_inicio DESC, id DESC";
    private static final String CURSO = COLUMNAS_CURSO + "WHERE empresa_id = ? AND id = ?";

    private final JdbcTemplate jdbc;

    public EmpresaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Cuenta> buscarCuenta(String usuario) {
        return jdbc.query(BUSCAR_CUENTA, (rs, fila) -> new Cuenta(rs.getLong("empresa_id"), rs.getString("usuario"),
                rs.getString("clave_hash"), rs.getString("nombre")), usuario).stream().findFirst();
    }

    public List<CursoEmpresaResponse> cursos(long empresaId) {
        return jdbc.query(CURSOS, (rs, fila) -> curso(rs).curso, empresaId);
    }

    /** El curso solo si es de la empresa: el de otra empresa no se distingue de uno inexistente. */
    public Optional<CursoConInstancia> curso(long empresaId, long cursoId) {
        return jdbc.query(CURSO, (rs, fila) -> curso(rs), empresaId, cursoId).stream().findFirst();
    }

    private static CursoConInstancia curso(ResultSet rs) throws SQLException {
        return new CursoConInstancia(new CursoEmpresaResponse(rs.getLong("id"), rs.getString("nombre"),
                rs.getString("descripcion"), rs.getString("estado"), rs.getDate("fecha_inicio").toLocalDate(),
                rs.getDate("fecha_fin").toLocalDate(), null), rs.getString("instancia_flowable_id"));
    }

    public static final class Cuenta {
        public final long empresaId;
        public final String usuario;
        public final String claveHash;
        public final String empresa;

        public Cuenta(long empresaId, String usuario, String claveHash, String empresa) {
            this.empresaId = empresaId;
            this.usuario = usuario;
            this.claveHash = claveHash;
            this.empresa = empresa;
        }
    }

    /** El curso con el id de su instancia, que no se expone a la empresa. */
    public static final class CursoConInstancia {
        public final CursoEmpresaResponse curso;
        public final String instanciaId;

        public CursoConInstancia(CursoEmpresaResponse curso, String instanciaId) {
            this.curso = curso;
            this.instanciaId = instanciaId;
        }
    }
}
