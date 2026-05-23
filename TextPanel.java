import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JPanel;

public class TextPanel extends JPanel {

    char[][] text = new char[][]{};
    int size = 0;
    int baseline = 0;
    int rightshift = 0;
    Font font;

    private BufferedImage buffer;
    private double zoom = 1.0;

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (buffer == null) return;
        Graphics2D g2 = (Graphics2D) g;
        // Nearest-neighbor when zooming in (crisp pixels); bilinear when zooming out (smooth).
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            zoom > 1.0 ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                       : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(buffer, 0, 0,
            (int)(buffer.getWidth()  * zoom),
            (int)(buffer.getHeight() * zoom), null);
    }

    @Override
    public void setFont(Font font) {
        super.setFont(font);
        Map<TextAttribute, Object> attrs = new HashMap<>();
        attrs.put(TextAttribute.SIZE, font.getSize() * 1.25);
        this.font = font.deriveFont(attrs);
    }

    public void setText(String t) {
        String[] a = t.split("\n");
        this.text = new char[a.length][];
        for (int i = 0; i < a.length; i++)
            this.text[i] = a[i].toCharArray();
    }

    public void setFontSize(int size)    { this.size     = size;     }
    public void setBaseLine(int baseline){ this.baseline = baseline; }

    public void setZoom(double factor) {
        zoom = Math.max(0.01, Math.min(factor, 20.0));
        if (buffer != null) {
            setPreferredSize(new Dimension(
                (int)(buffer.getWidth()  * zoom),
                (int)(buffer.getHeight() * zoom)));
            revalidate();
            repaint();
        }
    }

    public double getZoom()      { return zoom; }
    public int getBufferWidth()  { return buffer != null ? buffer.getWidth()  : 0; }
    public int getBufferHeight() { return buffer != null ? buffer.getHeight() : 0; }

    // Renders all characters to an off-screen buffer once.
    // paintComponent then just blits the buffer — O(1) per scroll/zoom event.
    public void renderBuffer() {
        if (font == null || text.length == 0 || size == 0) return;

        int cols = text[0].length;
        int rows = text.length;
        int w = Math.max(1, cols * size);
        int h = Math.max(1, (int)(rows * size + baseline * 1.5));

        buffer = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = buffer.createGraphics();
        g2.setColor(getBackground() != null ? getBackground() : Color.WHITE);
        g2.fillRect(0, 0, w, h);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(font);
        g2.setColor(getForeground() != null ? getForeground() : Color.BLACK);

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < text[i].length; j++) {
                g2.drawString(Character.toString(text[i][j]),
                    (int)(j * size + rightshift / 1.5),
                    (int)(i * size + baseline * 1.5));
            }
        }
        g2.dispose();
    }
}
