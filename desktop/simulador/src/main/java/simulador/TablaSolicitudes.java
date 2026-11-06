package simulador;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;

/** Tabla de solicitudes: empresa, temática y contacto, sin mostrar el ID largo de la instancia. */
public class TablaSolicitudes extends JTable {
    private static final long serialVersionUID = 1L;

    private final ModeloSolicitudes modelo;

    public TablaSolicitudes() {
        super(new ModeloSolicitudes());
        modelo = (ModeloSolicitudes) getModel();
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setRowHeight(26);
        setShowGrid(false);
        setIntercellSpacing(new java.awt.Dimension(0, 0));
        setFillsViewportHeight(true);
        getTableHeader().setReorderingAllowed(false);
        getColumnModel().getColumn(0).setPreferredWidth(170);
        getColumnModel().getColumn(1).setPreferredWidth(110);
        getColumnModel().getColumn(2).setPreferredWidth(110);
    }

    /** La solicitud elegida, o null si no hay ninguna. */
    public Solicitud seleccionada() {
        int fila = getSelectedRow();
        return fila < 0 ? null : modelo.solicitudes.get(fila);
    }

    public int cantidad() {
        return modelo.solicitudes.size();
    }

    /** Reemplaza el contenido y mantiene elegida la misma solicitud, si sigue existiendo. */
    public void reemplazar(List<Solicitud> nuevas) {
        Solicitud anterior = seleccionada();
        modelo.solicitudes = new ArrayList<>(nuevas);
        modelo.fireTableDataChanged();
        if (anterior == null) {
            return;
        }
        for (int i = 0; i < nuevas.size(); i++) {
            if (nuevas.get(i).id().equals(anterior.id())) {
                setRowSelectionInterval(i, i);
                return;
            }
        }
    }

    private static final class ModeloSolicitudes extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private static final String[] COLUMNAS = {"Empresa", "Temática", "Contacto"};

        private List<Solicitud> solicitudes = new ArrayList<>();

        @Override
        public int getRowCount() {
            return solicitudes.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNAS.length;
        }

        @Override
        public String getColumnName(int columna) {
            return COLUMNAS[columna];
        }

        @Override
        public Object getValueAt(int fila, int columna) {
            Solicitud solicitud = solicitudes.get(fila);
            switch (columna) {
                case 0:
                    return solicitud.nombre();
                case 1:
                    return solicitud.variable("tematica");
                default:
                    return solicitud.variable("contactoNombre");
            }
        }
    }
}
