package flowable.proposal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.Test;

class IncrementProposalVersionTest {
    private final Map<String, Object> variables = new HashMap<>();
    private final DelegateExecution execution = (DelegateExecution) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] {DelegateExecution.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getVariable":
                        return variables.get(args[0]);
                    case "setVariable":
                        variables.put((String) args[0], args[1]);
                        return null;
                    default:
                        throw new UnsupportedOperationException(method.getName());
                }
            });

    private final IncrementProposalVersion listener = new IncrementProposalVersion();

    @Test
    void laPrimeraModificacionEsLaVersionDos() {
        listener.notify(execution);

        assertEquals(2L, version());
    }

    @Test
    void cadaModificacionSumaUno() {
        variables.put("versionPropuesta", 2L);
        listener.notify(execution);
        assertEquals(3L, version());

        variables.put("versionPropuesta", 7L);
        listener.notify(execution);
        assertEquals(8L, version());
    }

    @Test
    void aceptaUnaVersionGuardadaComoEntero() {
        variables.put("versionPropuesta", 4);
        listener.notify(execution);

        assertEquals(5L, version());
    }

    @Test
    void noTocaLasOtrasVariables() {
        variables.put("observaciones", "Cambiar presupuesto");
        listener.notify(execution);

        assertEquals("Cambiar presupuesto", variables.get("observaciones"));
    }

    private long version() {
        return ((Number) variables.get("versionPropuesta")).longValue();
    }
}
