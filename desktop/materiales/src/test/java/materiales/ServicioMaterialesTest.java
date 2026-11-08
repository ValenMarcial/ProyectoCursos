package materiales;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.idm.api.Group;
import org.flowable.idm.api.IdmIdentityService;
import org.flowable.idm.api.User;
import org.flowable.idm.engine.IdmEngine;
import org.flowable.idm.engine.IdmEngineConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import materiales.ServicioMateriales.Usuario;

/** Prueba contra un motor real (H2 en memoria) con un proceso mínimo: una tarea "Prepara materiales" para el grupo. */
class ServicioMaterialesTest {
    private static ProcessEngine procesos;
    private static IdmEngine idm;
    private static ServicioMateriales servicio;

    @BeforeAll
    static void arrancar() {
        procesos = ProcessEngineConfiguration.createStandaloneInMemProcessEngineConfiguration()
                .setJdbcUrl("jdbc:h2:mem:materiales;DB_CLOSE_DELAY=-1")
                .setAsyncExecutorActivate(false)
                .buildProcessEngine();
        IdmEngineConfiguration cfg = IdmEngineConfiguration.createStandaloneInMemIdmEngineConfiguration();
        cfg.setJdbcUrl("jdbc:h2:mem:materiales-idm;DB_CLOSE_DELAY=-1");
        cfg.setDatabaseSchemaUpdate(IdmEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        idm = cfg.buildIdmEngine();
        IdmIdentityService ids = idm.getIdmIdentityService();
        grupo(ids, "Administracion");
        grupo(ids, "Docentes");
        usuario(ids, "admin1", "clave1", "Administracion");
        usuario(ids, "admin2", "clave2", "Administracion");
        usuario(ids, "docente1", "clave3", "Docentes");
        procesos.getRepositoryService().createDeployment()
                .addClasspathResource("materiales-test.bpmn20.xml").deploy();
        servicio = new ServicioMateriales(procesos.getTaskService(), procesos.getRuntimeService(), ids);
    }

    @AfterAll
    static void cerrar() {
        procesos.close();
        idm.close();
    }

    @BeforeEach
    void limpiar() {
        procesos.getRuntimeService().createProcessInstanceQuery().list()
                .forEach(i -> procesos.getRuntimeService().deleteProcessInstance(i.getId(), "test"));
    }

    private static void grupo(IdmIdentityService ids, String id) {
        Group g = ids.newGroup(id);
        g.setName(id);
        ids.saveGroup(g);
    }

    private static void usuario(IdmIdentityService ids, String id, String clave, String grupo) {
        User u = ids.newUser(id);
        u.setPassword(clave);
        ids.saveUser(u);
        ids.createMembership(id, grupo);
    }

    private String iniciar(String empresa) {
        return procesos.getRuntimeService().startProcessInstanceByKey("materialesTest",
                Map.of("empresaNombre", empresa, "tematica", "Excel")).getId();
    }

    @Test
    void identificaConUsuarioYClaveDeFlowable() {
        Usuario u = servicio.identificar("admin1", "clave1");
        assertNotNull(u);
        assertTrue(u.esAdministrativo());
        assertNull(servicio.identificar("admin1", "mala"));
        assertNull(servicio.identificar("nadie", "x"));
        assertFalse(servicio.identificar("docente1", "clave3").esAdministrativo());
    }

    @Test
    void listaLasDisponiblesParaElGrupoConEmpresaYTematica() {
        iniciar("Acme");
        List<TareaMateriales> tareas = servicio.consultar(servicio.identificar("admin1", "clave1"));
        assertEquals(1, tareas.size());
        assertEquals("Acme", tareas.get(0).empresa);
        assertEquals("Excel", tareas.get(0).tematica);
        assertFalse(tareas.get(0).propia);
        assertTrue(servicio.consultar(servicio.identificar("docente1", "clave3")).isEmpty());
    }

    @Test
    void reclamarLaHaceSoloDeEseUsuario() {
        iniciar("Acme");
        Usuario a1 = servicio.identificar("admin1", "clave1");
        Usuario a2 = servicio.identificar("admin2", "clave2");
        String id = servicio.consultar(a1).get(0).id;
        servicio.reclamar(a1, id);
        assertTrue(servicio.consultar(a1).get(0).propia);
        assertTrue(servicio.consultar(a2).isEmpty());
        assertThrows(IllegalStateException.class, () -> servicio.reclamar(a2, id));
    }

    @Test
    void noSeCompletaSinReclamarNiAjena() {
        iniciar("Acme");
        Usuario a1 = servicio.identificar("admin1", "clave1");
        Usuario a2 = servicio.identificar("admin2", "clave2");
        String id = servicio.consultar(a1).get(0).id;
        assertThrows(IllegalStateException.class, () -> servicio.completar(a1, id));
        servicio.reclamar(a1, id);
        assertThrows(IllegalStateException.class, () -> servicio.completar(a2, id));
    }

    @Test
    void completarAvanzaElProceso() {
        String instancia = iniciar("Acme");
        Usuario a1 = servicio.identificar("admin1", "clave1");
        String id = servicio.consultar(a1).get(0).id;
        servicio.reclamar(a1, id);
        servicio.completar(a1, id);
        assertTrue(servicio.consultar(a1).isEmpty());
        assertEquals(0, procesos.getRuntimeService().createProcessInstanceQuery().processInstanceId(instancia).count());
    }

    @Test
    void usuarioSinGruposNoVeNiReclama() {
        iniciar("Acme");
        Usuario sinGrupos = new Usuario("fantasma", List.of());
        assertTrue(servicio.consultar(sinGrupos).isEmpty());
        String id = servicio.consultar(servicio.identificar("admin1", "clave1")).get(0).id;
        assertThrows(IllegalStateException.class, () -> servicio.reclamar(sinGrupos, id));
    }
}
