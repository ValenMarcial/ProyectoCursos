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
        getTableHeader().setFont(getTableHeader().getFont().deriveFont(java.awt.Font.BOLD));
        setSelectionBackground(new java.awt.Color(0xDCE8FF));
        setSelectionForeground(java.awt.Color.BLACK);
        setShowHorizontalLines(true);
        setGridColor(new java.awt.Color(0xE6E6E6));
        getColumnModel().getColumn(3).setCellRenderer(new RenderizadorEtapa());
        getColumnModel().getColumn(0).setPreferredWidth(170);
        getColumnModel().getColumn(1).setPreferredWidth(110);
        getColumnModel().getColumn(2).setPreferredWidth(90);
        getColumnModel().getColumn(3).setPreferredWidth(260);
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

    /** Pinta la etapa según a quién le toca: empresa (verde), institución (naranja) o nadie (gris). */
    private final class RenderizadorEtapa extends javax.swing.table.DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public java.awt.Component getTableCellRendererComponent(JTable tabla, Object valor, boolean elegida,
                boolean foco, int fila, int columna) {
            super.getTableCellRendererComponent(tabla, valor, elegida, false, fila, columna);
            Etapa etapa = modelo.solicitudes.get(fila).etapa();
            java.awt.Color color = java.awt.Color.GRAY;
            if (etapa != null && etapa.turno() == Etapa.Turno.EMPRESA) {
                color = new java.awt.Color(0x1B7A34);
            } else if (etapa != null && etapa.turno() == Etapa.Turno.INSTITUCION) {
                color = new java.awt.Color(0xB26A00);
            }
            setForeground(color);
            setFont(getFont().deriveFont(java.awt.Font.BOLD));
            return this;
        }
    }

    private static final class ModeloSolicitudes extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private static final String[] COLUMNAS = {"Empresa", "Temática", "Contacto", "Etapa"};

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
                case 2:
                    return solicitud.variable("contactoNombre");
                default:
                    return solicitud.etapa() == null ? "..." : solicitud.etapa().texto();
            }
        }
    }
}
