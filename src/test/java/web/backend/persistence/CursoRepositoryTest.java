package web.backend.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import web.backend.api.dto.CursoFinalizadoResponse;
import web.backend.persistence.CursoRepository.CursoConInforme;

class CursoRepositoryTest {
    private CursoRepository repository;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:curso-repo-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=1000", "sa", ""));
        jdbc.execute("CREATE TABLE empresas (id BIGINT PRIMARY KEY, nombre VARCHAR(255) NOT NULL)");
        jdbc.execute("CREATE TABLE cursos (id BIGINT PRIMARY KEY, empresa_id BIGINT, nombre VARCHAR(255), "
                + "descripcion VARCHAR(2000), estado VARCHAR(40), fecha_inicio DATE, fecha_fin DATE, "
                + "instancia_flowable_id VARCHAR(64), resultados VARCHAR(255))");
        jdbc.execute("INSERT INTO empresas VALUES (1, 'Tech S.A.'), (2, 'Otra S.R.L.')");
        jdbc.execute("INSERT INTO cursos VALUES "
                + "(10, 1, 'BPMN', 'x', 'finalizado', DATE '2026-03-01', DATE '2026-04-01', 'proc-10', "
                + "'cursos/proc-10/a.pdf'), "
                + "(11, 2, 'Cloud', 'x', 'finalizado', DATE '2026-05-01', DATE '2026-06-01', 'proc-11', NULL), "
                + "(12, 1, 'IA', 'x', 'en_elaboracion_informe', DATE '2026-07-01', DATE '2026-08-01', 'proc-12', "
                + "'cursos/proc-12/c.pdf'), "
                + "(13, 2, 'Datos', 'x', 'cancelado', DATE '2026-09-01', DATE '2026-10-01', 'proc-13', NULL)");
        repository = new CursoRepository(jdbc);
    }

    @Test
    void listaSoloLosFinalizadosDeTodasLasEmpresasDelMasRecienteAlMasViejo() {
        List<CursoFinalizadoResponse> cursos = repository.finalizados();

        assertEquals(List.of(11L, 10L), cursos.stream().map(c -> c.id).collect(Collectors.toList()));
        assertEquals("Otra S.R.L.", cursos.get(0).empresa);
        assertFalse(cursos.get(0).informeDisponible);
        assertTrue(cursos.get(1).informeDisponible);
    }

    @Test
    void unCursoNoFinalizadoNoSeEncuentra() {
        CursoConInforme curso = repository.finalizado(10L).orElseThrow();

        assertEquals("proc-10", curso.instanciaId);
        assertEquals("cursos/proc-10/a.pdf", curso.rutaInforme);
        assertTrue(repository.finalizado(12L).isEmpty());
        assertTrue(repository.finalizado(13L).isEmpty());
    }
}
