package com.danicaju.chat.client.ui;

import com.danicaju.chat.service.PistonClient;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renderizador modular de Markdown enriquecido y bloques de código cercados (```lang ... ```)
 * con resaltado de sintaxis, botón Copiar al portapapeles y ejecución aislada via Piston API.
 */
public final class MarkdownCodeRenderer {

    private static final Pattern CODE_FENCE = Pattern.compile("```([a-zA-Z0-9_+.#-]*)\\s*\\R([\\s\\S]*?)```");
    private static final Pattern URL_PATTERN = Pattern.compile("(https?://[^\\s<>\"]+)");
    private static final PistonClient PISTON_CLIENT = new PistonClient();

    private MarkdownCodeRenderer() {
    }

    public static JPanel buildContentPanel(String text) {
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setOpaque(false);
        container.setAlignmentX(Component.LEFT_ALIGNMENT);

        if (text == null || text.isEmpty()) {
            return container;
        }

        List<Segment> segments = parseSegments(text);
        for (Segment seg : segments) {
            if (seg.isCode()) {
                container.add(createCodeBlockCard(seg.lang(), seg.text()));
                container.add(Box.createVerticalStrut(4));
            } else if (!seg.text().trim().isEmpty()) {
                container.add(createMarkdownTextPane(seg.text()));
                container.add(Box.createVerticalStrut(2));
            }
        }
        return container;
    }

