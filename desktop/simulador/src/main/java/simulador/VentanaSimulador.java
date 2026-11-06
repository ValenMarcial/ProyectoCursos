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

import javax.swing.Box;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
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
    private static final String TITULO_DERECHA = "Comunicaciones habilitadas";

    private final JTextField urlCampo = new JTextField(32);
    private final JTextField usuarioCampo = new JTextField(8);
    private final JPasswordField passwordCampo = new JPasswordField(8);

    private final TablaSolicitudes tabla = new TablaSolicitudes();

    private final JPanel panelComunicaciones = new JPanel();
    private final JPanel panelCampos = new JPanel(new GridBagLayout());
    private final JButton enviar = new JButton("Enviar comunicación");
    private final JCheckBox autoRefresco = new JCheckBox("Actualizar automáticamente", true);
    private final JLabel estado = new JLabel(" ");
    private final javax.swing.JTextArea registro = new javax.swing.JTextArea(5, 40);
    private final JPanel panelDerecha = new JPanel(new BorderLayout(0, 8));

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
        tabla.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !cargandoLista) {
                actualizarTitulo();
                cargarHabilitadas(true);
            }
        });

        new Timer(REFRESCO_MS, e -> {
            if (autoRefresco.isSelected() && simulador != null) {
                refrescarSolicitudes();
                cargarHabilitadas(false);
            }
        }).start();

        setMinimumSize(new Dimension(1000, 620));
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
        JScrollPane scroll = new JScrollPane(tabla);
        scroll.setPreferredSize(new Dimension(640, 300));
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
        JPanel derecha = panelDerecha;
        derecha.setBorder(BorderFactory.createTitledBorder(TITULO_DERECHA));
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
        registro.setEditable(false);
        registro.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
        JScrollPane scrollRegistro = new JScrollPane(registro);
        scrollRegistro.setBorder(BorderFactory.createTitledBorder("Registro"));

        JPanel pie = new JPanel(new BorderLayout());
        pie.setBorder(BorderFactory.createEmptyBorder(0, 8, 6, 8));
        pie.add(scrollRegistro, BorderLayout.CENTER);
        JPanel linea = new JPanel(new BorderLayout());
        linea.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 4));
        linea.add(estado, BorderLayout.CENTER);
        pie.add(linea, BorderLayout.SOUTH);
        return pie;
    }

    /** Agrega una línea con la hora al registro de lo que hizo el simulador. */
    private void registrar(String texto) {
        registro.append(java.time.LocalTime.now().withNano(0) + "  " + texto + "\n");
        registro.setCaretPosition(registro.getDocument().getLength());
    }

    private void actualizarTitulo() {
        Solicitud solicitud = tabla.seleccionada();
        String titulo = solicitud == null ? TITULO_DERECHA : solicitud.nombre() + " – " + TITULO_DERECHA.toLowerCase();
        panelDerecha.setBorder(BorderFactory.createTitledBorder(titulo));
        panelDerecha.repaint();
    }

    // ---- Conexión y carga de datos --------------------------------------------------------

    private void conectar() {
        simulador = new Simulador(new FlowableClient(
                urlCampo.getText().trim(), usuarioCampo.getText().trim(), new String(passwordCampo.getPassword())));
        mostrar("Conectando...", false);
        registrar("Conectando a " + urlCampo.getText().trim());
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
                return actual.solicitudesConEtapa();
            }

            @Override
            protected void done() {
                try {
                    reemplazarSolicitudes(get());
                    mostrar(tabla.cantidad() + " solicitud(es) en curso.", false);
                } catch (Exception e) {
                    mostrar(mensaje(e), true);
                }
            }
        }.execute();
    }

    private void reemplazarSolicitudes(List<Solicitud> nuevas) {
        boolean habiaSeleccion = tabla.seleccionada() != null;

        cargandoLista = true;
        try {
            tabla.reemplazar(nuevas);
        } finally {
            cargandoLista = false;
        }
        actualizarTitulo();
        if (habiaSeleccion && tabla.seleccionada() == null) {
            mostrarHabilitadas(new ArrayList<>(), true);
        }
    }

    /** @param forzar reconstruir el formulario aunque las comunicaciones no hayan cambiado */
    private void cargarHabilitadas(boolean forzar) {
        Solicitud solicitud = tabla.seleccionada();
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
                    Solicitud ahora = tabla.seleccionada();
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
            BotonComunicacion opcion = new BotonComunicacion(comunicacion.titulo(), color(comunicacion));
            opcion.addActionListener(e -> elegir(comunicacion));
            grupo.add(opcion);
            panelComunicaciones.add(opcion);
            panelComunicaciones.add(Box.createVerticalStrut(8));
            if (comunicacion == anterior) {
                opcion.setSelected(true);
                elegida = comunicacion;
            }
        }
        if (habilitadas.isEmpty()) {
            Solicitud elegidaFila = tabla.seleccionada();
            String texto = "Elegí una solicitud.";
            if (elegidaFila != null) {
                texto = "La empresa no tiene nada para enviar ahora.";
                if (elegidaFila.etapa() != null) {
                    texto += " " + elegidaFila.etapa().texto() + ".";
                }
            }
            panelComunicaciones.add(new JLabel("<html><body style='width:300px'>" + texto + "</body></html>"));
        }
        armarCampos();
        panelComunicaciones.revalidate();
        panelComunicaciones.repaint();
    }

    private static Color color(Comunicacion comunicacion) {
        switch (comunicacion) {
            case ACEPTACION:
                return new Color(0x2E7D32);
            case RECHAZO:
                return new Color(0xC62828);
            case MODIFICACIONES:
                return new Color(0xEF8F00);
            case NOMINA:
                return new Color(0x1565C0);
            default:
                return new Color(0x6D4C41);
        }
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
        Solicitud solicitud = tabla.seleccionada();
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
                    registrar("Enviado a " + solicitud.nombre() + ": " + comunicacion.titulo());
                    refrescarSolicitudes();
                    cargarHabilitadas(true);
                } catch (Exception e) {
                    mostrar(mensaje(e), true);
                    registrar("ERROR al enviar " + comunicacion.titulo() + " a " + solicitud.nombre() + ": " + mensaje(e));
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
        SwingUtilities.invokeLater(() -> {
            VentanaSimulador ventana = new VentanaSimulador(url, usuario, password);
            ventana.setVisible(true);
            ventana.conectar();
        });
    }
}
