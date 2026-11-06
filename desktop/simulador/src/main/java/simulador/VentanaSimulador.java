package simulador;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;

/**
 * Ventana del simulador: elegir una solicitud, ver solo las comunicaciones de la empresa que el
 * proceso espera en ese momento y enviarlas con sus datos. Toda llamada a Flowable se hace fuera
 * del hilo de la interfaz.
 */
public class VentanaSimulador extends JFrame {
    private static final long serialVersionUID = 1L;
    private static final int REFRESCO_MS = 5000;

    private final JTextField urlCampo = new JTextField(32);
    private final JTextField usuarioCampo = new JTextField(8);
    private final JPasswordField passwordCampo = new JPasswordField(8);

    private final DefaultListModel<Solicitud> solicitudes = new DefaultListModel<>();
    private final JList<Solicitud> listaSolicitudes = new JList<>(solicitudes);

    private final JPanel panelComunicaciones = new JPanel();
    private final JPanel panelCampos = new JPanel(new GridBagLayout());
    private final JButton enviar = new JButton("Enviar comunicación");
    private final JCheckBox autoRefresco = new JCheckBox("Actualizar automáticamente", true);
    private final JLabel estado = new JLabel(" ");

    private final Map<String, JTextField> camposActuales = new LinkedHashMap<>();
    private List<Comunicacion> habilitadasActuales = new ArrayList<>();
    private Comunicacion elegida;
    private Simulador simulador;
    private boolean cargandoLista;