    private static List<Segment> parseSegments(String input) {
        List<Segment> list = new ArrayList<>();
        Matcher m = CODE_FENCE.matcher(input);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) {
                list.add(new Segment(false, "", input.substring(last, m.start())));
            }
            String lang = m.group(1) != null ? m.group(1).trim() : "text";
            if (lang.isEmpty()) lang = "text";
            String code = m.group(2) != null ? m.group(2).replaceAll("\\s+$", "") : "";
            list.add(new Segment(true, lang, code));
            last = m.end();
        }
        if (last < input.length()) {
            list.add(new Segment(false, "", input.substring(last)));
        }
        return list;
    }

    private static JEditorPane createMarkdownTextPane(String raw) {
        String html = markdownToHtml(raw);
        JEditorPane pane = new JEditorPane("text/html",
                "<html><body style='margin:0;padding:0;font-family:Segoe UI,sans-serif;font-size:13px;color:#DBDEE1;background-color:#313338;'>"
                        + html + "</body></html>");
        pane.setEditable(false);
        pane.setOpaque(false);
        pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        pane.setBorder(null);
        pane.setAlignmentX(Component.LEFT_ALIGNMENT);
        pane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && e.getURL() != null) {
                try {
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().browse(e.getURL().toURI());
                    }
                } catch (Exception ignored) {
                }
            }
        });
        return pane;
    }

    public static String markdownToHtml(String text) {
        String esc = text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");

        // Inline code `...`
        esc = esc.replaceAll("`([^`]+)`",
                "<code style='background-color:#1E1F22;color:#E5E7EB;padding:1px 4px;font-family:Consolas,monospace;'>$1</code>");
        // Bold **...**
        esc = esc.replaceAll("\\*\\*([^*]+)\\*\\*", "<b>$1</b>");
        // Italic *...*
        esc = esc.replaceAll("(?<!\\*)\\*([^*]+)\\*(?!\\*)", "<i>$1</i>");
        // Strikethrough ~~...~~
        esc = esc.replaceAll("~~([^~]+)~~", "<s>$1</s>");
        // @mentions
        esc = esc.replaceAll("(@[a-zA-Z0-9._-]+)",
                "<span style='color:#949CF7;background-color:#3C4270;'><b>$1</b></span>");

        // Auto-link URLs
        Matcher urlMatcher = URL_PATTERN.matcher(esc);
        StringBuffer sb = new StringBuffer();
        while (urlMatcher.find()) {
            String u = urlMatcher.group(1);
            urlMatcher.appendReplacement(sb, Matcher.quoteReplacement("<a href='" + u + "' style='color:#00A8FC;'>" + u + "</a>"));
        }
        urlMatcher.appendTail(sb);

        return sb.toString().replace("\r\n", "\n").replace("\n", "<br/>");
    }

    private static JPanel createCodeBlockCard(String language, String code) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(new Color(0x1E1F22));
        card.setBorder(BorderFactory.createLineBorder(new Color(0x3F4147), 1));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Barra superior del bloque
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(0x2B2D31));
        header.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 8));

        JLabel langLabel = new JLabel(language.toUpperCase());
        langLabel.setFont(new Font("Consolas", Font.BOLD, 11));
        langLabel.setForeground(new Color(0x949BA4));
        header.add(langLabel, BorderLayout.WEST);

        JPanel btnGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btnGroup.setOpaque(false);

        JButton copyBtn = new JButton("Copiar");
        styleSmallCodeButton(copyBtn, new Color(0x3F4147));
        copyBtn.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(code), null);
            copyBtn.setText("Copiado");
            Timer t = new Timer(1400, ev -> copyBtn.setText("Copiar"));
            t.setRepeats(false);
            t.start();
        });
        btnGroup.add(copyBtn);

        JTextArea outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        outputArea.setBackground(new Color(0x111214));
        outputArea.setForeground(new Color(0xD1D5DB));
        outputArea.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        outputArea.setVisible(false);

        if (PistonClient.isLanguageSupported(language)) {
            JButton runBtn = new JButton("Run >");
            styleSmallCodeButton(runBtn, new Color(0x23A55A));
            runBtn.addActionListener(e -> {
                runBtn.setEnabled(false);
                runBtn.setText("Ejecutando...");
                outputArea.setVisible(true);
                outputArea.setForeground(new Color(0x949BA4));
                outputArea.setText("Ejecutando en Piston Sandbox (" + language + ")...");
                card.revalidate();

                PISTON_CLIENT.executeAsync(language, code).thenAccept(res -> SwingUtilities.invokeLater(() -> {
                    runBtn.setEnabled(true);
                    runBtn.setText("Run >");
                    StringBuilder outText = new StringBuilder();
                    outText.append("[Exit ").append(res.exitCode()).append(" - ").append(res.language()).append("]\n");
                    if (!res.stdout().isEmpty()) {
                        outText.append(res.stdout());
                    }
                    if (!res.stderr().isEmpty()) {
                        if (!res.stdout().isEmpty()) outText.append("\n--- STDERR ---\n");
                        outText.append(res.stderr());
                    }
                    if (res.stdout().isEmpty() && res.stderr().isEmpty()) {
                        outText.append("(Sin salida)");
                    }
                    outputArea.setForeground(res.success() ? new Color(0x57F287) : new Color(0xF87171));
                    outputArea.setText(outText.toString());
                    card.revalidate();
                    card.repaint();
                }));
            });
            btnGroup.add(runBtn);
        }

        header.add(btnGroup, BorderLayout.EAST);
        card.add(header, BorderLayout.NORTH);

        JTextPane codePane = new JTextPane();
        codePane.setEditable(false);
        codePane.setFont(new Font("Consolas", Font.PLAIN, 13));
        codePane.setBackground(new Color(0x1E1F22));
        codePane.setForeground(new Color(0xE5E7EB));
        codePane.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        applySyntaxHighlighting(codePane, code);

        JScrollPane scroll = new JScrollPane(codePane);
        scroll.setBorder(null);
        int lines = Math.min(18, Math.max(2, code.split("\n", -1).length));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, lines * 20 + 20));
        card.add(scroll, BorderLayout.CENTER);
        card.add(outputArea, BorderLayout.SOUTH);

        return card;
    }

    private static void applySyntaxHighlighting(JTextPane pane, String code) {
        pane.setText(code);
        StyledDocument doc = pane.getStyledDocument();
        SimpleAttributeSet base = new SimpleAttributeSet();
        StyleConstants.setForeground(base, new Color(0xE5E7EB));
        doc.setCharacterAttributes(0, code.length(), base, true);

        colorRegex(doc, code, "\\b(public|private|protected|class|interface|record|return|if|else|for|while|switch|case|def|import|from|const|let|var|function|async|await|void|int|long|double|boolean|String|print|println|console)\\b", new Color(0x949CF7));
        colorRegex(doc, code, "\\b\\d+(?:\\.\\d+)?\\b", new Color(0xFBBF24));
        colorRegex(doc, code, "\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'", new Color(0x57F287));
        colorRegex(doc, code, "(//[^\\n]*|#[^\\n]*)", new Color(0x6B7280));
    }

    private static void colorRegex(StyledDocument doc, String text, String regex, Color color) {
        SimpleAttributeSet style = new SimpleAttributeSet();
        StyleConstants.setForeground(style, color);
        Matcher m = Pattern.compile(regex).matcher(text);
        while (m.find()) {
            doc.setCharacterAttributes(m.start(), m.end() - m.start(), style, false);
        }
    }

    private static void styleSmallCodeButton(JButton btn, Color bg) {
        btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
    }

    private record Segment(boolean isCode, String lang, String text) {
    }
}
