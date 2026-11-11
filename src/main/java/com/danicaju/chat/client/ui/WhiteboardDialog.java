package com.danicaju.chat.client.ui;

import com.danicaju.chat.client.ChatClientConnection;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/**
 * Pizarra Colaborativa en tiempo real por canal.
 * Sincroniza trazos vectoriales segmentados y borrado entre usuarios conectados al canal.
 */
public class WhiteboardDialog extends JDialog {

    private static final int CANVAS_W = 820;
    private static final int CANVAS_H = 480;
    private static final Color CANVAS_BG = new Color(0x1E1F22);

    private final ChatClientConnection connection;
    private String activeChannel;
    private final BufferedImage canvasImage;
    private final JPanel drawSurface;
    private final JLabel statusLabel;

    private Color brushColor = new Color(0x5865F2);
    private int brushSize = 4;
    private Point lastPoint;

    public WhiteboardDialog(Window owner, ChatClientConnection connection, String channel) {
        super(owner, "Pizarra Colaborativa - " + channel, ModalityType.MODELESS);
        this.connection = connection;
        this.activeChannel = channel;

        canvasImage = new BufferedImage(CANVAS_W, CANVAS_H, BufferedImage.TYPE_INT_RGB);
        clearCanvasLocal();

        setLayout(new BorderLayout());
        getContentPane().setBackground(new Color(0x2B2D31));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        toolbar.setBackground(new Color(0x2B2D31));
        for (Color c : new Color[]{Color.WHITE, new Color(0x5865F2), new Color(0x23A55A), new Color(0xF0B232), new Color(0xF23F43), new Color(0xEB459E)}) {
            JButton swatch = new JButton("  ");
            swatch.setBackground(c);
            swatch.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
            swatch.addActionListener(e -> brushColor = c);
            toolbar.add(swatch);
        }

        JSlider sizeSlider = new JSlider(2, 16, 4);
        sizeSlider.setPreferredSize(new Dimension(110, 24));
        sizeSlider.setBackground(new Color(0x2B2D31));
        sizeSlider.addChangeListener(e -> brushSize = sizeSlider.getValue());
        JLabel sizeLbl = new JLabel("Grosor:");
        sizeLbl.setForeground(new Color(0xDBDEE1));
        toolbar.add(sizeLbl);
        toolbar.add(sizeSlider);

        JButton clearBtn = new JButton("Limpiar Pizarra");
        clearBtn.setBackground(new Color(0xF23F43));
        clearBtn.setForeground(Color.WHITE);
        clearBtn.addActionListener(e -> {
            clearCanvasLocal();
            connection.sendWhiteboardEvent(activeChannel, "CLEAR");
        });
        toolbar.add(clearBtn);

        statusLabel = new JLabel("Dibuja libremente; todos en " + channel + " verán los trazos en vivo.");
        statusLabel.setForeground(new Color(0x949BA4));
        toolbar.add(statusLabel);

        drawSurface = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.drawImage(canvasImage, 0, 0, null);
            }
        };
        drawSurface.setPreferredSize(new Dimension(CANVAS_W, CANVAS_H));

        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                lastPoint = e.getPoint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                Point next = e.getPoint();
                if (lastPoint != null) {
                    drawSegmentLocal(lastPoint.x, lastPoint.y, next.x, next.y, brushColor, brushSize);
                    String hex = String.format("#%02X%02X%02X", brushColor.getRed(), brushColor.getGreen(), brushColor.getBlue());
                    connection.sendWhiteboardEvent(activeChannel,
                            "S|" + lastPoint.x + "|" + lastPoint.y + "|" + next.x + "|" + next.y + "|" + hex + "|" + brushSize);
                }
                lastPoint = next;
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                lastPoint = null;
            }
        };
        drawSurface.addMouseListener(adapter);
        drawSurface.addMouseMotionListener(adapter);

        add(toolbar, BorderLayout.NORTH);
        add(drawSurface, BorderLayout.CENTER);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    public void setActiveChannel(String channel) {
        this.activeChannel = channel;
        setTitle("Pizarra Colaborativa - " + channel);
    }

    public void handleRemoteEvent(String sender, String payload) {
        if (payload == null) return;
        SwingUtilities.invokeLater(() -> {
            if ("CLEAR".equalsIgnoreCase(payload)) {
                clearCanvasLocal();
                statusLabel.setText("Pizarra limpiada por @" + sender);
                return;
            }
            if (payload.startsWith("S|")) {
                String[] parts = payload.split("\\|");
                if (parts.length >= 7) {
                    try {
                        int x1 = Integer.parseInt(parts[1]);
                        int y1 = Integer.parseInt(parts[2]);
                        int x2 = Integer.parseInt(parts[3]);
                        int y2 = Integer.parseInt(parts[4]);
                        Color col = ImageUtils.parseColor(parts[5], Color.WHITE);
                        int width = Integer.parseInt(parts[6]);
                        drawSegmentLocal(x1, y1, x2, y2, col, width);
                        statusLabel.setText("Dibujando: @" + sender);
                    } catch (Exception ignored) {
                    }
                }
            }
        });
    }

    private void drawSegmentLocal(int x1, int y1, int x2, int y2, Color color, int width) {
        Graphics2D g2 = canvasImage.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(color);
        g2.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(x1, y1, x2, y2);
        g2.dispose();
        drawSurface.repaint();
    }

    private void clearCanvasLocal() {
        Graphics2D g2 = canvasImage.createGraphics();
        g2.setColor(CANVAS_BG);
        g2.fillRect(0, 0, CANVAS_W, CANVAS_H);
        g2.dispose();
        if (drawSurface != null) drawSurface.repaint();
    }
}
