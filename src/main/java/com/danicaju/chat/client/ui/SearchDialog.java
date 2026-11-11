package com.danicaju.chat.client.ui;

import com.danicaju.chat.client.ChatClientConnection;
import com.danicaju.chat.model.Message;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

/**
 * Buscador avanzado con soporte para filtros parseados:
 * from:usuario, in:canal, has:link, has:file y texto libre.
 */
public class SearchDialog extends JDialog {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM HH:mm")
            .withZone(ZoneId.systemDefault());

    private final JTextField queryField;
    private final JPanel resultsContainer;
    private final JLabel statusLabel;
    private Consumer<Message> onResultJump;

    public SearchDialog(Window owner, ChatClientConnection connection, Consumer<Message> onResultJump) {
        super(owner, "Buscador Avanzado de Mensajes", ModalityType.MODELESS);
        this.onResultJump = onResultJump;

        setLayout(new BorderLayout(8, 8));
        getContentPane().setBackground(new Color(0x2B2D31));
        setSize(580, 480);
        setLocationRelativeTo(owner);

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBackground(new Color(0x2B2D31));
        top.setBorder(BorderFactory.createEmptyBorder(12, 12, 4, 12));

        JPanel searchBar = new JPanel(new BorderLayout(8, 0));
        searchBar.setOpaque(false);
        queryField = new JTextField();
        queryField.setBackground(new Color(0x1E1F22));
        queryField.setForeground(Color.WHITE);
        queryField.setCaretColor(Color.WHITE);
        queryField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        queryField.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        statusLabel = new JLabel("Ejemplos: from:alice in:ia-computacion has:link");
        statusLabel.setForeground(new Color(0x949BA4));
        statusLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));

        JButton searchBtn = new JButton("Buscar");
        searchBtn.setBackground(new Color(0x5865F2));
        searchBtn.setForeground(Color.WHITE);
        Runnable runSearch = () -> {
            statusLabel.setText("Buscando en H2...");
            connection.searchMessages(queryField.getText().trim());
        };
        searchBtn.addActionListener(e -> runSearch.run());
        queryField.addActionListener(e -> runSearch.run());

        searchBar.add(queryField, BorderLayout.CENTER);
        searchBar.add(searchBtn, BorderLayout.EAST);

        JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        chips.setOpaque(false);
        for (String chip : new String[]{"from:", "in:general", "has:link", "has:file"}) {
            JButton b = new JButton("+ " + chip);
            b.setFont(new Font("Consolas", Font.PLAIN, 11));
            b.setBackground(new Color(0x1E1F22));
            b.setForeground(new Color(0x949CF7));
            b.setFocusPainted(false);
            b.addActionListener(e -> {
                String cur = queryField.getText().trim();
                queryField.setText(cur.isEmpty() ? chip : cur + " " + chip);
                queryField.requestFocusInWindow();
            });
            chips.add(b);
        }

        top.add(searchBar);
        top.add(chips);
        top.add(statusLabel);
        add(top, BorderLayout.NORTH);

        resultsContainer = new JPanel();
        resultsContainer.setLayout(new BoxLayout(resultsContainer, BoxLayout.Y_AXIS));
        resultsContainer.setBackground(new Color(0x313338));
        JScrollPane scroll = new JScrollPane(resultsContainer);
        scroll.setBorder(null);
        add(scroll, BorderLayout.CENTER);
    }

    public void setInitialQuery(String query) {
        if (query != null) queryField.setText(query);
    }

    public void updateResults(String query, List<Message> results) {
        resultsContainer.removeAll();
        int count = results != null ? results.size() : 0;
        statusLabel.setText("Resultados para '" + query + "': " + count + " coincidencias (clic para ir al mensaje)");

        if (results != null) {
            for (Message m : results) {
                JPanel item = new JPanel(new BorderLayout(6, 4));
                item.setBackground(new Color(0x2B2D31));
                item.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0x1E1F22)),
                        BorderFactory.createEmptyBorder(8, 10, 8, 10)
                ));
                item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 84));
                item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

                String headerText = "@" + m.getSender() + "  en "
                        + (m.getChannel() != null ? m.getChannel() : "DM")
                        + "  [" + DATE_FMT.format(Instant.ofEpochMilli(m.getTimestamp())) + "]";
                JLabel h = new JLabel(headerText);
                h.setFont(new Font("Segoe UI", Font.BOLD, 12));
                h.setForeground(new Color(0x949CF7));

                String snippet = m.getContent() != null ? m.getContent() : "";
                if (m.getEmbed() != null && m.getEmbed().getTitle() != null) {
                    snippet += " | [Embed: " + m.getEmbed().getTitle() + "]";
                }
                if (m.getAttachmentName() != null) {
                    snippet += " [Archivo: " + m.getAttachmentName() + "]";
                }
                JLabel body = new JLabel(snippet.length() > 110 ? snippet.substring(0, 107) + "..." : snippet);
                body.setForeground(new Color(0xDBDEE1));

                item.add(h, BorderLayout.NORTH);
                item.add(body, BorderLayout.CENTER);
                item.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        if (onResultJump != null) onResultJump.accept(m);
                    }
                });
                resultsContainer.add(item);
                resultsContainer.add(Box.createVerticalStrut(4));
            }
        }
        resultsContainer.revalidate();
        resultsContainer.repaint();
    }
}
