package web.backend.api.dto;

import java.util.List;

/**
 * Página de resultados con la forma {"data": [...], "total": n, "start": n, "size": n}.
 * Es la misma forma que usa Flowable en sus listados, así el adaptador de la web la lee igual.
 * Genérica para que la use cualquier listado paginado de la API, no solo el de tareas.
 *
 * @param <T> tipo de cada elemento de la página
 */
public class PaginaResponse<T> {
    private final List<T> data;
    private final long total;
    private final int start;
    private final int size;

    /**
     * Recibe los elementos de esta página, el total de resultados (de todas las páginas),
     * la posición del primero (start) y el tamaño de página pedido (size).
     */
    public PaginaResponse(List<T> data, long total, int start, int size) {
        this.data = List.copyOf(data);
        this.total = total;
        this.start = start;
        this.size = size;
    }

    /** Elementos de esta página. */
    public List<T> getData() {
        return data;
    }

    /** Cantidad total de resultados, sumando todas las páginas. */
    public long getTotal() {
        return total;
    }

    /** Posición (desde 0) del primer elemento de esta página. */
    public int getStart() {
        return start;
    }

    /** Tamaño de página pedido; la última página puede traer menos elementos. */
    public int getSize() {
        return size;
    }
}
