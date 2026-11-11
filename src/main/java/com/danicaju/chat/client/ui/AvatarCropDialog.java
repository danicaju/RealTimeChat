package com.danicaju.chat.client.ui;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Modal interactivo para cargar una imagen local, redimensionar su recorte con zoom/desplazamiento
 * y previsualizar el avatar circular resultante antes de guardarlo en Base64.
 */
public class AvatarCropDialog extends JDialog {

    private final BufferedImage sourceImage;
    private BufferedImage previewCropped;
    private String resultBase64;

    private final JSlider zoomSlider;
    private final JSlider panXSlider;
    private final JSlider panYSlider;
    private final JPanel previewCanvas;

    private AvatarCropDialog(Window owner, BufferedImage sourceImage) {
        super(owner, "Ajustar y Redimensionar Avatar", ModalityType.APPLICATION_MODAL);
        this.sourceImage = sourceImage;

        setLayout(new BorderLayout(12, 12));
        getContentPane().setBackground(new Color(0x2B2D31));

        JLabel title = new JLabel("Ajusta el zoom y encuadre de tu avatar (" + sourceImage.getWidth() + "x" + sourceImage.getHeight() + " px -> 128x128 px)");
        title.setForeground(new Color(0xF2F3F5));
        title.setBorder(BorderFactory.createEmptyBorder(12, 16, 4, 16));
        add(title, BorderLayout.NORTH);

        zoomSlider = new JSlider(100, 300, 100);
        panXSlider = new JSlider(-100, 100, 0);
        panYSlider = new JSlider(-100, 100, 0);

        previewCanvas = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int cx = (getWidth() - 160) / 2;
                int cy = (getHeight() - 160) / 2;
                if (previewCropped != null) {
                    BufferedImage circle = new BufferedImage(160, 160, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D cg = circle.createGraphics();
                    cg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    cg.fill(new Ellipse2D.Double(0, 0, 160, 160));
                    cg.setComposite(AlphaComposite.SrcIn);
                    cg.drawImage(previewCropped, 0, 0, 160, 160, null);
                    cg.dispose();
                    g2.drawImage(circle, cx, cy, null);
                }
                g2.setColor(new Color(0x5865F2));
                g2.setStroke(new BasicStroke(2.5f));
                g2.draw(new Ellipse2D.Double(cx, cy, 160, 160));
                g2.dispose();
            }
        };
        previewCanvas.setPreferredSize(new Dimension(240, 190));
        previewCanvas.setBackground(new Color(0x1E1F22));

        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setBackground(new Color(0x2B2D31));
        controls.setBorder(BorderFactory.createEmptyBorder(4, 16, 4, 16));

        controls.add(makeSliderRow("Zoom / Redimensionar:", zoomSlider));
        controls.add(Box.createVerticalStrut(6));
        controls.add(makeSliderRow("Desplazar Horizontal:", panXSlider));
        controls.add(Box.createVerticalStrut(6));
        controls.add(makeSliderRow("Desplazar Vertical:", panYSlider));

        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.setBackground(new Color(0x2B2D31));
        center.add(previewCanvas, BorderLayout.NORTH);
        center.add(controls, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.setBackground(new Color(0x2B2D31));
        JButton cancelBtn = new JButton("Cancelar");
        JButton saveBtn = new JButton("Aplicar Avatar");
        saveBtn.setBackground(new Color(0x5865F2));
        saveBtn.setForeground(Color.WHITE);

        cancelBtn.addActionListener(e -> {
            resultBase64 = null;
            dispose();
        });
        saveBtn.addActionListener(e -> {
            BufferedImage final128 = ImageUtils.cropAndResize(
                    sourceImage,
                    zoomSlider.getValue() / 100.0,
                    panXSlider.getValue() / 100.0,
                    panYSlider.getValue() / 100.0,
                    128
            );
            String jpg = ImageUtils.encodeCompressedJpegBase64(final128, 0.85f);
            resultBase64 = (jpg != null && jpg.length() < 350_000) ? jpg : ImageUtils.encodeToPngBase64(final128);
            dispose();
        });

        actions.add(cancelBtn);
        actions.add(saveBtn);
        add(actions, BorderLayout.SOUTH);

        zoomSlider.addChangeListener(e -> updatePreview());
        panXSlider.addChangeListener(e -> updatePreview());
        panYSlider.addChangeListener(e -> updatePreview());

        updatePreview();
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    private JPanel makeSliderRow(String labelText, JSlider slider) {
        JPanel row = new JPanel(new BorderLayout(8, 2));
        row.setBackground(new Color(0x2B2D31));
        JLabel lbl = new JLabel(labelText);
        lbl.setForeground(new Color(0xB5BAC1));
        slider.setBackground(new Color(0x2B2D31));
        row.add(lbl, BorderLayout.NORTH);
        row.add(slider, BorderLayout.CENTER);
        return row;
    }

    private void updatePreview() {
        previewCropped = ImageUtils.cropAndResize(
                sourceImage,
                zoomSlider.getValue() / 100.0,
                panXSlider.getValue() / 100.0,
                panYSlider.getValue() / 100.0,
                160
        );
        previewCanvas.repaint();
    }

    /**
     * Abre JFileChooser + modal de recorte/redimensionado y retorna Base64 listo para UserProfile.
     */
    public static String chooseAndCropAvatar(Window owner) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Selecciona una imagen para tu avatar");
        chooser.setFileFilter(new FileNameExtensionFilter("Imágenes (PNG, JPG, GIF, BMP)", "png", "jpg", "jpeg", "gif", "bmp"));
        if (chooser.showOpenDialog(owner) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        File file = chooser.getSelectedFile();
        try {
            BufferedImage img = ImageIO.read(file);
            if (img == null) {
                JOptionPane.showMessageDialog(owner, "No se pudo leer el archivo seleccionado como imagen.",
                        "Formato no soportado", JOptionPane.WARNING_MESSAGE);
                return null;
            }
            AvatarCropDialog dialog = new AvatarCropDialog(owner, img);
            dialog.setVisible(true);
            return dialog.resultBase64;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(owner, "Error leyendo imagen: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }
}
