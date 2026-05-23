import java.io.File;
import java.io.IOException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Image;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.filechooser.FileSystemView;

public class ImageToText {

    // Paul Bourke's canonical ASCII art gradient: dark (dense) → light (sparse).
    // Built once at class load time; never recomputed per transform.
    private static final char[] SHADE_CHARS =
        "$@B%8&WM#*oahkbdpqwmZO0QLCJUYXzcvunxrjft/\\|()1{}[]?-_+~<>i!lI;:,\"^`'. ".toCharArray();

    private static final Map<Integer, Character> SHADE_MAP = buildShadeMap();

    private static Map<Integer, Character> buildShadeMap() {
        Map<Integer, Character> map = new HashMap<>(256);
        for (int i = 0; i <= 255; i++) {
            int idx = (int) (i * (double)(SHADE_CHARS.length - 1) / 255);
            idx = Math.max(0, Math.min(idx, SHADE_CHARS.length - 1));
            map.put(i, SHADE_CHARS[idx]);
        }
        return Collections.unmodifiableMap(map);
    }

    static ImageToText ITT;

    JFrame frame;
    JPanel panel;
    JPanel controlPanel;
    JTextField pathField;
    TextPrompt pathPrompt;
    JButton browse;
    JFileChooser fileChooser;
    JLabel scaleLabel;
    JTextField scaleField;
    JButton transform;
    JLabel statusLabel;
    JLabel zoomLabel;
    JScrollPane scrollPane;
    TextPanel textPanel;

    ImageToText() throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

