package simulador;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JToggleButton;

/** Botón de una comunicación: un color propio y, al elegirlo, se rellena entero. */
public class BotonComunicacion extends JToggleButton {
    private static final long serialVersionUID = 1L;

    private final Color color;

    public BotonComunicacion(String texto, Color color) {
        super(texto);
        this.color = color;
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setFont(getFont().deriveFont(Font.BOLD, 14f));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setPreferredSize(new Dimension(300, 40));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        setAlignmentX(LEFT_ALIGNMENT);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int ancho = getWidth() - 1;
        int alto = getHeight() - 1;

        Color fondo = isSelected() ? color : mezclarConBlanco(color, 0.88f);
        g2.setColor(fondo);
        g2.fillRoundRect(0, 0, ancho, alto, 14, 14);
        g2.setColor(color);
        g2.setStroke(new BasicStroke(isSelected() ? 2f : 1.2f));
        g2.drawRoundRect(0, 0, ancho, alto, 14, 14);

        g2.setFont(getFont());
        FontMetrics medidas = g2.getFontMetrics();
        g2.setColor(isSelected() ? Color.WHITE : color.darker());
        int x = 14;
        int y = (getHeight() - medidas.getHeight()) / 2 + medidas.getAscent();
        g2.drawString(getText(), x, y);
        g2.dispose();
    }

    private static Color mezclarConBlanco(Color base, float cantidadDeBlanco) {
        int r = Math.round(base.getRed() + (255 - base.getRed()) * cantidadDeBlanco);
        int g = Math.round(base.getGreen() + (255 - base.getGreen()) * cantidadDeBlanco);
        int b = Math.round(base.getBlue() + (255 - base.getBlue()) * cantidadDeBlanco);
        return new Color(r, g, b);
    }
}
