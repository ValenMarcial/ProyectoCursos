package web.backend.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import shared.PasswordHash;
import web.backend.api.dto.ActividadResponse;
import web.backend.api.dto.CursoEmpresaResponse;
import web.backend.flowable.FlowableRespuestaException;
import web.backend.flowable.FlowableTareaClient;
import web.backend.persistence.EmpresaRepository;
import web.backend.persistence.EmpresaRepository.Cuenta;
import web.backend.persistence.EmpresaRepository.CursoConInstancia;
import web.backend.service.EmpresaException.Motivo;

/**
 * Reglas de la API de empresas (escenario 10): login con la cuenta que crea "Habilitar acceso",
 * sus cursos y las actividades en curso de cada uno.
 */
@Service
public class EmpresaService {
    /** Estados en los que el proceso ya terminó: no hay actividades para consultar. */
    private static final Set<String> ESTADOS_FINALES = Set.of("finalizado", "cancelado");
    /**
     * Se compara contra este hash cuando el usuario no existe, para que la respuesta tarde lo mismo
     * y no revele qué usuarios hay.
     */
    private static final String HASH_SIN_CUENTA = PasswordHash.hash("sin-cuenta");

    private final EmpresaRepository empresas;
    private final FlowableTareaClient flowable;

    public EmpresaService(EmpresaRepository empresas, FlowableTareaClient flowable) {
        this.empresas = empresas;
        this.flowable = flowable;
    }

    /** Valida usuario y clave. El usuario no distingue mayúsculas, como el email del que sale. */
    public EmpresaSesion iniciar(String usuario, String clave) {
        Optional<Cuenta> cuenta = empresas.buscarCuenta(usuario.trim().toLowerCase(Locale.ROOT));
        boolean valida = PasswordHash.coincide(clave, cuenta.map(c -> c.claveHash).orElse(HASH_SIN_CUENTA));
        if (cuenta.isEmpty() || !valida) {
            throw new EmpresaException(Motivo.CREDENCIALES_INVALIDAS);
        }
        return new EmpresaSesion(cuenta.get().empresaId, cuenta.get().usuario, cuenta.get().empresa);
    }

    public List<CursoEmpresaResponse> cursos(EmpresaSesion sesion) {
        return empresas.cursos(sesion.getEmpresaId());
    }

    /**
     * Detalle de un curso de la empresa con las actividades en curso, incluidas las de ramas
     * paralelas. Un curso de otra empresa responde igual que uno que no existe.
     */
    public CursoEmpresaResponse curso(EmpresaSesion sesion, long cursoId) {
        CursoConInstancia curso = empresas.curso(sesion.getEmpresaId(), cursoId)
                .orElseThrow(() -> new EmpresaException(Motivo.CURSO_NO_ENCONTRADO));
        if (curso.instanciaId == null || ESTADOS_FINALES.contains(curso.curso.estado)) {
            return curso.curso.conActividades(List.of());
        }
        try {
            return curso.curso.conActividades(flowable.actividadesEnCurso(curso.instanciaId));
        } catch (RestClientException | FlowableRespuestaException exception) {
            throw new EmpresaException(Motivo.FLOWABLE_NO_RESPONDE, exception);
        }
    }
}