        frame = new JFrame("Image to Text");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(700, 500));

        panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(new EmptyBorder(8, 8, 4, 8));

        // Control panel — two explicit rows so zoom buttons always show
        controlPanel = new JPanel(new BorderLayout(0, 4));
        controlPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder("Settings"),
            new EmptyBorder(0, 4, 4, 4)));

        // Row 1: path field stretches to fill width, browse on right
        pathField = new JTextField();
        pathPrompt = new TextPrompt("Image path…", pathField);
        browse = new JButton("Browse…");
        fileChooser = new JFileChooser(FileSystemView.getFileSystemView());
        fileChooser.addChoosableFileFilter(
            new FileNameExtensionFilter("Image files", "jpg", "jpeg", "png", "gif"));
        fileChooser.setAcceptAllFileFilterUsed(false);
        browse.addActionListener(e -> {
            if (fileChooser.showOpenDialog(panel) == JFileChooser.APPROVE_OPTION) {
                path = fileChooser.getSelectedFile().getAbsolutePath();
                pathField.setText(path);
            }
        });
        JPanel pathRow = new JPanel(new BorderLayout(6, 0));
        pathRow.add(pathField, BorderLayout.CENTER);
        pathRow.add(browse,    BorderLayout.EAST);
        controlPanel.add(pathRow, BorderLayout.NORTH);

        // Row 2: scale + transform | zoom controls
        scaleLabel = new JLabel("Scale (0.1–1.0):");
        scaleField = new JTextField("1.0", 5);
        scaleLabel.setLabelFor(scaleField);
        transform = new JButton("Transform");
        transform.addActionListener(e -> runTransform());

        zoomLabel = new JLabel("—", JLabel.CENTER);
        zoomLabel.setPreferredSize(new Dimension(52, 24));
        JButton zoomOut = new JButton("−");
        JButton zoomIn  = new JButton("+");
        JButton fitBtn  = new JButton("Fit");
        zoomOut.addActionListener(e -> adjustZoom(1.0 / 1.25));
        zoomIn .addActionListener(e -> adjustZoom(1.25));
        fitBtn .addActionListener(e -> fitToWindow());

        JPanel actionRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
        actionRow.add(scaleLabel);
        actionRow.add(scaleField);
        actionRow.add(transform);
        actionRow.add(new JLabel("   "));
        actionRow.add(zoomOut);
        actionRow.add(zoomLabel);
        actionRow.add(zoomIn);
        actionRow.add(fitBtn);
        controlPanel.add(actionRow, BorderLayout.SOUTH);

        panel.add(controlPanel, BorderLayout.PAGE_START);

        // Text output panel — focusable so keyboard shortcuts work after clicking it
        textPanel = new TextPanel();
        textPanel.setOpaque(true);
        textPanel.setFocusable(true);

        // Drag-to-pan: record screen position on press, update viewport on drag
        Point[] panState = { new Point(), new Point() }; // [0] dragStart, [1] viewStart
        MouseAdapter panAdapter = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                panState[0] = e.getLocationOnScreen();
                panState[1] = scrollPane.getViewport().getViewPosition();
                textPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                textPanel.requestFocusInWindow();
            }
            @Override public void mouseDragged(MouseEvent e) {
                Point now = e.getLocationOnScreen();
                int x = Math.max(0, panState[1].x - (now.x - panState[0].x));
                int y = Math.max(0, panState[1].y - (now.y - panState[0].y));
                scrollPane.getViewport().setViewPosition(new Point(x, y));
            }
            @Override public void mouseReleased(MouseEvent e) {
                textPanel.setCursor(Cursor.getDefaultCursor());
            }
        };
        textPanel.addMouseListener(panAdapter);
        textPanel.addMouseMotionListener(panAdapter);

        scrollPane = new JScrollPane(textPanel,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        // Two-finger scroll → pan (scroll pane handles it natively on all platforms).
        // Ctrl + scroll → zoom (works on Mac trackpad, Windows touchpad, mouse wheel).
        scrollPane.addMouseWheelListener(e -> {
            if (e.isControlDown()) {
                adjustZoom(Math.pow(1.08, -e.getPreciseWheelRotation()));
                e.consume();
            }
            // else: fall through to scroll pane's default handler for panning
        });

        // Keyboard shortcuts (active whenever the window is focused,
        // but arrow keys are suppressed while a text field has focus)
        int panStep = 80;
        InputMap  im = scrollPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = scrollPane.getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT,  0), "pan-left");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "pan-right");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP,    0), "pan-up");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN,  0), "pan-down");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, KeyEvent.CTRL_DOWN_MASK), "zoom-in");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS,  KeyEvent.CTRL_DOWN_MASK), "zoom-out");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_0,      KeyEvent.CTRL_DOWN_MASK), "zoom-fit");

        am.put("pan-left",  panAction(-panStep, 0));
        am.put("pan-right", panAction( panStep, 0));
        am.put("pan-up",    panAction(0, -panStep));
        am.put("pan-down",  panAction(0,  panStep));
        am.put("zoom-in",   new AbstractAction() {
            public void actionPerformed(ActionEvent e) { adjustZoom(1.25); }});
        am.put("zoom-out",  new AbstractAction() {
            public void actionPerformed(ActionEvent e) { adjustZoom(1.0 / 1.25); }});
        am.put("zoom-fit",  new AbstractAction() {
            public void actionPerformed(ActionEvent e) { fitToWindow(); }});

        panel.add(scrollPane, BorderLayout.CENTER);

        // Status bar
        statusLabel = new JLabel("Ready — browse or type a path, then click Transform.");
        statusLabel.setBorder(new EmptyBorder(3, 4, 3, 4));
        panel.add(statusLabel, BorderLayout.PAGE_END);

        frame.getContentPane().add(panel);
        frame.setSize(900, 650);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    String path = "";
    double scale = 1.0;
    File file;
    BufferedImage img;
    int width, height;
    BufferedImage grayImg;
    int newWidth, newHeight;
    int size;
    int maxHeight;
    int fontSize;
    Font font;
    int rightshift;

    public void getFile() throws IOException {
        file = new File(path);
        img = ImageIO.read(file);
        if (img == null) throw new IOException("Unsupported or unreadable image: " + path);
        width  = img.getWidth();
        height = img.getHeight();
    }

    public BufferedImage resize() {
        Image tmp = img.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
        BufferedImage dimg = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = dimg.createGraphics();
        g2d.drawImage(tmp, 0, 0, null);
        g2d.dispose();
        return dimg;
    }

    public String convert() throws IOException {
        StringBuilder sb = new StringBuilder(newWidth * newHeight + newHeight);
        rightshift = 0;
        for (int y = 0; y < newHeight; y++) {
            for (int x = 0; x < newWidth; x++) {
                int pixel = grayImg.getRGB(x, y);
                Color color = new Color(pixel, false);
                // Rec. 601 luma coefficients
                int gray = (int) Math.round(
                    0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue());
                gray = Math.min(gray, 255);
                grayImg.setRGB(x, y, gray << 16 | gray << 8 | gray);
                char ch = SHADE_MAP.get(gray);
                sb.append(ch);
                if (y == 0 && x == 0)
                    rightshift = size / 2 - textPanel.getFontMetrics(font).charWidth(ch) / 2;
            }
            sb.append('\n');
        }
        String base = path.substring(0, path.lastIndexOf('.'));
        String ext  = path.substring(path.lastIndexOf('.'));
        ImageIO.write(grayImg, "JPG", createFile(base + "GrayScale" + ext));
        return sb.toString();
    }

    public void getTextSize() {
        FontMetrics fm = textPanel.getFontMetrics(font);
        size      = Math.max(fm.getHeight(), fm.getMaxAscent());
        maxHeight = fm.getMaxAscent();
        for (int i = 32; i <= 126; i++)
            size = Math.max(size, fm.charWidth((char) i));
    }

    public File createFile(String p) throws IOException {
        File f = new File(p);
        if (f.createNewFile()) return f;
        String base = p.substring(0, p.lastIndexOf('.'));
        String ext  = p.substring(p.lastIndexOf('.'));
        for (int i = 1; i <= 200; i++) {
            f = new File(base + " (" + i + ")" + ext);
            if (f.createNewFile()) return f;
        }
        throw new IOException("Could not create output file after 200 attempts: " + p);
    }

    private AbstractAction panAction(int dx, int dy) {
        return new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                // Don't steal arrow keys while a text field has focus
                if (KeyboardFocusManager.getCurrentKeyboardFocusManager()
                        .getFocusOwner() instanceof JTextField) return;
                pan(dx, dy);
            }
        };
    }

    private void pan(int dx, int dy) {
        JViewport vp = scrollPane.getViewport();
        Point p          = vp.getViewPosition();
        Dimension view   = vp.getView().getPreferredSize();
        Dimension extent = vp.getExtentSize();
        int newX = Math.max(0, Math.min(p.x + dx, Math.max(0, view.width  - extent.width)));
        int newY = Math.max(0, Math.min(p.y + dy, Math.max(0, view.height - extent.height)));
        vp.setViewPosition(new Point(newX, newY));
    }

    private void adjustZoom(double factor) {
        textPanel.setZoom(textPanel.getZoom() * factor);
        zoomLabel.setText(String.format("%.0f%%", textPanel.getZoom() * 100));
    }

    private void fitToWindow() {
        int bw = textPanel.getBufferWidth();
        int bh = textPanel.getBufferHeight();
        if (bw <= 0 || bh <= 0) return;
        double fz = Math.min(
            (double) scrollPane.getViewport().getWidth()  / bw,
            (double) scrollPane.getViewport().getHeight() / bh);
        textPanel.setZoom(Math.max(0.01, fz));
        zoomLabel.setText(String.format("%.0f%%", textPanel.getZoom() * 100));
    }

    private void runTransform() {
        String rawPath = pathField.getText().trim();
        if (rawPath.isEmpty()) {
            JOptionPane.showMessageDialog(frame,
                "Please enter or browse to an image path.",
                "No Image", JOptionPane.WARNING_MESSAGE);
            return;
        }

        transform.setEnabled(false);
        browse.setEnabled(false);
        statusLabel.setText("Processing…");

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                path = rawPath;
                if (!scaleField.getText().isEmpty()) {
                    scale = Double.parseDouble(scaleField.getText());
                    scale = (double) Math.round(scale * 10) / 10;
                    scale = Math.max(0.1, Math.min(scale, 1.0));
                }

                ITT.getFile();
                newWidth  = (int) (width  * scale);
                newHeight = (int) (height * scale);
                grayImg   = resize();

                fontSize = 18;
                font = new Font(Font.MONOSPACED, Font.BOLD, fontSize);
                textPanel.setFont(font);
                ITT.getTextSize();

                return ITT.convert();
            }

            @Override
            protected void done() {
                try {
                    String text = get();
                    scaleField.setText(Double.toString(scale));
                    textPanel.setFontSize(size);
                    textPanel.setBaseLine(maxHeight);
                    textPanel.setText(text);
                    textPanel.rightshift = rightshift;
                    statusLabel.setText("Rendering…");
                    textPanel.renderBuffer();
                    fitToWindow();
                    scrollPane.revalidate();
                    statusLabel.setText(String.format(
                        "Done — %d×%d image at scale %.1f  |  %d×%d characters",
                        width, height, scale, newWidth, newHeight));
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    cause.printStackTrace();
                    JOptionPane.showMessageDialog(frame,
                        "Error: " + cause.getMessage(),
                        "Transform Failed", JOptionPane.ERROR_MESSAGE);
                    statusLabel.setText("Failed.");
                }
                transform.setEnabled(true);
                browse.setEnabled(true);
            }
        }.execute();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                ITT = new ImageToText();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