    public VentanaSimulador(String url, String usuario, String password) {
        super("Simulador de comunicaciones de la empresa");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        urlCampo.setText(url);
        usuarioCampo.setText(usuario);
        passwordCampo.setText(password);

        add(construirConexion(), BorderLayout.NORTH);
        add(construirCentro(), BorderLayout.CENTER);
        add(construirPie(), BorderLayout.SOUTH);

        enviar.setEnabled(false);
        enviar.addActionListener(e -> enviarElegida());
        listaSolicitudes.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !cargandoLista) {
                cargarHabilitadas(true);
            }
        });

        new Timer(REFRESCO_MS, e -> {
            if (autoRefresco.isSelected() && simulador != null) {
                refrescarSolicitudes();
                cargarHabilitadas(false);
            }
        }).start();

        setMinimumSize(new Dimension(820, 460));
        pack();
        setLocationRelativeTo(null);
    }

    private JPanel construirConexion() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(BorderFactory.createTitledBorder("Flowable (API REST)"));
        panel.add(new JLabel("URL"));
        panel.add(urlCampo);
        panel.add(new JLabel("Usuario"));
        panel.add(usuarioCampo);
        panel.add(new JLabel("Contraseña"));
        panel.add(passwordCampo);
        JButton conectar = new JButton("Conectar");
        conectar.addActionListener(e -> conectar());
        panel.add(conectar);
        return panel;
    }

    private JPanel construirCentro() {
        JPanel izquierda = new JPanel(new BorderLayout(0, 4));
        izquierda.setBorder(BorderFactory.createTitledBorder("Solicitudes"));
        listaSolicitudes.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scroll = new JScrollPane(listaSolicitudes);
        scroll.setPreferredSize(new Dimension(330, 300));
        izquierda.add(scroll, BorderLayout.CENTER);
        JButton actualizar = new JButton("Actualizar");
        actualizar.addActionListener(e -> {
            refrescarSolicitudes();
            cargarHabilitadas(true);
        });
        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.LEFT));
        acciones.add(actualizar);
        acciones.add(autoRefresco);
        izquierda.add(acciones, BorderLayout.SOUTH);

        panelComunicaciones.setLayout(new BoxLayout(panelComunicaciones, BoxLayout.Y_AXIS));
        JPanel derecha = new JPanel(new BorderLayout(0, 8));
        derecha.setBorder(BorderFactory.createTitledBorder("Comunicaciones habilitadas"));
        JPanel arriba = new JPanel(new BorderLayout(0, 8));
        arriba.add(panelComunicaciones, BorderLayout.NORTH);
        arriba.add(panelCampos, BorderLayout.CENTER);
        derecha.add(arriba, BorderLayout.CENTER);
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        botones.add(enviar);
        derecha.add(botones, BorderLayout.SOUTH);

        JPanel centro = new JPanel(new BorderLayout(8, 0));
        centro.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        centro.add(izquierda, BorderLayout.WEST);
        centro.add(derecha, BorderLayout.CENTER);
        return centro;
    }

    private JPanel construirPie() {
        JPanel pie = new JPanel(new BorderLayout());
        pie.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        pie.add(estado, BorderLayout.CENTER);
        return pie;
    }

    // ---- Conexión y carga de datos --------------------------------------------------------

    private void conectar() {
        simulador = new Simulador(new FlowableClient(
                urlCampo.getText().trim(), usuarioCampo.getText().trim(), new String(passwordCampo.getPassword())));
        mostrar("Conectando...", false);
        refrescarSolicitudes();
    }

    private void refrescarSolicitudes() {
        Simulador actual = simulador;
        if (actual == null) {
            mostrar("Primero conectate a Flowable.", true);
            return;
        }
        new SwingWorker<List<Solicitud>, Void>() {
            @Override
            protected List<Solicitud> doInBackground() {
                return actual.solicitudes();
            }

            @Override
            protected void done() {
                try {
                    reemplazarSolicitudes(get());
                    mostrar(solicitudes.size() + " solicitud(es) en curso.", false);
                } catch (Exception e) {
                    mostrar(mensaje(e), true);
                }
            }
        }.execute();
    }

    private void reemplazarSolicitudes(List<Solicitud> nuevas) {
        Solicitud seleccionada = listaSolicitudes.getSelectedValue();
        String idSeleccionado = seleccionada == null ? null : seleccionada.id();

        cargandoLista = true;
        try {
            solicitudes.clear();
            int indice = -1;
            for (Solicitud solicitud : nuevas) {
                solicitudes.addElement(solicitud);
                if (solicitud.id().equals(idSeleccionado)) {
                    indice = solicitudes.size() - 1;
                }
            }
            if (indice >= 0) {
                listaSolicitudes.setSelectedIndex(indice);
            }
        } finally {
            cargandoLista = false;
        }
        if (idSeleccionado != null && listaSolicitudes.getSelectedValue() == null) {
            mostrarHabilitadas(new ArrayList<>(), true);
        }
    }

    /** @param forzar reconstruir el formulario aunque las comunicaciones no hayan cambiado */
    private void cargarHabilitadas(boolean forzar) {
        Solicitud solicitud = listaSolicitudes.getSelectedValue();
        Simulador actual = simulador;
        if (solicitud == null || actual == null) {
            mostrarHabilitadas(new ArrayList<>(), true);
            return;
        }
        new SwingWorker<List<Comunicacion>, Void>() {
            @Override
            protected List<Comunicacion> doInBackground() {
                return actual.habilitadas(solicitud.id());
            }

            @Override
            protected void done() {
                try {
                    Solicitud ahora = listaSolicitudes.getSelectedValue();
                    if (ahora != null && ahora.id().equals(solicitud.id())) {
                        mostrarHabilitadas(get(), forzar);
                    }
                } catch (Exception e) {
                    mostrar(mensaje(e), true);
                }
            }
        }.execute();
    }

    // ---- Comunicaciones y formulario --------------------------------------------------------

    private void mostrarHabilitadas(List<Comunicacion> habilitadas, boolean forzar) {
        if (!forzar && habilitadas.equals(habilitadasActuales)) {
            return;
        }
        habilitadasActuales = new ArrayList<>(habilitadas);
        Comunicacion anterior = elegida;

        panelComunicaciones.removeAll();
        ButtonGroup grupo = new ButtonGroup();
        elegida = null;
        for (Comunicacion comunicacion : habilitadas) {
            JRadioButton opcion = new JRadioButton(comunicacion.titulo());
            opcion.addActionListener(e -> elegir(comunicacion));
            grupo.add(opcion);
            panelComunicaciones.add(opcion);
            if (comunicacion == anterior) {
                opcion.setSelected(true);
                elegida = comunicacion;
            }
        }
        if (habilitadas.isEmpty()) {
            String texto = listaSolicitudes.getSelectedValue() == null
                    ? "Elegí una solicitud." : "Ninguna comunicación habilitada en este momento.";
            panelComunicaciones.add(new JLabel(texto));
        }
        armarCampos();
        panelComunicaciones.revalidate();
        panelComunicaciones.repaint();
    }

    private void elegir(Comunicacion comunicacion) {
        elegida = comunicacion;
        armarCampos();
    }

    private void armarCampos() {
        panelCampos.removeAll();
        camposActuales.clear();
        enviar.setEnabled(elegida != null);

        if (elegida != null) {
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(4, 4, 4, 4);
            c.anchor = GridBagConstraints.WEST;
            int fila = 0;
            for (Comunicacion.Campo campo : elegida.campos()) {
                c.gridx = 0;
                c.gridy = fila;
                c.fill = GridBagConstraints.NONE;
                c.weightx = 0;
                panelCampos.add(new JLabel(campo.descripcion()), c);

                JTextField texto = new JTextField(sugerido(campo), 28);
                c.gridx = 1;
                c.fill = GridBagConstraints.HORIZONTAL;
                c.weightx = 1;
                panelCampos.add(texto, c);
                camposActuales.put(campo.variable(), texto);
                fila++;
            }
            if (elegida.campos().isEmpty()) {
                c.gridx = 0;
                c.gridy = 0;
                panelCampos.add(new JLabel("Esta comunicación no lleva datos."), c);
            }
        }
        panelCampos.revalidate();
        panelCampos.repaint();
    }

    /** Valor inicial cómodo para las pruebas: la fecha acordada, dos minutos adelante. */
    private static String sugerido(Comunicacion.Campo campo) {
        if (campo.variable().equals("fechaInicio")) {
            return LocalDateTime.now().plusMinutes(2).withNano(0).toString();
        }
        return "";
    }

    private void enviarElegida() {
        Solicitud solicitud = listaSolicitudes.getSelectedValue();
        Simulador actual = simulador;
        Comunicacion comunicacion = elegida;
        if (solicitud == null || actual == null || comunicacion == null) {
            return;
        }
        Map<String, String> datos = new LinkedHashMap<>();
        camposActuales.forEach((variable, campo) -> datos.put(variable, campo.getText()));

        enviar.setEnabled(false);
        mostrar("Enviando...", false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                actual.enviar(solicitud.id(), comunicacion, datos);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    mostrar("Enviado: " + comunicacion.titulo() + " (" + solicitud.nombre() + ").", false);
                    refrescarSolicitudes();
                    cargarHabilitadas(true);
                } catch (Exception e) {
                    mostrar(mensaje(e), true);
                    enviar.setEnabled(elegida != null);
                }
            }
        }.execute();
    }

    // ---- Utilidades -------------------------------------------------------------------------

    private void mostrar(String texto, boolean error) {
        estado.setForeground(error ? new Color(0xB00020) : new Color(0x1B5E20));
        estado.setText(texto);
    }

    /** El mensaje útil de una excepción de SwingWorker (la causa real, no ExecutionException). */
    private static String mensaje(Exception e) {
        Throwable causa = e.getCause() == null ? e : e.getCause();
        return causa.getMessage() == null ? causa.toString() : causa.getMessage();
    }

    /** Abre la ventana en el hilo de la interfaz. */
    public static void abrir(String url, String usuario, String password) {
        SwingUtilities.invokeLater(() -> new VentanaSimulador(url, usuario, password).setVisible(true));
    }
}
