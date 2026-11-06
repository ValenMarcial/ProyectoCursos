package simulador;

/** En qué punto del proceso está una solicitud y quién tiene que actuar. */
public final class Etapa {
    /** A quién le toca actuar. */
    public enum Turno {
        EMPRESA, INSTITUCION, NADIE
    }

    private final String texto;
    private final Turno turno;

    public Etapa(String texto, Turno turno) {
        this.texto = texto;
        this.turno = turno;
    }

    public String texto() {
        return texto;
    }

    public Turno turno() {
        return turno;
    }

    @Override
    public String toString() {
        return texto;
    }
}
