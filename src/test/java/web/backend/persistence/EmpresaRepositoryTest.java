package web.backend.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import web.backend.persistence.EmpresaRepository.Cuenta;
import web.backend.persistence.EmpresaRepository.CursoConInstancia;

class EmpresaRepositoryTest {
    private EmpresaRepository repository;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:empresa-repo-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=1000", "sa", ""));
        jdbc.execute("CREATE TABLE empresas (id BIGINT PRIMARY KEY, nombre VARCHAR(255) NOT NULL)");
        jdbc.execute("CREATE TABLE cuentas_empresa (empresa_id BIGINT PRIMARY KEY, usuario VARCHAR(320) UNIQUE, "
                + "clave_hash VARCHAR(200))");
        jdbc.execute("CREATE TABLE cursos (id BIGINT PRIMARY KEY, empresa_id BIGINT, nombre VARCHAR(255), "
                + "descripcion VARCHAR(2000), estado VARCHAR(40), fecha_inicio DATE, fecha_fin DATE, "
                + "instancia_flowable_id VARCHAR(64), resultados VARCHAR(255))");
        jdbc.execute("INSERT INTO empresas VALUES (1, 'Tech S.A.'), (2, 'Otra S.R.L.')");
        jdbc.execute("INSERT INTO cuentas_empresa VALUES (1, 'rrhh@tech.com', 'hash')");
        jdbc.execute("INSERT INTO cursos VALUES "
                + "(10, 1, 'BPMN', 'Modelado', 'en_elaboracion_informe', DATE '2027-01-15', DATE '2027-02-15', "
                + "'proc-10', 'cursos/proc-10/a.pdf'), "
                + "(11, 1, 'Cloud', 'AWS', 'finalizado', DATE '2026-03-01', DATE '2026-04-01', 'proc-11', "
                + "'cursos/proc-11/b.pdf'), "
                + "(12, 1, 'Viejo', 'Sin PDF', 'finalizado', DATE '2025-03-01', DATE '2025-04-01', 'proc-12', NULL), "
                + "(20, 2, 'IA', 'LLM', 'en_preparacion', DATE '2027-05-01', DATE '2027-06-01', 'proc-20', NULL)");
        repository = new EmpresaRepository(jdbc);
    }

    @Test
    void buscaLaCuentaConElNombreDeLaEmpresa() {
        Cuenta cuenta = repository.buscarCuenta("rrhh@tech.com").orElseThrow();

        assertEquals(1L, cuenta.empresaId);
        assertEquals("hash", cuenta.claveHash);
        assertEquals("Tech S.A.", cuenta.empresa);
        assertTrue(repository.buscarCuenta("nadie@tech.com").isEmpty());
    }

    @Test
    void listaSoloLosCursosDeLaEmpresaDelMasNuevoAlMasViejo() {
        List<Long> ids = repository.cursos(1L).stream().map(c -> c.id).collect(Collectors.toList());

        assertEquals(List.of(10L, 11L, 12L), ids);
    }

    @Test
    void elCursoDeOtraEmpresaNoSeEncuentra() {
        CursoConInstancia propio = repository.curso(1L, 10L).orElseThrow();

        assertEquals("proc-10", propio.instanciaId);
        assertEquals("en_elaboracion_informe", propio.curso.estado);
        assertEquals(LocalDate.of(2027, 1, 15), propio.curso.fechaInicio);
        assertTrue(repository.curso(1L, 20L).isEmpty());
    }

    @Test
    void elInformeEstaDisponibleSoloEnCursosFinalizadosConPdf() {
        CursoConInstancia finalizado = repository.curso(1L, 11L).orElseThrow();

        assertEquals("cursos/proc-11/b.pdf", finalizado.rutaInforme);
        assertTrue(finalizado.curso.informeDisponible);
        assertFalse(repository.curso(1L, 10L).orElseThrow().curso.informeDisponible);
        assertFalse(repository.curso(1L, 12L).orElseThrow().curso.informeDisponible);
    }
}
