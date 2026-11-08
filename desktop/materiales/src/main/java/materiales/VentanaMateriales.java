package materiales;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.table.AbstractTableModel;
import materiales.ServicioMateriales.Usuario;

/** Ventana de la app: identificación con el usuario de Flowable y lista de tareas "Preparar materiales". */
public class VentanaMateriales extends JFrame {
    private static final String LOGIN = "login";
    private static final String TAREAS = "tareas";

    private final ServicioMateriales servicio;
    private final CardLayout cartas = new CardLayout();
    private final JPanel raiz = new JPanel(cartas);

    private final JTextField campoUsuario = new JTextField(16);
    private final JPasswordField campoClave = new JPasswordField(16);
    private final JLabel errorLogin = new JLabel(" ");

    private final ModeloTareas modelo = new ModeloTareas();
    private final JTable tabla = new JTable(modelo);
    private final JLabel saludo = new JLabel();
    private final JLabel estado = new JLabel(" ");
    private final JButton reclamar = new JButton("Reclamar");
    private final JButton completar = new JButton("Completar");
    private final Timer refresco;
    private Usuario usuario;

    public VentanaMateriales(ServicioMateriales servicio) {
        super("Preparar materiales");
        this.servicio = servicio;
        raiz.add(panelLogin(), LOGIN);
        raiz.add(panelTareas(), TAREAS);
        setContentPane(raiz);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(820, 460));
        pack();
        setLocationRelativeTo(null);
        refresco = new Timer(5000, e -> actualizar());
    }

    private JPanel panelLogin() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.gridx = 0;
        c.gridwidth = 2;
        JLabel titulo = new JLabel("Preparar materiales");
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 22f));
        p.add(titulo, c);
        c.gridy = 1;
        p.add(new JLabel("Ingresá con tu usuario y contraseña de Flowable"), c);
        c.gridwidth = 1;
        c.gridy = 2;
        p.add(new JLabel("Usuario"), c);
        c.gridx = 1;
        p.add(campoUsuario, c);
        c.gridx = 0;
        c.gridy = 3;
        p.add(new JLabel("Contraseña"), c);
        c.gridx = 1;
        p.add(campoClave, c);
        JButton entrar = new JButton("Ingresar");
        c.gridx = 0;
        c.gridy = 4;
        c.gridwidth = 2;
        p.add(entrar, c);
        errorLogin.setForeground(new Color(0xC62828));
        c.gridy = 5;
        p.add(errorLogin, c);
        entrar.addActionListener(e -> ingresar(entrar));
        campoClave.addActionListener(e -> ingresar(entrar));
        getRootPane().setDefaultButton(entrar);
        return p;
    }

    private JPanel panelTareas() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JPanel arriba = new JPanel(new BorderLayout());
        saludo.setFont(saludo.getFont().deriveFont(Font.BOLD, 15f));
        arriba.add(saludo, BorderLayout.WEST);
        JButton salir = new JButton("Cerrar sesión");
        arriba.add(salir, BorderLayout.EAST);
        p.add(arriba, BorderLayout.NORTH);

        tabla.setRowHeight(26);
        tabla.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        tabla.getSelectionModel().addListSelectionListener(e -> habilitarBotones());
        p.add(new JScrollPane(tabla), BorderLayout.CENTER);

        JPanel abajo = new JPanel(new BorderLayout());
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton refrescar = new JButton("Actualizar");
        botones.add(refrescar);
        botones.add(reclamar);
        botones.add(completar);
        abajo.add(estado, BorderLayout.CENTER);
        abajo.add(botones, BorderLayout.EAST);
        p.add(abajo, BorderLayout.SOUTH);

        reclamar.setEnabled(false);
        completar.setEnabled(false);
        refrescar.addActionListener(e -> actualizar());
        reclamar.addActionListener(e -> accion("Tarea reclamada.", () -> {
            servicio.reclamar(usuario, seleccionada().id);
            return null;
        }));
        completar.addActionListener(e -> {
            TareaMateriales t = seleccionada();
            int ok = JOptionPane.showConfirmDialog(this, "¿Marcar como completados los materiales de " + t.empresa + "?",
                    "Completar tarea", JOptionPane.OK_CANCEL_OPTION);
            if (ok == JOptionPane.OK_OPTION) {
                accion("Tarea completada.", () -> {
                    servicio.completar(usuario, t.id);
                    return null;
                });
            }
        });
        salir.addActionListener(e -> cerrarSesion());
        return p;
    }

    private void ingresar(JButton entrar) {
        String id = campoUsuario.getText();
        String clave = new String(campoClave.getPassword());
        entrar.setEnabled(false);
        errorLogin.setText("Verificando...");
        enSegundoPlano(() -> servicio.identificar(id, clave), u -> {
            entrar.setEnabled(true);
            if (u == null) {
                errorLogin.setText("Usuario o contraseña incorrectos.");
            } else if (!u.esAdministrativo()) {
                errorLogin.setText("Esta aplicación es solo para el personal de Administración.");
            } else {
                usuario = u;
                campoClave.setText("");
                errorLogin.setText(" ");
                saludo.setText("Usuario: " + u.id);
                cartas.show(raiz, TAREAS);
                getRootPane().setDefaultButton(null);
                actualizar();
                refresco.start();
            }
        }, ex -> {
            entrar.setEnabled(true);
            errorLogin.setText("No se pudo verificar: " + mensaje(ex));
        });
    }

    private void cerrarSesion() {
        refresco.stop();
        usuario = null;
        modelo.reemplazar(new ArrayList<>());
        cartas.show(raiz, LOGIN);
    }

    private void actualizar() {
        if (usuario == null) {
            return;
        }
        Usuario u = usuario;
        String idSeleccionada = tabla.getSelectedRow() >= 0 ? seleccionada().id : null;
        enSegundoPlano(() -> servicio.consultar(u), lista -> {
            modelo.reemplazar(lista);
            for (int i = 0; i < lista.size(); i++) {
                if (lista.get(i).id.equals(idSeleccionada)) {
                    tabla.setRowSelectionInterval(i, i);
                }
            }
            habilitarBotones();
            estado.setText(lista.isEmpty() ? "No hay tareas de materiales para vos ahora." : lista.size() + " tarea(s).");
        }, ex -> estado.setText("Error al consultar: " + mensaje(ex)));
    }

    private void accion(String exito, Callable<Void> trabajo) {
        enSegundoPlano(trabajo, v -> {
            estado.setText(exito);
            actualizar();
        }, ex -> {
            estado.setText(mensaje(ex));
            actualizar();
        });
    }

    private TareaMateriales seleccionada() {
        return modelo.tarea(tabla.getSelectedRow());
    }

    private void habilitarBotones() {
        TareaMateriales t = tabla.getSelectedRow() >= 0 ? seleccionada() : null;
        reclamar.setEnabled(t != null && !t.propia);
        completar.setEnabled(t != null && t.propia);
    }

    private static <T> void enSegundoPlano(Callable<T> trabajo, Consumer<T> alTerminar, Consumer<Exception> alFallar) {
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return trabajo.call();
            }

            @Override
            protected void done() {
                try {
                    alTerminar.accept(get());
                } catch (Exception e) {
                    alFallar.accept(e);
                }
            }
        }.execute();
    }

    private static String mensaje(Exception e) {
        Throwable causa = e.getCause() != null ? e.getCause() : e;
        return causa.getMessage() == null ? causa.toString() : causa.getMessage();
    }

    public static void abrir(ServicioMateriales servicio) {
        SwingUtilities.invokeLater(() -> new VentanaMateriales(servicio).setVisible(true));
    }

    private static final class ModeloTareas extends AbstractTableModel {
        private static final String[] COLUMNAS = {"Empresa", "Temática", "Creada", "Estado"};
        private final SimpleDateFormat formato = new SimpleDateFormat("dd/MM HH:mm");
        private List<TareaMateriales> filas = new ArrayList<>();

        void reemplazar(List<TareaMateriales> nuevas) {
            filas = nuevas;
            fireTableDataChanged();
        }

        TareaMateriales tarea(int fila) {
            return filas.get(fila);
        }

        @Override
        public int getRowCount() {
            return filas.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNAS.length;
        }

        @Override
        public String getColumnName(int c) {
            return COLUMNAS[c];
        }

        @Override
        public Object getValueAt(int f, int c) {
            TareaMateriales t = filas.get(f);
            switch (c) {
                case 0: return t.empresa;
                case 1: return t.tematica;
                case 2: return formato.format(t.creada);
                default: return t.estado();
            }
        }
    }
}
