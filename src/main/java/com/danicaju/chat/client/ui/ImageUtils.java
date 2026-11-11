package com.danicaju.chat.client.ui;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utilidades de procesamiento, recorte, redimensionado, compresión y caché en memoria
 * para avatares personalizados e imágenes pegadas del portapapeles.
 */
public final class ImageUtils {

    private static final Map<String, BufferedImage> IMAGE_CACHE =
            Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
                    return size() > 64;
                }
            });

    private ImageUtils() {
    }

    public static BufferedImage decodeBase64(String base64) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        String key = base64.length() > 64 ? base64.substring(0, 64) + "_" + base64.hashCode() : base64;
        BufferedImage cached = IMAGE_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            String clean = base64.contains(",") ? base64.substring(base64.indexOf(',') + 1) : base64;
            byte[] bytes = Base64.getDecoder().decode(clean);
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img != null) {
                IMAGE_CACHE.put(key, img);
            }
            return img;
        } catch (Exception e) {
            return null;
        }
    }

    public static String encodeToPngBase64(BufferedImage img) {
        if (img == null) return null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            return null;
        }
    }

    public static String encodeCompressedJpegBase64(BufferedImage img, float quality) {
        if (img == null) return null;
        try {
            BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = rgb.createGraphics();
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g2.drawImage(img, 0, 0, null);
            g2.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            var writers = ImageIO.getImageWritersByFormatName("jpg");
            if (writers.hasNext()) {
                var writer = writers.next();
                var param = writer.getDefaultWriteParam();
                param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
                try (var ios = ImageIO.createImageOutputStream(baos)) {
                    writer.setOutput(ios);
                    writer.write(null, new javax.imageio.IIOImage(rgb, null, null), param);
                }
                writer.dispose();
                return Base64.getEncoder().encodeToString(baos.toByteArray());
            }
            ImageIO.write(rgb, "jpg", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            return encodeToPngBase64(img);
        }
    }

    /**
     * Recorta con zoom y offset normalizado [-1..1] y redimensiona al tamaño objetivo cuadrado.
     */
    public static BufferedImage cropAndResize(BufferedImage src, double zoom, double offsetXNorm, double offsetYNorm, int targetSize) {
        if (src == null) return null;
        int w = src.getWidth();
        int h = src.getHeight();
        int baseSide = Math.min(w, h);
        double safeZoom = Math.max(1.0, Math.min(4.0, zoom));
        int cropSide = Math.max(16, (int) Math.round(baseSide / safeZoom));

        double maxShiftX = Math.max(0, (w - cropSide) / 2.0);
        double maxShiftY = Math.max(0, (h - cropSide) / 2.0);
        int cx = (int) Math.round((w - cropSide) / 2.0 + Math.max(-1.0, Math.min(1.0, offsetXNorm)) * maxShiftX);
        int cy = (int) Math.round((h - cropSide) / 2.0 + Math.max(-1.0, Math.min(1.0, offsetYNorm)) * maxShiftY);
        cx = Math.max(0, Math.min(w - cropSide, cx));
        cy = Math.max(0, Math.min(h - cropSide, cy));

        BufferedImage sub = src.getSubimage(cx, cy, cropSide, cropSide);
        BufferedImage resized = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = resized.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(sub, 0, 0, targetSize, targetSize, null);
        g2.dispose();
        return resized;
    }

    /**
     * Redimensiona imágenes grandes del portapapeles manteniendo proporción antes de subir.
     */
    public static BufferedImage scaleToMaxDimensions(Image src, int maxWidth, int maxHeight) {
        int w = src.getWidth(null);
        int h = src.getHeight(null);
        if (w <= 0 || h <= 0) return null;
        double scale = Math.min(1.0, Math.min((double) maxWidth / w, (double) maxHeight / h));
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));

        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, nw, nh);
        g2.drawImage(src, 0, 0, nw, nh, null);
        g2.dispose();
        return out;
    }

    /**
     * Genera un avatar circular de alta nitidez (imagen custom o preset vectorial).
     */
    public static ImageIcon buildAvatarIcon(String username, String avatarPreset, String customBase64, int size, Color ringColor) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        BufferedImage custom = decodeBase64(customBase64);
        if (custom != null) {
            BufferedImage circle = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D cg = circle.createGraphics();
            cg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            cg.fill(new Ellipse2D.Double(0, 0, size, size));
            cg.setComposite(AlphaComposite.SrcIn);
            cg.drawImage(custom, 0, 0, size, size, null);
            cg.dispose();
            g2.drawImage(circle, 0, 0, null);
        } else {
            Color c1 = resolveAvatarPrimary(avatarPreset);
            Color c2 = resolveAvatarSecondary(avatarPreset);
            g2.setPaint(new GradientPaint(0, 0, c1, size, size, c2));
            g2.fill(new Ellipse2D.Double(0, 0, size, size));

            String letter = (username != null && !username.isEmpty())
                    ? username.substring(0, 1).toUpperCase() : "?";
            g2.setColor(Color.WHITE);
            g2.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, Math.max(11, size / 2)));
            var fm = g2.getFontMetrics();
            int tx = (size - fm.stringWidth(letter)) / 2;
            int ty = (size - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(letter, tx, ty);
        }

        if (ringColor != null) {
            g2.setColor(ringColor);
            g2.setStroke(new BasicStroke(Math.max(1.5f, size / 18f)));
            g2.draw(new Ellipse2D.Double(1, 1, size - 2.0, size - 2.0));
        }
        g2.dispose();
        return new ImageIcon(img);
    }

    public static Color resolveAvatarPrimary(String preset) {
        if (preset == null) return new Color(0x5865F2);
        return switch (preset) {
            case "synthwave" -> new Color(0xEB459E);
            case "cosmic" -> new Color(0x7C3AED);
            case "emerald" -> new Color(0x23A55A);
            case "sunset" -> new Color(0xF97316);
            case "bot-matrix" -> new Color(0x10B981);
            case "golden" -> new Color(0xF0B232);
            case "retro-pixel" -> new Color(0xEC4899);
            default -> new Color(0x5865F2);
        };
    }

    public static Color resolveAvatarSecondary(String preset) {
        if (preset == null) return new Color(0x3B45C4);
        return switch (preset) {
            case "synthwave" -> new Color(0x7C3AED);
            case "cosmic" -> new Color(0x2563EB);
            case "emerald" -> new Color(0x0D9488);
            case "sunset" -> new Color(0xEF4444);
            case "bot-matrix" -> new Color(0x064E3B);
            case "golden" -> new Color(0xD97706);
            case "retro-pixel" -> new Color(0x6366F1);
            default -> new Color(0x3B82F6);
        };
    }

    public static Color parseColor(String hex, Color fallback) {
        if (hex == null || hex.isBlank()) return fallback;
        try {
            if (hex.startsWith("gradient-cyber")) return new Color(0x5865F2);
            if (hex.startsWith("gradient-sunset")) return new Color(0xF97316);
            if (hex.startsWith("gradient-cosmic")) return new Color(0x7C3AED);
            if (hex.startsWith("gradient-emerald")) return new Color(0x23A55A);
            String clean = hex.startsWith("#") ? hex.substring(1) : hex;
            return new Color(Integer.parseInt(clean, 16));
        } catch (Exception e) {
            return fallback;
        }
    }
}
