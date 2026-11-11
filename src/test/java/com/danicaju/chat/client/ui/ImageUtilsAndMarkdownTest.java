package com.danicaju.chat.client.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.ImageIcon;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

class ImageUtilsAndMarkdownTest {

    @Test
    @DisplayName("Recorte, redimensionado y Base64 roundtrip de avatares e imágenes")
    void testCropResizeAndRoundtrip() {
        BufferedImage wide = new BufferedImage(400, 200, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = wide.createGraphics();
        g.setColor(new Color(0x5865F2));
        g.fillRect(0, 0, 400, 200);
        g.setColor(Color.YELLOW);
        g.fillOval(150, 50, 100, 100);
        g.dispose();

        BufferedImage cropped = ImageUtils.cropAndResize(wide, 1.5, 0.25, -0.25, 128);
        assertNotNull(cropped);
        assertEquals(128, cropped.getWidth());
        assertEquals(128, cropped.getHeight());

        String b64 = ImageUtils.encodeCompressedJpegBase64(cropped, 0.85f);
        assertNotNull(b64);
        assertFalse(b64.isBlank());

        BufferedImage decoded = ImageUtils.decodeBase64(b64);
        assertNotNull(decoded);
        assertEquals(128, decoded.getWidth());
        assertEquals(128, decoded.getHeight());

        BufferedImage scaled = ImageUtils.scaleToMaxDimensions(new BufferedImage(1600, 800, BufferedImage.TYPE_INT_RGB), 800, 800);
        assertNotNull(scaled);
        assertEquals(800, scaled.getWidth());
        assertEquals(400, scaled.getHeight());

        ImageIcon customIcon = ImageUtils.buildAvatarIcon("alice", "cyber-neon", b64, 48, Color.GREEN);
        assertEquals(48, customIcon.getIconWidth());
        assertEquals(48, customIcon.getIconHeight());

        ImageIcon presetIcon = ImageUtils.buildAvatarIcon("bob", "cosmic", null, 36, Color.GRAY);
        assertEquals(36, presetIcon.getIconWidth());
        assertEquals(36, presetIcon.getIconHeight());
    }

    @Test
    @DisplayName("MarkdownRenderer formatea estilos, menciones, enlaces con $ y escapa HTML")
    void testMarkdownToHtml() {
        String html = MarkdownCodeRenderer.markdownToHtml(
                "Hola @alice **negrita** `codigo` ~~tachado~~ <script>alert(1)</script> https://example.com/$1?a=b"
        );
        assertTrue(html.contains("<b>negrita</b>"));
        assertTrue(html.contains("<code"));
        assertTrue(html.contains("<s>tachado</s>"));
        assertTrue(html.contains("@alice"));
        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;"));
        assertTrue(html.contains("href='https://example.com/$1?a=b'"));
    }
}
