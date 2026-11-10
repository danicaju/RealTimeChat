package com.danicaju.chat.client;

import com.danicaju.chat.model.Message;
import com.danicaju.chat.model.UserProfile;
import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
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
import java.awt.GradientPaint;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cliente gráfico de escritorio para RealTimeChat NextGen con Look & Feel FlatLaf Dark,
 * soporte para múltiples canales (#general, #ia-computacion, #random),
 * personalización integral de perfiles de usuario (avatar, bio, banner),
 * indicador de escritura en tiempo real (typing indicator),
 * búfer de reproducción de historial y asistente inteligente AI (@bot).
 */
public class ChatGuiClient extends JFrame implements ChatEventListener {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public static final String[] AVATAR_PRESETS = {
            "cyber-neon", "synthwave", "cosmic", "emerald", "sunset", "bot-matrix", "retro-pixel", "golden"
    };

    public static final String[] BANNER_PRESETS = {
            "#5865F2", "#57F287", "#EB459E", "#FEE75C", "#ED4245", "gradient-cyber", "gradient-sunset", "gradient-cosmic"
    };

    private final ChatClientConnection connection;
    private final DefaultListModel<String> userListModel;
    private final DefaultListModel<String> channelListModel;
    private JList<String> userList;
    private JList<String> channelList;
    private JTextPane chatPane;
    private HTMLDocument htmlDoc;
    private HTMLEditorKit editorKit;
    private JTextField inputField;
    private JButton sendButton;
    private JLabel statusLabel;
    private JLabel channelTitleLabel;
    private JLabel userCountLabel;
    private JLabel typingIndicatorLabel;

    private String currentServerHost = "127.0.0.1";
    private int currentServerPort = 5000;
    private String currentUsername = "";
    private UserProfile myProfile;

    // Caché de perfiles consultados
    private final Map<String, UserProfile> profileCache = new ConcurrentHashMap<>();

    // Manejo del estado de typing
    private final Set<String> activeTypingUsers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private Timer typingStopTimer;
    private boolean isUserTyping = false;

    private JDialog activeDialog;
    private JDialog currentProfileDialog;
    private String currentProfileDialogUser;
    private JLabel currentProfileAvatarLabel;
    private ProfileBannerPanel currentProfileBannerPanel;
    private JTextArea currentProfileBioArea;

    public ChatGuiClient() {
        super("RealTimeChat NextGen");
        this.connection = new ChatClientConnection();
        this.userListModel = new DefaultListModel<>();
        this.channelListModel = new DefaultListModel<>();
        this.myProfile = new UserProfile();

        // Canales por defecto iniciales
        channelListModel.addElement("#general");
        channelListModel.addElement("#ia-computacion");
        channelListModel.addElement("#random");

        initComponents();
        setupWindowEvents();
        setupTypingDetection();
    }

    private void initComponents() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1080, 720);
        setMinimumSize(new Dimension(800, 520));
        setLocationRelativeTo(null);

        JPanel rootPanel = new JPanel(new BorderLayout());
        rootPanel.setBorder(new EmptyBorder(0, 0, 0, 0));

        // 1. Barra superior (Header)
        JPanel headerPanel = createHeaderPanel();
        rootPanel.add(headerPanel, BorderLayout.NORTH);

        // 2. Barra lateral izquierda (Canales y Usuarios en línea)
        JPanel sidebarPanel = createSidebarPanel();

        // 3. Área central de mensajes
        JPanel chatPanel = createChatPanel();

        // SplitPane divisor moderno entre sidebar y chat
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebarPanel, chatPanel);
        splitPane.setDividerLocation(250);
        splitPane.setDividerSize(4);
        splitPane.setContinuousLayout(true);
        splitPane.setBorder(null);
        rootPanel.add(splitPane, BorderLayout.CENTER);

        // 4. Barra inferior de envío con indicador de escritura
        JPanel bottomPanel = createBottomInputPanel();
        rootPanel.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(rootPanel);
    }

    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(new EmptyBorder(10, 16, 10, 16));
        header.setBackground(new Color(30, 31, 35));

        // Título de la app y canal activo
        JPanel leftHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        leftHeader.setOpaque(false);

        JLabel appTitle = new JLabel("RealTimeChat NextGen");
        appTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        appTitle.setForeground(new Color(242, 243, 245));

        channelTitleLabel = new JLabel("#general");
        channelTitleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        channelTitleLabel.setForeground(new Color(88, 101, 242));
        channelTitleLabel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(78, 80, 88)));
        channelTitleLabel.setBorder(new EmptyBorder(0, 10, 0, 0));

        leftHeader.add(appTitle);
        leftHeader.add(channelTitleLabel);

        // Panel derecho de acciones y estado
        JPanel rightStatusPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightStatusPanel.setOpaque(false);

        statusLabel = new JLabel("Desconectado", new StatusDotIcon(new Color(237, 66, 69)), JLabel.LEFT);
        statusLabel.setIconTextGap(6);
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        statusLabel.setForeground(new Color(237, 66, 69));

        JButton profileBtn = new JButton("Mi Perfil");
        profileBtn.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT);
        profileBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        profileBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        profileBtn.addActionListener(e -> showProfileEditDialog());

        JButton reconnectBtn = new JButton("Conectar / Cambiar");
        reconnectBtn.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT);
        reconnectBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        reconnectBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        reconnectBtn.addActionListener(e -> showConnectionDialog());

        rightStatusPanel.add(statusLabel);
        rightStatusPanel.add(profileBtn);
        rightStatusPanel.add(reconnectBtn);

        header.add(leftHeader, BorderLayout.WEST);
        header.add(rightStatusPanel, BorderLayout.EAST);
        return header;
    }

    private JPanel createSidebarPanel() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(new Color(43, 45, 49));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(53, 55, 60)));

        // Sección de Canales (Superior)
        JPanel channelsSection = new JPanel(new BorderLayout());
        channelsSection.setBackground(new Color(43, 45, 49));

        JPanel channelHeader = new JPanel(new BorderLayout());
        channelHeader.setBackground(new Color(43, 45, 49));
        channelHeader.setBorder(new EmptyBorder(12, 14, 6, 14));
        JLabel chanLabel = new JLabel("CANALES DE TEXTO");
        chanLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        chanLabel.setForeground(new Color(148, 155, 164));
        channelHeader.add(chanLabel, BorderLayout.WEST);
        channelsSection.add(channelHeader, BorderLayout.NORTH);

        channelList = new JList<>(channelListModel);
        channelList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        channelList.setBackground(new Color(43, 45, 49));
        channelList.setSelectedValue("#general", true);
        channelList.setCellRenderer(new ChannelListCellRenderer());
        channelList.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        channelList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selected = channelList.getSelectedValue();
                if (selected != null && !selected.equalsIgnoreCase(connection.getCurrentChannel())) {
                    switchChannel(selected);
                }
            }
        });

        JScrollPane chanScroll = new JScrollPane(channelList);
        chanScroll.setBorder(null);
        chanScroll.setPreferredSize(new Dimension(240, 140));
        channelsSection.add(chanScroll, BorderLayout.CENTER);

        // Sección de Usuarios (Inferior)
        JPanel usersSection = new JPanel(new BorderLayout());
        usersSection.setBackground(new Color(43, 45, 49));
        usersSection.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(53, 55, 60)));

        JPanel userHeader = new JPanel(new BorderLayout());
        userHeader.setBackground(new Color(43, 45, 49));
        userHeader.setBorder(new EmptyBorder(10, 14, 6, 14));
        userCountLabel = new JLabel("EN ESTE CANAL (0)");
        userCountLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        userCountLabel.setForeground(new Color(148, 155, 164));
        userHeader.add(userCountLabel, BorderLayout.WEST);
        usersSection.add(userHeader, BorderLayout.NORTH);

        userList = new JList<>(userListModel);
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userList.setBackground(new Color(43, 45, 49));
        userList.setCellRenderer(new UserListCellRenderer());
        userList.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Doble clic o menú contextual para ver perfil o enviar privado
        userList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = userList.locationToIndex(e.getPoint());
                if (index < 0) return;
                String selected = userListModel.getElementAt(index);

                if (e.getClickCount() == 2) {
                    showUserProfilePopup(selected);
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    userList.setSelectedIndex(index);
                    showUserContextMenu(e.getComponent(), e.getX(), e.getY(), selected);
                }
            }
        });

        JScrollPane userScroll = new JScrollPane(userList);
        userScroll.setBorder(null);
        usersSection.add(userScroll, BorderLayout.CENTER);

        sidebar.add(channelsSection, BorderLayout.NORTH);
        sidebar.add(usersSection, BorderLayout.CENTER);

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

        chatPane.addHyperlinkListener(e -> {
            if (e.getEventType() == javax.swing.event.HyperlinkEvent.EventType.ACTIVATED) {
                String desc = e.getDescription();
                if (desc != null && desc.startsWith("user:")) {
                    String targetUser = desc.substring(5).trim();
                    showUserProfilePopup(targetUser);
                }
            }
        });

        resetChatHtmlDocument();

        JScrollPane scrollPane = new JScrollPane(chatPane);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        chatPanel.add(scrollPane, BorderLayout.CENTER);

        return chatPanel;
    }

    private void resetChatHtmlDocument() {
        String baseCss = "<style>" +
                "body { font-family: 'Segoe UI', Tahoma, Arial, sans-serif; font-size: 13px; color: #dbdee1; margin: 10px; background-color: #313338; }" +
                ".msg-row { margin-bottom: 8px; line-height: 1.4; }" +
                ".timestamp { color: #949ba4; font-size: 11px; margin-right: 6px; font-weight: normal; }" +
                ".sender-other { color: #5865f2; font-weight: bold; font-size: 13px; }" +
                ".sender-me { color: #57f287; font-weight: bold; font-size: 13px; }" +
                ".sender-bot { color: #eb459e; font-weight: bold; font-size: 13px; }" +
                ".bot-badge { background-color: #5865f2; color: #ffffff; font-size: 10px; padding: 1px 4px; font-weight: bold; border-radius: 3px; margin-left: 4px; }" +
                ".system-msg { color: #f0b232; font-style: italic; background-color: #383424; padding: 4px 8px; }" +
                ".private-msg { color: #eb459e; background-color: #382434; padding: 6px 10px; border-left: 3px solid #eb459e; }" +
                ".history-divider { color: #949ba4; font-size: 11px; text-align: center; border-bottom: 1px dashed #4e5058; margin: 12px 0; padding-bottom: 4px; }" +
                ".body-text { color: #dbdee1; word-wrap: break-word; }" +
                "</style><div id='chat-flow'></div>";
        try {
            htmlDoc = (HTMLDocument) editorKit.createDefaultDocument();
            chatPane.setDocument(htmlDoc);
            editorKit.insertHTML(htmlDoc, 0, baseCss, 0, 0, null);
        } catch (Exception ignored) {
        }
    }

    private JPanel createBottomInputPanel() {
        JPanel container = new JPanel(new BorderLayout());
        container.setBackground(new Color(43, 45, 49));

        // Indicador de escritura en tiempo real (Typing indicator)
        typingIndicatorLabel = new JLabel(" ");
        typingIndicatorLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        typingIndicatorLabel.setForeground(new Color(181, 186, 193));
        typingIndicatorLabel.setBorder(new EmptyBorder(4, 18, 2, 18));
        container.add(typingIndicatorLabel, BorderLayout.NORTH);

        // Barra inferior de envío
        JPanel bottomPanel = new JPanel(new BorderLayout(8, 0));
        bottomPanel.setBorder(new EmptyBorder(4, 16, 12, 16));
        bottomPanel.setOpaque(false);

        inputField = new JTextField();
        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        inputField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Enviar mensaje a " + connection.getCurrentChannel() + " (prueba @bot resume, /w usuario)...");
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
        container.add(bottomPanel, BorderLayout.CENTER);

        return container;
    }

    private void setupTypingDetection() {
        typingStopTimer = new Timer(2500, e -> {
            if (isUserTyping) {
                isUserTyping = false;
                connection.sendTyping(false);
            }
        });
        typingStopTimer.setRepeats(false);

        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                if (e.getKeyChar() != KeyEvent.VK_ENTER && connection.isConnected()) {
                    if (!isUserTyping) {
                        isUserTyping = true;
                        connection.sendTyping(true);
                    }
                    typingStopTimer.restart();
                }
            }
        });
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

    private void switchChannel(String channelName) {
        if (!connection.isConnected()) {
            channelTitleLabel.setText(channelName);
            channelList.setSelectedValue(channelName, true);
            inputField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Enviar mensaje a " + channelName + "...");
            return;
        }

        boolean isDifferent = !channelName.equalsIgnoreCase(connection.getCurrentChannel());
        connection.switchChannel(channelName);
        channelTitleLabel.setText(channelName);
        channelList.setSelectedValue(channelName, true);
        inputField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Enviar mensaje a " + channelName + "...");
        if (isDifferent) {
            resetChatHtmlDocument();
            appendSystemHtml("Cambiando al canal " + channelName + "...");
        }
    }

    private void sendMessageFromInput() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) {
            return;
        }

        // Cancelar typing local
        if (isUserTyping) {
            isUserTyping = false;
            typingStopTimer.stop();
            connection.sendTyping(false);
        }

        if (!connection.isConnected()) {
            appendSystemHtml("No estás conectado. Pulsa 'Conectar / Cambiar' para iniciar sesión.");
            return;
        }

        if (text.equalsIgnoreCase("/help") || text.equalsIgnoreCase("/ayuda")) {
            appendSystemHtml("Comandos disponibles: /w &lt;usuario&gt; &lt;mensaje&gt;, /join &lt;#canal&gt;, /channels, /list, /help, /quit. Puedes invocar al asistente con @bot.");
            inputField.setText("");
            return;
        }

        if (text.startsWith("/join ") || text.startsWith("/canal ")) {
            String[] parts = text.split("\\s+", 2);
            if (parts.length >= 2) {
                switchChannel(parts[1].trim());
            }
            inputField.setText("");
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

        if (text.equalsIgnoreCase("/channels") || text.equalsIgnoreCase("/canales")) {
            connection.requestChannelList();
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

    private void showUserContextMenu(Component comp, int x, int y, String targetUser) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem viewProfileItem = new JMenuItem("Ver Perfil");
        viewProfileItem.addActionListener(e -> showUserProfilePopup(targetUser));

        JMenuItem sendPrivateItem = new JMenuItem("Enviar Mensaje Privado");
        sendPrivateItem.addActionListener(e -> {
            inputField.setText("/w " + targetUser + " ");
            inputField.requestFocusInWindow();
        });

        menu.add(viewProfileItem);
        menu.add(sendPrivateItem);
        menu.show(comp, x, y);
    }

    /**
     * Muestra la tarjeta de perfil emergente de un usuario con actualización reactiva en tiempo real.
     */
    public void showUserProfilePopup(String targetUsername) {
        if (targetUsername == null || targetUsername.trim().isEmpty()) {
            return;
        }
        String cleanUser = targetUsername.trim();

        // Solicitar siempre al servidor los datos más recientes del perfil
        if (connection.isConnected()) {
            connection.requestProfile(cleanUser);
        }

        UserProfile cached = profileCache.get(cleanUser);
        if (cached == null) {
            cached = new UserProfile(cleanUser);
        }

        JDialog dialog = new JDialog(this, "Perfil de @" + cleanUser, false);
        this.currentProfileDialog = dialog;
        this.currentProfileDialogUser = cleanUser;
        dialog.setSize(380, 320);
        dialog.setLocationRelativeTo(this);
        dialog.setResizable(false);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (currentProfileDialog == dialog) {
                    currentProfileDialog = null;
                    currentProfileDialogUser = null;
                    currentProfileAvatarLabel = null;
                    currentProfileBannerPanel = null;
                    currentProfileBioArea = null;
                }
            }
        });

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(new Color(49, 51, 56));

        // Banner superior
        ProfileBannerPanel bannerPanel = new ProfileBannerPanel(cached.getBanner());
        bannerPanel.setPreferredSize(new Dimension(380, 80));
        this.currentProfileBannerPanel = bannerPanel;

        // Tarjeta central con avatar y bio
        JPanel bodyPanel = new JPanel();
        bodyPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 10));
        bodyPanel.setBackground(new Color(49, 51, 56));

        JLabel avatarLabel = new JLabel(new AvatarPresetIcon(cached.getAvatar(), 54));
        this.currentProfileAvatarLabel = avatarLabel;
        JLabel nameLabel = new JLabel("@" + cleanUser, SwingConstants.CENTER);
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        nameLabel.setForeground(Color.WHITE);

        JTextArea bioArea = new JTextArea(cached.getBio());
        this.currentProfileBioArea = bioArea;
        bioArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        bioArea.setForeground(new Color(219, 222, 225));
        bioArea.setBackground(new Color(43, 45, 49));
        bioArea.setLineWrap(true);
        bioArea.setWrapStyleWord(true);
        bioArea.setEditable(false);
        bioArea.setBorder(new EmptyBorder(8, 10, 8, 10));
        bioArea.setPreferredSize(new Dimension(340, 70));

        JButton sendMsgBtn = new JButton("Enviar Mensaje Privado");
        sendMsgBtn.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT);
        sendMsgBtn.putClientProperty(FlatClientProperties.STYLE, "background: #5865f2; foreground: #ffffff;");
        sendMsgBtn.addActionListener(e -> {
            dialog.dispose();
            inputField.setText("/w " + cleanUser + " ");
            inputField.requestFocusInWindow();
        });

        bodyPanel.add(avatarLabel);
        bodyPanel.add(nameLabel);
        bodyPanel.add(bioArea);
        bodyPanel.add(sendMsgBtn);

        content.add(bannerPanel, BorderLayout.NORTH);
        content.add(bodyPanel, BorderLayout.CENTER);

        dialog.setContentPane(content);
        dialog.setVisible(true);
    }

    /**
     * Muestra el diálogo modal para editar la biografía, avatar y banner propios.
     */
    public void showProfileEditDialog() {
        JDialog dialog = new JDialog(this, "Personalizar Mi Perfil", true);
        dialog.setSize(440, 420);
        dialog.setLocationRelativeTo(this);
        dialog.setResizable(false);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(16, 20, 16, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 4, 6, 4);

        JComboBox<String> avatarCombo = new JComboBox<>(AVATAR_PRESETS);
        avatarCombo.setEditable(true);
        avatarCombo.setSelectedItem(myProfile.getAvatar());

        JComboBox<String> bannerCombo = new JComboBox<>(BANNER_PRESETS);
        bannerCombo.setEditable(true);
        bannerCombo.setSelectedItem(myProfile.getBanner());

        JTextArea bioField = new JTextArea(myProfile.getBio(), 4, 20);
        bioField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        bioField.setLineWrap(true);
        bioField.setWrapStyleWord(true);
        JScrollPane bioScroll = new JScrollPane(bioField);

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.3;
        panel.add(new JLabel("Avatar Preset:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.7;
        panel.add(avatarCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.3;
        panel.add(new JLabel("Banner (#hex / preset):"), gbc);
        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 0.7;
        panel.add(bannerCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.3;
        panel.add(new JLabel("Biografía:"), gbc);
        gbc.gridx = 1; gbc.gridy = 2; gbc.weightx = 0.7;
        panel.add(bioScroll, gbc);

        // Vista previa en vivo
        JPanel previewContainer = new JPanel(new BorderLayout());
        previewContainer.setBorder(BorderFactory.createTitledBorder("Vista Previa"));
        ProfileBannerPanel previewBanner = new ProfileBannerPanel((String) bannerCombo.getSelectedItem());
        previewBanner.setPreferredSize(new Dimension(380, 50));
        JLabel previewAvatar = new JLabel(new AvatarPresetIcon((String) avatarCombo.getSelectedItem(), 36));
        previewContainer.add(previewBanner, BorderLayout.NORTH);
        previewContainer.add(previewAvatar, BorderLayout.WEST);

        java.awt.event.ActionListener previewUpdater = e -> {
            String av = (avatarCombo.getSelectedItem() != null) ? avatarCombo.getSelectedItem().toString().trim() : "cyber-neon";
            String bn = (bannerCombo.getSelectedItem() != null) ? bannerCombo.getSelectedItem().toString().trim() : "#5865F2";
            previewAvatar.setIcon(new AvatarPresetIcon(av, 36));
            previewAvatar.repaint();
            previewBanner.setBannerStyle(bn);
            previewBanner.repaint();
        };
        avatarCombo.addActionListener(previewUpdater);
        bannerCombo.addActionListener(previewUpdater);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        panel.add(previewContainer, gbc);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton cancelBtn = new JButton("Cancelar");
        JButton saveBtn = new JButton("Guardar Perfil");
        saveBtn.putClientProperty(FlatClientProperties.STYLE, "background: #5865f2; foreground: #ffffff;");

        cancelBtn.addActionListener(e -> dialog.dispose());
        saveBtn.addActionListener(e -> {
            String av = (avatarCombo.getSelectedItem() != null) ? avatarCombo.getSelectedItem().toString().trim() : "cyber-neon";
            String bn = (bannerCombo.getSelectedItem() != null) ? bannerCombo.getSelectedItem().toString().trim() : "#5865F2";
            myProfile.setAvatar(av);
            myProfile.setBanner(bn);
            myProfile.setBio(bioField.getText());
            myProfile.setUsername(currentUsername);

            if (connection.isConnected()) {
                connection.updateProfile(myProfile);
                appendSystemHtml("¡Perfil actualizado con éxito!");
            }
            dialog.dispose();
        });

        btnPanel.add(cancelBtn);
        btnPanel.add(saveBtn);

        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        panel.add(btnPanel, gbc);

        dialog.setContentPane(panel);
        dialog.setVisible(true);
    }

    /**
     * Muestra el diálogo modal para configurar IP, puerto y nickname.
     */
    public void showConnectionDialog() {
        JDialog dialog = new JDialog(this, "Conexión a RealTimeChat NextGen", true);
        this.activeDialog = dialog;
        dialog.setSize(380, 280);
        dialog.setLocationRelativeTo(this);
        dialog.setResizable(false);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (activeDialog == dialog) {
                    activeDialog = null;
                }
            }
        });

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
                        myProfile.setUsername(user);
                        dialog.dispose();
                    } else {
                        errorLabel.setText("No se pudo conectar. Revisa nickname o servidor.");
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
                "<span class='system-msg'>[SISTEMA] " + escapeHtml(text) + "</span></div>";
        appendHtml(html);
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("\n", "<br/>");
    }

    // --- Implementación de ChatEventListener ---

    @Override
    public void onConnected(String username) {
        SwingUtilities.invokeLater(() -> {
            this.currentUsername = username;
            this.myProfile.setUsername(username);
            statusLabel.setText("En línea: @" + username);
            statusLabel.setIcon(new StatusDotIcon(new Color(87, 242, 135)));
            statusLabel.setForeground(new Color(87, 242, 135));
            appendSystemHtml("¡Conexión establecida como @" + username + "! Canal: " + connection.getCurrentChannel());
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
                boolean isBot = "RealTimeBot".equalsIgnoreCase(sender);

                String senderClass = isMe ? "sender-me" : (isBot ? "sender-bot" : "sender-other");
                String displayName = isMe ? "Tú" : escapeHtml(sender);
                String botBadge = isBot ? "<span class='bot-badge'>BOT AI</span>" : "";
                String userLink = isBot ? ("@" + displayName) : ("<a href='user:" + escapeHtml(sender) + "' style='text-decoration:none;' class='" + senderClass + "'>@" + displayName + "</a>");

                String html = "<div class='msg-row'><span class='timestamp'>[" + time + "]</span> " +
                        "<span class='" + senderClass + "'>" + userLink + botBadge + ":</span> " +
                        "<span class='body-text'>" + escapeHtml(message.getContent()) + "</span></div>";
                appendHtml(html);
            }
            case PRIVATE -> {
                String sender = message.getSender();
                String target = message.getTarget();
                boolean isMe = currentUsername != null && currentUsername.equalsIgnoreCase(sender);
                String label = isMe ? "[PRIVADO PARA @" + escapeHtml(target) + "]" : "[PRIVADO DE @" + escapeHtml(sender) + "]";
                String html = "<div class='msg-row'><div class='private-msg'><span class='timestamp'>[" + time + "]</span> " +
                        "<b>" + label + ":</b> " +
                        "<span class='body-text'>" + escapeHtml(message.getContent()) + "</span></div></div>";
                appendHtml(html);
            }
            case SYSTEM -> {
                appendSystemHtml(message.getContent());
            }
            case LEAVE -> {
                appendSystemHtml("El usuario @" + message.getSender() + " se ha desconectado.");
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
            userCountLabel.setText("EN ESTE CANAL (" + users.size() + ")");
        });
    }

    @Override
    public void onChannelListUpdated(List<String> channels) {
        SwingUtilities.invokeLater(() -> {
            channelListModel.clear();
            for (String c : channels) {
                channelListModel.addElement(c);
            }
        });
    }

    @Override
    public void onChannelChanged(String newChannel) {
        SwingUtilities.invokeLater(() -> {
            channelTitleLabel.setText(newChannel);
            channelList.setSelectedValue(newChannel, true);
            inputField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Enviar mensaje a " + newChannel + "...");
        });
    }

    @Override
    public void onTypingUpdated(String sender, String channel, boolean isTyping) {
        SwingUtilities.invokeLater(() -> {
            if (sender == null || sender.equalsIgnoreCase(currentUsername)) {
                return;
            }
            if (isTyping) {
                activeTypingUsers.add(sender);
            } else {
                activeTypingUsers.remove(sender);
            }

            if (activeTypingUsers.isEmpty()) {
                typingIndicatorLabel.setText(" ");
            } else if (activeTypingUsers.size() == 1) {
                String u = activeTypingUsers.iterator().next();
                typingIndicatorLabel.setText("✍️ @" + u + " está escribiendo en " + channel + "...");
            } else if (activeTypingUsers.size() == 2) {
                List<String> list = new ArrayList<>(activeTypingUsers);
                typingIndicatorLabel.setText("✍️ @" + list.get(0) + " y @" + list.get(1) + " están escribiendo...");
            } else {
                typingIndicatorLabel.setText("✍️ Varias personas están escribiendo...");
            }
        });
    }

    @Override
    public void onHistoryReceived(String channel, List<Message> history) {
        SwingUtilities.invokeLater(() -> {
            if (history == null || history.isEmpty()) {
                return;
            }
            String divider = "<div class='history-divider'>── Mensajes anteriores en " + escapeHtml(channel) + " (" + history.size() + ") ──</div>";
            appendHtml(divider);

            for (Message m : history) {
                String time = TIME_FORMATTER.format(Instant.ofEpochMilli(m.getTimestamp()));
                boolean isMe = currentUsername != null && currentUsername.equalsIgnoreCase(m.getSender());
                boolean isBot = "RealTimeBot".equalsIgnoreCase(m.getSender());
                String senderClass = isMe ? "sender-me" : (isBot ? "sender-bot" : "sender-other");
                String displayName = isMe ? "Tú" : escapeHtml(m.getSender());
                String botBadge = isBot ? "<span class='bot-badge'>BOT AI</span>" : "";
                String userLink = isBot ? ("@" + displayName) : ("<a href='user:" + escapeHtml(m.getSender()) + "' style='text-decoration:none;' class='" + senderClass + "'>@" + displayName + "</a>");

                String html = "<div class='msg-row'><span class='timestamp'>[" + time + "]</span> " +
                        "<span class='" + senderClass + "'>" + userLink + botBadge + ":</span> " +
                        "<span class='body-text'>" + escapeHtml(m.getContent()) + "</span></div>";
                appendHtml(html);
            }
        });
    }

    @Override
    public void onProfileReceived(UserProfile profile) {
        if (profile != null && profile.getUsername() != null) {
            profileCache.put(profile.getUsername(), profile);
            SwingUtilities.invokeLater(() -> {
                userList.repaint();
                // Si la ventana modal o popup de perfil está abierta para este usuario, actualizarla en vivo
                if (currentProfileDialog != null && currentProfileDialog.isShowing()
                        && profile.getUsername().equalsIgnoreCase(currentProfileDialogUser)) {
                    if (currentProfileAvatarLabel != null) {
                        currentProfileAvatarLabel.setIcon(new AvatarPresetIcon(profile.getAvatar(), 54));
                        currentProfileAvatarLabel.repaint();
                    }
                    if (currentProfileBannerPanel != null) {
                        currentProfileBannerPanel.setBannerStyle(profile.getBanner());
                        currentProfileBannerPanel.repaint();
                    }
                    if (currentProfileBioArea != null) {
                        currentProfileBioArea.setText(profile.getBio());
                    }
                }
            });
        }
    }

    @Override
    public void onDisconnected(String reason) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Desconectado");
            statusLabel.setIcon(new StatusDotIcon(new Color(237, 66, 69)));
            statusLabel.setForeground(new Color(237, 66, 69));
            userListModel.clear();
            userCountLabel.setText("EN ESTE CANAL (0)");
            activeTypingUsers.clear();
            typingIndicatorLabel.setText(" ");
            appendSystemHtml("Desconectado del servidor: " + reason);
        });
    }

    @Override
    public void onError(String errorMessage) {
        SwingUtilities.invokeLater(() -> {
            appendSystemHtml("ERROR: " + errorMessage);
            Component parentComp = (activeDialog != null && activeDialog.isShowing()) ? activeDialog : this;
            JOptionPane.showMessageDialog(parentComp, errorMessage, "Aviso de RealTimeChat", JOptionPane.WARNING_MESSAGE);
        });
    }

    // --- Renderizadores de Lista y Componentes Gráficos Personalizados ---

    private class ChannelListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            label.setBorder(new EmptyBorder(6, 12, 6, 12));
            label.setFont(new Font("Segoe UI", Font.BOLD, 13));

            String ch = value != null ? value.toString() : "";
            label.setText("#  " + (ch.startsWith("#") ? ch.substring(1) : ch));

            if (isSelected) {
                label.setBackground(new Color(53, 55, 60));
                label.setForeground(Color.WHITE);
            } else {
                label.setBackground(new Color(43, 45, 49));
                label.setForeground(new Color(148, 155, 164));
            }
            return label;
        }
    }

    private class UserListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            label.setBorder(new EmptyBorder(6, 10, 6, 10));
            label.setFont(new Font("Segoe UI", Font.PLAIN, 13));

            String user = value != null ? value.toString() : "";
            boolean isMe = currentUsername != null && currentUsername.equalsIgnoreCase(user);
            label.setText("@" + user + (isMe ? " (Tú)" : ""));

            UserProfile prof = profileCache.get(user);
            String avatarKey = prof != null ? prof.getAvatar() : (isMe ? myProfile.getAvatar() : "cyber-neon");
            label.setIcon(new AvatarPresetIcon(avatarKey, 18));

            if (isSelected) {
                label.setBackground(new Color(53, 55, 60));
                label.setForeground(Color.WHITE);
            } else {
                label.setBackground(new Color(43, 45, 49));
                label.setForeground(new Color(219, 222, 225));
            }
            return label;
        }
    }

    /**
     * Panel superior para renderizar el banner del perfil (con gradientes o colores sólidos).
     */
    public static class ProfileBannerPanel extends JPanel {
        private String bannerStyle;

        public ProfileBannerPanel(String style) {
            this.bannerStyle = style != null ? style : "#5865F2";
        }

        public void setBannerStyle(String style) {
            this.bannerStyle = style;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            if ("gradient-cyber".equalsIgnoreCase(bannerStyle)) {
                GradientPaint gp = new GradientPaint(0, 0, new Color(88, 101, 242), w, h, new Color(235, 69, 158));
                g2.setPaint(gp);
            } else if ("gradient-sunset".equalsIgnoreCase(bannerStyle)) {
                GradientPaint gp = new GradientPaint(0, 0, new Color(242, 63, 67), w, h, new Color(254, 231, 92));
                g2.setPaint(gp);
            } else if ("gradient-cosmic".equalsIgnoreCase(bannerStyle)) {
                GradientPaint gp = new GradientPaint(0, 0, new Color(87, 242, 135), w, h, new Color(88, 101, 242));
                g2.setPaint(gp);
            } else {
                Color c;
                try {
                    c = Color.decode(bannerStyle);
                } catch (Exception e) {
                    c = new Color(88, 101, 242);
                }
                g2.setColor(c);
            }

            g2.fillRect(0, 0, w, h);
            g2.dispose();
        }
    }

    /**
     * Icono vectorial para renderizar presets de avatar con badges de estilo.
     */
    public static class AvatarPresetIcon implements javax.swing.Icon {
        private final String preset;
        private final int size;

        public AvatarPresetIcon(String preset, int size) {
            this.preset = preset != null ? preset : "cyber-neon";
            this.size = size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Color baseColor;
            switch (preset.toLowerCase()) {
                case "synthwave" -> baseColor = new Color(235, 69, 158); // Rosa neón
                case "cosmic" -> baseColor = new Color(155, 89, 182);    // Violeta cósmico
                case "emerald" -> baseColor = new Color(35, 165, 90);    // Verde esmeralda
                case "sunset" -> baseColor = new Color(230, 126, 34);    // Naranja atardecer
                case "bot-matrix" -> baseColor = new Color(46, 204, 113);// Verde matriz
                case "golden" -> baseColor = new Color(241, 196, 15);    // Oro
                case "retro-pixel" -> baseColor = new Color(52, 152, 219);// Azul retro
                default -> baseColor = new Color(88, 101, 242);          // Blurple Discord
            }

            // Círculo base del avatar
            g2.setColor(baseColor);
            g2.fillOval(x, y, size, size);

            // Borde estético
            g2.setColor(new Color(255, 255, 255, 120));
            g2.drawOval(x, y, size - 1, size - 1);

            // Letra inicial o símbolo central si el tamaño lo permite
            if (size >= 24) {
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Segoe UI", Font.BOLD, size / 2));
                String initial = preset.substring(0, 1).toUpperCase();
                int strW = g2.getFontMetrics().stringWidth(initial);
                int strH = g2.getFontMetrics().getAscent();
                g2.drawString(initial, x + (size - strW) / 2, y + (size + strH) / 2 - 2);
            }

            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return size + 4;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }

    private static class StatusDotIcon implements javax.swing.Icon {
        private final Color color;
        public StatusDotIcon(Color color) { this.color = color; }
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillOval(x + 1, y + 2, 9, 9);
            g2.dispose();
        }
        @Override public int getIconWidth() { return 13; }
        @Override public int getIconHeight() { return 13; }
    }

    public static void main(String[] args) {
        FlatDarkLaf.setup();
        SwingUtilities.invokeLater(() -> {
            ChatGuiClient gui = new ChatGuiClient();
            if (args != null && args.length > 0 && !args[0].trim().isEmpty()) {
                gui.currentServerHost = args[0].trim();
            }
            if (args != null && args.length > 1 && !args[1].trim().isEmpty()) {
                try {
                    gui.currentServerPort = Integer.parseInt(args[1].trim());
                } catch (NumberFormatException ignored) {
                }
            }
            if (args != null && args.length > 2 && !args[2].trim().isEmpty()) {
                gui.currentUsername = args[2].trim();
            }
            gui.setVisible(true);
            gui.showConnectionDialog();
        });
    }
}
