package materiales;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

/** Pestaña con las consultas 4.3: resultados, duración de las ramas y tareas por persona. */
public class PanelAnalisis extends JPanel {
    private final Analisis analisis;
    private final DefaultTableModel resultados = modelo("Resultado", "Cantidad", "Porcentaje");
    private final DefaultTableModel duraciones = modelo("Rama", "Duración promedio", "Cursos considerados");
    private final DefaultTableModel tareas = modelo("Persona", "Tareas completadas");
    private final JLabel estado = new JLabel(" ");

    public PanelAnalisis(Analisis analisis) {
        super(new BorderLayout(8, 8));
        this.analisis = analisis;
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel centro = new JPanel();
        centro.setLayout(new BoxLayout(centro, BoxLayout.Y_AXIS));
        centro.add(seccion("Resultados de las solicitudes terminadas (4.3.1)", resultados));
        centro.add(Box.createVerticalStrut(10));
        centro.add(seccion("Preparación paralela: duración promedio por rama (4.3.2)", duraciones));
        centro.add(Box.createVerticalStrut(10));
        centro.add(seccion("Tareas completadas por persona (4.3.3)", tareas));
        add(new JScrollPane(centro), BorderLayout.CENTER);

        JPanel abajo = new JPanel(new BorderLayout());
        JButton actualizar = new JButton("Actualizar");
        JPanel boton = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        boton.add(actualizar);
        abajo.add(estado, BorderLayout.CENTER);
        abajo.add(boton, BorderLayout.EAST);
        add(abajo, BorderLayout.SOUTH);
        actualizar.addActionListener(e -> actualizar());
    }

    private static DefaultTableModel modelo(String... columnas) {
        return new DefaultTableModel(columnas, 0) {
            @Override
            public boolean isCellEditable(int f, int c) {
                return false;
            }
        };
    }

    private static JPanel seccion(String titulo, DefaultTableModel modelo) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        JLabel t = new JLabel(titulo);
        t.setFont(t.getFont().deriveFont(Font.BOLD, 13f));
        p.add(t, BorderLayout.NORTH);
        JTable tabla = new JTable(modelo);
        tabla.setRowHeight(24);
        JScrollPane scroll = new JScrollPane(tabla);
        scroll.setPreferredSize(new java.awt.Dimension(500, 24 * 6 + 28));
        p.add(scroll, BorderLayout.CENTER);
        return p;
    }

    public void actualizar() {
        estado.setText("Consultando el historial...");
        new SwingWorker<Object[], Void>() {
            @Override
            protected Object[] doInBackground() {
                return new Object[] {analisis.resultados(), analisis.duraciones(), analisis.tareasPorUsuario()};
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void done() {
                try {
                    Object[] r = get();
                    mostrar((Map<String, Integer>) r[0], (Analisis.Duraciones) r[1], (Map<String, Integer>) r[2]);
                } catch (Exception e) {
                    Throwable c = e.getCause() != null ? e.getCause() : e;
                    estado.setText("Error al consultar: " + c.getMessage());
                }
            }
        }.execute();
    }

    private void mostrar(Map<String, Integer> r, Analisis.Duraciones d, Map<String, Integer> t) {
        resultados.setRowCount(0);
        int total = r.values().stream().mapToInt(Integer::intValue).sum();
        r.forEach((k, v) -> resultados.addRow(new Object[] {k, v, String.format("%.1f %%", Analisis.porcentaje(v, r))}));
        resultados.addRow(new Object[] {"Total terminadas", total, total == 0 ? "-" : "100 %"});
        duraciones.setRowCount(0);
        duraciones.addRow(new Object[] {"Inscripciones", Analisis.Duraciones.formato(d.inscripciones), d.cursosInscripciones});
        duraciones.addRow(new Object[] {"Materiales", Analisis.Duraciones.formato(d.materiales), d.cursosMateriales});
        duraciones.addRow(new Object[] {"Recursos", Analisis.Duraciones.formato(d.recursos), d.cursosRecursos});
        tareas.setRowCount(0);
        t.forEach((k, v) -> tareas.addRow(new Object[] {k, v}));
        estado.setText("Actualizado " + new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date()));
    }
}
