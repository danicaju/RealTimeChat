package com.danicaju.chat.client;

import com.danicaju.chat.model.Message;
import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Cliente gráfico de escritorio para RealTimeChat con Look & Feel FlatLaf Dark,
 * diseño moderno inspirado en clientes de mensajería contemporáneos (Discord/Slack),
 * panel de usuarios online en tiempo real con badges y visualización enriquecida.
 */
public class ChatGuiClient extends JFrame implements ChatEventListener {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final ChatClientConnection connection;
    private final DefaultListModel<String> userListModel;
    private JList<String> userList;
    private JTextPane chatPane;
    private HTMLDocument htmlDoc;
    private HTMLEditorKit editorKit;
    private JTextField inputField;
    private JButton sendButton;
    private JLabel statusLabel;
    private JLabel userCountLabel;

    private String currentServerHost = "127.0.0.1";
    private int currentServerPort = 5000;
    private String currentUsername = "";

    public ChatGuiClient() {
        super("RealTimeChat");
        this.connection = new ChatClientConnection();
        this.userListModel = new DefaultListModel<>();

        initComponents();
        setupWindowEvents();
    }

    private void initComponents() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 680);
        setMinimumSize(new Dimension(750, 480));
        setLocationRelativeTo(null);

        JPanel rootPanel = new JPanel(new BorderLayout());
        rootPanel.setBorder(new EmptyBorder(0, 0, 0, 0));

        // 1. Barra superior (Header)
        JPanel headerPanel = createHeaderPanel();
        rootPanel.add(headerPanel, BorderLayout.NORTH);

        // 2. Barra lateral izquierda (Usuarios en línea)
        JPanel sidebarPanel = createSidebarPanel();

        // 3. Área central de mensajes
        JPanel chatPanel = createChatPanel();

        // SplitPane divisor moderno entre sidebar y chat
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebarPanel, chatPanel);
        splitPane.setDividerLocation(230);
        splitPane.setDividerSize(4);
        splitPane.setContinuousLayout(true);
        splitPane.setBorder(null);
        rootPanel.add(splitPane, BorderLayout.CENTER);

        // 4. Barra inferior de envío
        JPanel bottomPanel = createBottomInputPanel();
        rootPanel.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(rootPanel);
    }

    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(new EmptyBorder(10, 16, 10, 16));
        header.setBackground(new Color(30, 31, 35));

        // Título de la app
        JLabel appTitle = new JLabel("RealTimeChat");
        appTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        appTitle.setForeground(new Color(242, 243, 245));

        // Panel derecho de estado y desconexión
        JPanel rightStatusPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        rightStatusPanel.setOpaque(false);

        statusLabel = new JLabel("● Desconectado");
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        statusLabel.setForeground(new Color(237, 66, 69)); // Rojo

        JButton reconnectBtn = new JButton("Conectar / Cambiar");
        reconnectBtn.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT);
        reconnectBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        reconnectBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        reconnectBtn.addActionListener(e -> showConnectionDialog());

        rightStatusPanel.add(statusLabel);
        rightStatusPanel.add(reconnectBtn);

        header.add(appTitle, BorderLayout.WEST);
        header.add(rightStatusPanel, BorderLayout.EAST);
        return header;
    }

    private JPanel createSidebarPanel() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(new Color(43, 45, 49));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(53, 55, 60)));

        // Encabezado de la lista de usuarios
        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setBackground(new Color(43, 45, 49));
        titlePanel.setBorder(new EmptyBorder(12, 14, 8, 14));

        userCountLabel = new JLabel("USUARIOS EN LÍNEA (0)");
        userCountLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        userCountLabel.setForeground(new Color(148, 155, 164));
        titlePanel.add(userCountLabel, BorderLayout.WEST);
        sidebar.add(titlePanel, BorderLayout.NORTH);

        // Lista de usuarios con renderizado de avatar y badge de estado online
        userList = new JList<>(userListModel);
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userList.setBackground(new Color(43, 45, 49));
        userList.setCellRenderer(new UserListCellRenderer());
        userList.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Doble clic en un usuario prepara comando privado
        userList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String selected = userList.getSelectedValue();
                    if (selected != null && !selected.equalsIgnoreCase(currentUsername)) {
                        inputField.setText("/w " + selected + " ");
                        inputField.requestFocusInWindow();
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(userList);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(new Color(43, 45, 49));
        sidebar.add(scrollPane, BorderLayout.CENTER);

        return sidebar;
    }

    private JPanel createChatPanel() {
        JPanel chatPanel = new JPanel(new BorderLayout());
        chatPanel.setBackground(new Color(49, 51, 56));

        chatPane = new JTextPane();
        chatPane.setEditable(false);
        chatPane.setContentType("text/html");
        chatPane.setBackground(new Color(49, 51, 56));

        editorKit = new HTMLEditorKit();
        chatPane.setEditorKit(editorKit);
        htmlDoc = (HTMLDocument) editorKit.createDefaultDocument();
        chatPane.setDocument(htmlDoc);

        // Estilos CSS integrados
        String baseCss = "<style>" +
                "body { font-family: 'Segoe UI', Tahoma, Arial, sans-serif; font-size: 13px; color: #dbdee1; margin: 10px; background-color: #313338; }" +
                ".msg-row { margin-bottom: 8px; line-height: 1.4; }" +
                ".timestamp { color: #949ba4; font-size: 11px; margin-right: 6px; font-weight: normal; }" +
                ".sender-other { color: #5865f2; font-weight: bold; font-size: 13px; }" +
                ".sender-me { color: #57f287; font-weight: bold; font-size: 13px; }" +
                ".system-msg { color: #f0b232; font-style: italic; background-color: rgba(240, 178, 50, 0.1); padding: 4px 8px; border-radius: 4px; display: inline-block; }" +
                ".private-msg { color: #eb459e; background-color: rgba(235, 69, 158, 0.12); padding: 6px 10px; border-left: 3px solid #eb459e; border-radius: 4px; }" +
                ".body-text { color: #dbdee1; word-wrap: break-word; }" +
                "</style><div id='chat-flow'></div>";
        try {
            editorKit.insertHTML(htmlDoc, 0, baseCss, 0, 0, null);
        } catch (Exception ignored) {
        }

        JScrollPane scrollPane = new JScrollPane(chatPane);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        chatPanel.add(scrollPane, BorderLayout.CENTER);

        return chatPanel;
    }

    private JPanel createBottomInputPanel() {
        JPanel bottomPanel = new JPanel(new BorderLayout(8, 0));
        bottomPanel.setBorder(new EmptyBorder(12, 16, 12, 16));
        bottomPanel.setBackground(new Color(43, 45, 49));

        inputField = new JTextField();
        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        inputField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Escribe un mensaje o /w <usuario> <mensaje>...");
        inputField.putClientProperty(FlatClientProperties.STYLE, "arc: 12; margin: 6,10,6,10;");
        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    sendMessageFromInput();
                }
            }
        });

        sendButton = new JButton("Enviar");
        sendButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        sendButton.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT);
        sendButton.putClientProperty(FlatClientProperties.STYLE, "background: #5865f2; foreground: #ffffff; arc: 12;");
        sendButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        sendButton.setPreferredSize(new Dimension(85, 36));
        sendButton.addActionListener(e -> sendMessageFromInput());

        bottomPanel.add(inputField, BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);
        return bottomPanel;
    }

    private void setupWindowEvents() {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (connection.isConnected()) {
                    connection.disconnect();
                }
            }
        });
    }

    private void sendMessageFromInput() {
        String text = inputField.getText().trim();
        if (text.isEmpty() || !connection.isConnected()) {
            return;
        }

        if (text.startsWith("/w ") || text.startsWith("/msg ")) {
            String[] parts = text.split("\\s+", 3);
            if (parts.length >= 3) {
                connection.sendPrivateMessage(parts[1], parts[2]);
                inputField.setText("");
            } else {
                appendSystemHtml("Uso: /w &lt;usuario&gt; &lt;mensaje&gt;");
            }
            return;
        }

        if (text.equalsIgnoreCase("/list") || text.equalsIgnoreCase("/usuarios")) {
            connection.requestUserList();
            inputField.setText("");
            return;
        }

        if (text.equalsIgnoreCase("/quit") || text.equalsIgnoreCase("/salir")) {
            connection.disconnect();
            inputField.setText("");
            return;
        }

        connection.sendChatMessage(text);
        inputField.setText("");
    }

    /**
     * Muestra el diálogo modal para configurar IP, puerto y nickname.
     */
    public void showConnectionDialog() {
        JDialog dialog = new JDialog(this, "Conexión a RealTimeChat", true);
        dialog.setSize(380, 280);
        dialog.setLocationRelativeTo(this);
        dialog.setResizable(false);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(20, 24, 20, 24));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 4, 6, 4);

        JTextField hostField = new JTextField(currentServerHost);
        JTextField portField = new JTextField(String.valueOf(currentServerPort));
        JTextField userField = new JTextField(currentUsername.isEmpty() ? "User" + (int) (Math.random() * 900 + 100) : currentUsername);
        JLabel errorLabel = new JLabel(" ");
        errorLabel.setForeground(new Color(237, 66, 69));
        errorLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.3;
        panel.add(new JLabel("Servidor IP:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.7;
        panel.add(hostField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.3;
        panel.add(new JLabel("Puerto:"), gbc);
        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 0.7;
        panel.add(portField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.3;
        panel.add(new JLabel("Nickname:"), gbc);
        gbc.gridx = 1; gbc.gridy = 2; gbc.weightx = 0.7;
        panel.add(userField, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        panel.add(errorLabel, gbc);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton cancelBtn = new JButton("Cancelar");
        JButton connectBtn = new JButton("Conectar");
        connectBtn.putClientProperty(FlatClientProperties.STYLE, "background: #5865f2; foreground: #ffffff;");

        cancelBtn.addActionListener(e -> dialog.dispose());
        connectBtn.addActionListener(e -> {
            String host = hostField.getText().trim();
            String portStr = portField.getText().trim();
            String user = userField.getText().trim();

            if (host.isEmpty() || portStr.isEmpty() || user.isEmpty()) {
                errorLabel.setText("Todos los campos son obligatorios.");
                return;
            }

            int port;
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException ex) {
                errorLabel.setText("El puerto debe ser numérico.");
                return;
            }

            errorLabel.setText("Conectando...");
            connectBtn.setEnabled(false);

            new Thread(() -> {
                boolean ok = connection.connect(host, port, user, this);
                SwingUtilities.invokeLater(() -> {
                    connectBtn.setEnabled(true);
                    if (ok) {
                        currentServerHost = host;
                        currentServerPort = port;
                        currentUsername = user;
                        dialog.dispose();
                    }
                });
            }).start();
        });

        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        panel.add(btnPanel, gbc);
        btnPanel.add(cancelBtn);
        btnPanel.add(connectBtn);

        dialog.setContentPane(panel);
        dialog.setVisible(true);
    }

    private void appendHtml(String htmlSnippet) {
        SwingUtilities.invokeLater(() -> {
            try {
                editorKit.insertHTML(htmlDoc, htmlDoc.getLength(), htmlSnippet, 0, 0, null);
                chatPane.setCaretPosition(htmlDoc.getLength());
            } catch (Exception e) {
                System.err.println("Error insertando HTML: " + e.getMessage());
            }
        });
    }

    private void appendSystemHtml(String text) {
        String time = TIME_FORMATTER.format(Instant.now());
        String html = "<div class='msg-row'><span class='timestamp'>[" + time + "]</span> " +
                "<span class='system-msg'>ℹ️ " + escapeHtml(text) + "</span></div>";
        appendHtml(html);
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    // --- Implementación de ChatEventListener ---

    @Override
    public void onConnected(String username) {
        SwingUtilities.invokeLater(() -> {
            this.currentUsername = username;
            statusLabel.setText("● En línea: " + username);
            statusLabel.setForeground(new Color(87, 242, 135)); // Verde Discord
            appendSystemHtml("¡Conexión establecida como '" + username + "'!");
            inputField.requestFocusInWindow();
        });
    }

    @Override
    public void onMessageReceived(Message message) {
        if (message == null || message.getType() == null) {
            return;
        }

        String time = TIME_FORMATTER.format(Instant.ofEpochMilli(message.getTimestamp()));
        switch (message.getType()) {
            case CHAT -> {
                String sender = message.getSender();
                boolean isMe = currentUsername != null && currentUsername.equalsIgnoreCase(sender);
                String senderClass = isMe ? "sender-me" : "sender-other";
                String displayName = isMe ? "Tú" : escapeHtml(sender);
                String html = "<div class='msg-row'><span class='timestamp'>[" + time + "]</span> " +
                        "<span class='" + senderClass + "'>" + displayName + ":</span> " +
                        "<span class='body-text'>" + escapeHtml(message.getContent()) + "</span></div>";
                appendHtml(html);
            }
            case PRIVATE -> {
                String sender = message.getSender();
                String target = message.getTarget();
                boolean isMe = currentUsername != null && currentUsername.equalsIgnoreCase(sender);
                String label = isMe ? "🔒 Privado para @" + escapeHtml(target) : "🔒 Privado de @" + escapeHtml(sender);
                String html = "<div class='msg-row'><div class='private-msg'><span class='timestamp'>[" + time + "]</span> " +
                        "<b>" + label + ":</b> " +
                        "<span class='body-text'>" + escapeHtml(message.getContent()) + "</span></div></div>";
                appendHtml(html);
            }
            case SYSTEM -> {
                appendSystemHtml(message.getContent());
            }
            case LEAVE -> {
                appendSystemHtml("El usuario '" + message.getSender() + "' se ha desconectado.");
            }
            default -> {
            }
        }
    }

    @Override
    public void onUserListUpdated(List<String> users) {
        SwingUtilities.invokeLater(() -> {
            userListModel.clear();
            for (String u : users) {
                userListModel.addElement(u);
            }
            userCountLabel.setText("USUARIOS EN LÍNEA (" + users.size() + ")");
        });
    }

    @Override
    public void onDisconnected(String reason) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("● Desconectado");
            statusLabel.setForeground(new Color(237, 66, 69));
            userListModel.clear();
            userCountLabel.setText("USUARIOS EN LÍNEA (0)");
            appendSystemHtml("Desconectado del servidor: " + reason);
        });
    }

    @Override
    public void onError(String errorMessage) {
        SwingUtilities.invokeLater(() -> {
            appendSystemHtml("ERROR: " + errorMessage);
            JOptionPane.showMessageDialog(this, errorMessage, "Aviso de RealTimeChat", JOptionPane.WARNING_MESSAGE);
        });
    }

    /**
     * Renderizador personalizado para los elementos de la lista de usuarios.
     * Dibuja un badge verde de estado online al lado del nickname.
     */
    private class UserListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            label.setBorder(new EmptyBorder(7, 12, 7, 12));
            label.setFont(new Font("Segoe UI", Font.PLAIN, 13));

            String user = value != null ? value.toString() : "";
            boolean isMe = currentUsername != null && currentUsername.equalsIgnoreCase(user);
            label.setText(user + (isMe ? " (Tú)" : ""));

            if (isSelected) {
                label.setBackground(new Color(53, 55, 60));
                label.setForeground(Color.WHITE);
            } else {
                label.setBackground(new Color(43, 45, 49));
                label.setForeground(new Color(219, 222, 225));
            }

            label.setIcon(new OnlineBadgeIcon());
            return label;
        }
    }

    /**
     * Icono vectorial ligero para representar el badge verde de usuario conectado.
     */
    private static class OnlineBadgeIcon implements javax.swing.Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(87, 242, 135)); // Verde online Discord
            g2.fillOval(x, y + 2, 8, 8);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 12;
        }

        @Override
        public int getIconHeight() {
            return 12;
        }
    }

    public static void main(String[] args) {
        FlatDarkLaf.setup();
        SwingUtilities.invokeLater(() -> {
            ChatGuiClient gui = new ChatGuiClient();
            gui.setVisible(true);
            gui.showConnectionDialog();
        });
    }
}
