package com.danicaju.chat.client;

import com.danicaju.chat.client.ui.ChannelManagerDialogs;
import com.danicaju.chat.client.ui.ChatFeedPanel;
import com.danicaju.chat.client.ui.ImageUtils;
import com.danicaju.chat.client.ui.MessageCardPanel;
import com.danicaju.chat.client.ui.ProfileDialogs;
import com.danicaju.chat.client.ui.SearchDialog;
import com.danicaju.chat.client.ui.SlashCommandPopup;
import com.danicaju.chat.client.ui.ThreadSidePanel;
import com.danicaju.chat.client.ui.VoiceAudioManager;
import com.danicaju.chat.client.ui.WhiteboardDialog;
import com.danicaju.chat.model.ChannelInfo;
import com.danicaju.chat.model.EmbedData;
import com.danicaju.chat.model.Message;
import com.danicaju.chat.model.MessageType;
import com.danicaju.chat.model.PollData;
import com.danicaju.chat.model.PresenceStatus;
import com.danicaju.chat.model.UserProfile;
import com.danicaju.chat.model.WebhookInfo;
import com.danicaju.chat.security.Permission;
import com.danicaju.chat.service.SlashCommandHelper;
import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatDarkLaf;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.AWTEvent;
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
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Taskbar;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cliente gráfico estilo Discord para RealTimeChat NextGen.
 * Integra Optimistic UI, Typing Throttling/Debounce, Threads, Presencia (Online/Idle/DND),
 * Reacciones delta, Cursor Pagination + Virtualización, Bitfield Permissions, Markdown/Piston Run,
 * Slash Commands (/poll), Webhooks, Búsqueda avanzada, Paste de imágenes, OpenGraph, Voz PCM y Pizarra + E2EE.
 */
public class ChatGuiClient extends JFrame implements ChatEventListener {

    private static final long TYPING_DEBOUNCE_MS = 3000L;
    private static final long IDLE_TIMEOUT_MS = 180_000L;

    private final ChatClientConnection connection;
    private final DefaultListModel<String> userListModel = new DefaultListModel<>();
    private final DefaultListModel<String> channelListModel = new DefaultListModel<>();
    private final DefaultListModel<String> webhookListModel = new DefaultListModel<>();
    private final List<WebhookInfo> webhookCacheList = new ArrayList<>();

    private final Map<String, UserProfile> profileCache = new ConcurrentHashMap<>();
    private final Map<String, ChannelInfo> channelInfoMap = new ConcurrentHashMap<>();
    private final Map<String, Long> typingExpiryMap = new ConcurrentHashMap<>();
    private final Map<String, Double> voiceLevelsMap = new ConcurrentHashMap<>();
    private final List<String> currentVoiceParticipants = Collections.synchronizedList(new ArrayList<>());

    private String currentServerHost = "127.0.0.1";
    private int currentServerPort = 5000;
    private String currentUsername = "";
    private UserProfile myProfile = new UserProfile();
    private PresenceStatus manualPresence = PresenceStatus.ONLINE;
    private volatile boolean autoIdled = false;
    private volatile long lastUserActivityMs = System.currentTimeMillis();

    // Componentes principales
    private JList<String> channelList;
    private JList<String> userList;
    private ChatFeedPanel chatFeedPanel;
    private ThreadSidePanel threadSidePanel;
    private SlashCommandPopup slashPopup;
    private SearchDialog searchDialog;
    private WhiteboardDialog whiteboardDialog;
    private VoiceAudioManager voiceAudioManager;

    private JLabel statusLabel;
    private JLabel channelTitleLabel;
    private JLabel channelTopicLabel;
    private JLabel userCountLabel;
    private JLabel typingIndicatorLabel;

    // Dock usuario propio y voz
    private JLabel dockAvatarLabel;
    private JLabel dockNameLabel;
    private JLabel dockStatusLabel;
    private JPanel voiceDockPanel;
    private JLabel voiceChannelStatusLbl;
    private JProgressBar voiceMicBar;
    private JButton muteVoiceBtn;
    private JButton deafenVoiceBtn;

    // Barra de entrada y banners contextuales (Reply / Paste Imagen)
    private JTextArea inputArea;
    private JButton sendButton;
    private JButton createChanBtn;
    private JButton deleteChanBtn;
    private JButton permissionsBtn;
    private JButton webhooksBtn;

    private JPanel replyPreviewBar;
    private JLabel replyPreviewLabel;
    private Long pendingReplyToId;
    private String pendingReplySender;
    private String pendingReplyText;

    private JPanel imagePasteBar;
    private JLabel imagePasteThumb;
    private JLabel imagePasteInfo;
    private String pendingImageBase64;
    private String pendingImageName;

    private JDialog activeDialog;

    public ChatGuiClient() {
        super("RealTimeChat NextGen");
        this.connection = new ChatClientConnection();
        this.voiceAudioManager = new VoiceAudioManager(connection, level -> SwingUtilities.invokeLater(() -> {
            if (voiceMicBar != null) {
                voiceMicBar.setValue((int) Math.round(level * 100));
            }
        }));

        channelListModel.addElement("#general");
        channelListModel.addElement("#ia-computacion");
        channelListModel.addElement("#random");
        channelListModel.addElement("#voz-general");

        initComponents();
        setupWindowEvents();
        setupTypingAndIdleMonitors();
    }

    private void setupWindowIcons() {
        try {
            List<Image> iconList = new ArrayList<>();
            int[] sizes = {16, 20, 24, 28, 32, 48, 64, 128, 256, 512};
            for (int s : sizes) {
                java.net.URL url = getClass().getResource("/icons/icon-" + s + ".png");
                if (url != null) {
                    iconList.add(new ImageIcon(url).getImage());
                }
            }
            if (iconList.isEmpty()) {
                java.net.URL fallback = getClass().getResource("/icons/app-icon.png");
                if (fallback != null) {
                    iconList.add(new ImageIcon(fallback).getImage());
                }
            }
            if (!iconList.isEmpty()) {
                setIconImages(iconList);
                if (Taskbar.isTaskbarSupported()) {
                    Taskbar taskbar = Taskbar.getTaskbar();
                    if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                        taskbar.setIconImage(iconList.get(iconList.size() - 1));
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static ImageIcon getAppImageIcon(int size) {
        try {
            java.net.URL url = ChatGuiClient.class.getResource("/icons/icon-" + size + ".png");
            if (url != null) {
                return new ImageIcon(url);
            }
            java.net.URL fallback = ChatGuiClient.class.getResource("/icons/app-icon.png");
            if (fallback != null) {
                Image img = new ImageIcon(fallback).getImage();
                return new ImageIcon(img.getScaledInstance(size, size, Image.SCALE_SMOOTH));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void initComponents() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setupWindowIcons();
        setSize(1240, 780);
        setMinimumSize(new Dimension(920, 580));
        setLocationRelativeTo(null);

        JPanel rootPanel = new JPanel(new BorderLayout());
        rootPanel.setBackground(new Color(0x313338));
        rootPanel.add(createHeaderPanel(), BorderLayout.NORTH);

        JPanel sidebar = createSidebarPanel();

        chatFeedPanel = new ChatFeedPanel();
        chatFeedPanel.configure(
                currentUsername,
                getEffectiveMyChannelPerms(connection.getCurrentChannel()),
                profileCache,
                buildCardActions(),
                oldestId -> {
                    if (connection.isConnected()) {
                        connection.requestOlderHistory(connection.getCurrentChannel(), oldestId, 30);
                    }
                }
        );

        threadSidePanel = new ThreadSidePanel(() -> chatFeedPanel.revalidate());
        threadSidePanel.configure(currentUsername, profileCache, (rootId, text) -> {
            Message msg = Message.chat(connection.getCurrentChannel(), currentUsername, text);
            msg.setThreadParentId(rootId);
            connection.sendOptimisticOrQueue(msg);
        });

        JPanel centerStack = new JPanel(new BorderLayout());
        centerStack.add(chatFeedPanel, BorderLayout.CENTER);
        centerStack.add(createBottomInputPanel(), BorderLayout.SOUTH);

        JPanel mainArea = new JPanel(new BorderLayout());
        mainArea.add(centerStack, BorderLayout.CENTER);
        mainArea.add(threadSidePanel, BorderLayout.EAST);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebar, mainArea);
        splitPane.setDividerLocation(280);
        splitPane.setDividerSize(4);
        splitPane.setContinuousLayout(true);
        splitPane.setBorder(null);

        rootPanel.add(splitPane, BorderLayout.CENTER);
        setContentPane(rootPanel);
    }

    private MessageCardPanel.CardActions buildCardActions() {
        return new MessageCardPanel.CardActions() {
            @Override
            public void onReplyClicked(Message msg) {
                pendingReplyToId = msg.getId();
                pendingReplySender = msg.getSender();
                String raw = msg.getContent() != null ? msg.getContent() : "";
                pendingReplyText = raw.length() > 75 ? raw.substring(0, 72) + "..." : raw;
                replyPreviewLabel.setText("Respondiendo a @" + pendingReplySender + ": " + pendingReplyText);
                replyPreviewBar.setVisible(true);
                inputArea.requestFocusInWindow();
            }

            @Override
            public void onOpenThreadClicked(Message msg) {
                if (msg.getId() == null) {
                    JOptionPane.showMessageDialog(ChatGuiClient.this, "Espera a que el mensaje sea confirmado por el servidor.");
                    return;
                }
                threadSidePanel.configure(currentUsername, profileCache, (rootId, text) -> {
                    Message reply = Message.chat(connection.getCurrentChannel(), currentUsername, text);
                    reply.setThreadParentId(rootId);
                    connection.sendOptimisticOrQueue(reply);
                });
                threadSidePanel.openThread(msg);
                connection.requestThreadHistory(connection.getCurrentChannel(), msg.getId());
            }

            @Override
            public void onReactionToggle(Message msg, String emoji, boolean add) {
                if (msg.getId() != null) {
                    connection.sendReaction(msg.getId(), msg.getChannel(), emoji, add);
                }
            }

            @Override
            public void onDeleteClicked(Message msg) {
                if (msg.getId() != null) {
                    connection.deleteMessage(msg.getId(), msg.getChannel());
                }
            }

            @Override
            public void onRetryClicked(Message failedMsg) {
                chatFeedPanel.removeCard(null, failedMsg.getTempId());
                failedMsg.setTempId(null);
                sendMessageWithOptimisticUi(failedMsg);
            }

            @Override
            public void onDiscardErrorClicked(Message failedMsg) {
                connection.cancelPendingMessage(failedMsg.getTempId());
                chatFeedPanel.removeCard(failedMsg.getId(), failedMsg.getTempId());
            }

            @Override
            public void onJumpToMessage(Long messageId) {
                if (messageId != null && !chatFeedPanel.scrollToMessage(messageId)) {
                    JOptionPane.showMessageDialog(ChatGuiClient.this,
                            "El mensaje #" + messageId + " es anterior al búfer actual; desliza hacia arriba para cargarlo.",
                            "Mensaje fuera de vista", JOptionPane.INFORMATION_MESSAGE);
                }
            }

            @Override
            public void onPollVote(Message msg, int optionIndex) {
                if (msg.getId() != null) {
                    connection.votePoll(msg.getChannel(), msg.getId(), optionIndex);
                }
            }

            @Override
            public void onUserClicked(String username) {
                openProfileCard(username);
            }
        };
    }

    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(new EmptyBorder(8, 14, 8, 14));
        header.setBackground(new Color(0x1E1F22));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);

        JLabel appTitle = new JLabel("RealTimeChat");
        ImageIcon headerIcon = getAppImageIcon(26);
        if (headerIcon != null) {
            appTitle.setIcon(headerIcon);
            appTitle.setIconTextGap(8);
        }
        appTitle.setFont(new Font("Segoe UI", Font.BOLD, 15));
        appTitle.setForeground(new Color(0xF2F3F5));

        channelTitleLabel = new JLabel("#general");
        channelTitleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        channelTitleLabel.setForeground(new Color(0x949CF7));
        channelTitleLabel.setBorder(new EmptyBorder(0, 8, 0, 0));

        channelTopicLabel = new JLabel("Canal principal de la comunidad");
        channelTopicLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        channelTopicLabel.setForeground(new Color(0x949BA4));

        left.add(appTitle);
        left.add(channelTitleLabel);
        left.add(channelTopicLabel);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);

        statusLabel = new JLabel("Desconectado", new StatusDotIcon(new Color(0xF23F43)), JLabel.LEFT);
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        statusLabel.setForeground(new Color(0xF23F43));

        JButton searchBtn = makeHeaderBtn("Buscar");
        searchBtn.addActionListener(e -> openSearchDialog(""));

        JButton boardBtn = makeHeaderBtn("Pizarra");
        boardBtn.addActionListener(e -> openWhiteboardDialog());

        webhooksBtn = makeHeaderBtn("Webhooks");
        webhooksBtn.addActionListener(e -> ChannelManagerDialogs.showWebhooksDialog(
                this, connection, connection.getCurrentChannel(), webhookListModel, webhookCacheList
        ));

        permissionsBtn = makeHeaderBtn("Roles / Permisos");
        permissionsBtn.addActionListener(e -> ChannelManagerDialogs.showPermissionsDialog(
                this, connection, profileCache, new ArrayList<>(channelInfoMap.values()), null
        ));

        JButton reconnectBtn = makeHeaderBtn("Conectar");
        reconnectBtn.addActionListener(e -> showConnectionDialog());

        right.add(statusLabel);
        right.add(searchBtn);
        right.add(boardBtn);
        right.add(webhooksBtn);
        right.add(permissionsBtn);
        right.add(reconnectBtn);

        header.add(left, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JPanel createSidebarPanel() {
        JPanel sidebar = new JPanel(new BorderLayout(0, 0));
        sidebar.setBackground(new Color(0x2B2D31));

        // 1. Sección superior de Canales
        JPanel channelsSection = new JPanel(new BorderLayout(0, 4));
        channelsSection.setBackground(new Color(0x2B2D31));
        channelsSection.setBorder(new EmptyBorder(10, 10, 8, 10));

        JPanel chHeader = new JPanel(new BorderLayout());
        chHeader.setOpaque(false);
        JLabel channelsHdr = new JLabel("CANALES");
        channelsHdr.setFont(new Font("Segoe UI", Font.BOLD, 11));
        channelsHdr.setForeground(new Color(0x949BA4));

        JPanel chActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
        chActions.setOpaque(false);
        createChanBtn = new JButton("+");
        createChanBtn.setToolTipText("Crear nuevo canal (requiere MANAGE_CHANNELS)");
        styleTinySidebarBtn(createChanBtn);
        createChanBtn.addActionListener(e -> ChannelManagerDialogs.showCreateChannelDialog(this, connection));

        deleteChanBtn = new JButton("-");
        deleteChanBtn.setToolTipText("Eliminar un canal (requiere MANAGE_CHANNELS)");
        styleTinySidebarBtn(deleteChanBtn);
        deleteChanBtn.addActionListener(e -> {
            List<String> list = new ArrayList<>();
            for (int i = 0; i < channelListModel.size(); i++) list.add(channelListModel.get(i));
            ChannelManagerDialogs.showDeleteChannelDialog(this, connection, list);
        });
        chActions.add(createChanBtn);
        chActions.add(deleteChanBtn);
        chHeader.add(channelsHdr, BorderLayout.WEST);
        chHeader.add(chActions, BorderLayout.EAST);

        channelList = new JList<>(channelListModel);
        channelList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        channelList.setBackground(new Color(0x2B2D31));
        channelList.setForeground(new Color(0xDBDEE1));
        channelList.setFixedCellHeight(30);
        channelList.setSelectedIndex(0);
        channelList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                String name = value != null ? value.toString() : "";
                ChannelInfo info = channelInfoMap.get(name.toLowerCase());
                boolean isVoice = (info != null && ChannelInfo.TYPE_VOICE.equalsIgnoreCase(info.getType()))
                        || name.startsWith("#voz");
                label.setBorder(new EmptyBorder(4, 8, 4, 8));
                label.setFont(new Font("Segoe UI", isSelected ? Font.BOLD : Font.PLAIN, 13));
                label.setText(isVoice ? "[VOZ] " + name : name);
                if (isSelected) {
                    label.setBackground(new Color(0x404249));
                    label.setForeground(Color.WHITE);
                } else {
                    label.setBackground(new Color(0x2B2D31));
                    label.setForeground(isVoice ? new Color(0x57F287) : new Color(0x949BA4));
                }
                return label;
            }
        });
        channelList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                String selected = channelList.getSelectedValue();
                if (selected == null || !connection.isConnected()) return;
                if (!selected.equalsIgnoreCase(connection.getCurrentChannel())) {
                    connection.switchChannel(selected);
                }
                ChannelInfo info = channelInfoMap.get(selected.toLowerCase());
                boolean isVoice = (info != null && ChannelInfo.TYPE_VOICE.equalsIgnoreCase(info.getType()))
                        || selected.startsWith("#voz");
                if (isVoice && e.getClickCount() == 2) {
                    toggleJoinVoiceChannel(selected);
                }
            }
        });

        JScrollPane chScroll = new JScrollPane(channelList);
        chScroll.setBorder(null);
        chScroll.setPreferredSize(new Dimension(250, 155));
        channelsSection.add(chHeader, BorderLayout.NORTH);
        channelsSection.add(chScroll, BorderLayout.CENTER);

        // 2. Sección central de Miembros en Canal (con Avatar Custom + Estado de Presencia + Rol)
        JPanel usersSection = new JPanel(new BorderLayout(0, 4));
        usersSection.setBackground(new Color(0x2B2D31));
        usersSection.setBorder(new EmptyBorder(6, 10, 6, 10));

        userCountLabel = new JLabel("MIEMBROS (0)");
        userCountLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        userCountLabel.setForeground(new Color(0x949BA4));
        userCountLabel.setBorder(new EmptyBorder(0, 2, 4, 0));

        userList = new JList<>(userListModel);
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userList.setBackground(new Color(0x2B2D31));
        userList.setForeground(new Color(0xDBDEE1));
        userList.setFixedCellHeight(38);
        userList.setCellRenderer(new MemberListRenderer());
        userList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                String selected = userList.getSelectedValue();
                if (selected == null) return;
                if (SwingUtilities.isRightMouseButton(e)) {
                    showUserContextMenu(e.getComponent(), e.getX(), e.getY(), selected);
                } else if (e.getClickCount() == 2) {
                    openProfileCard(selected);
                }
            }
        });

        JScrollPane userScroll = new JScrollPane(userList);
        userScroll.setBorder(null);
        usersSection.add(userCountLabel, BorderLayout.NORTH);
        usersSection.add(userScroll, BorderLayout.CENTER);

        // 3. Dock inferior de Voz + Perfil Propio estilo Discord (#232428)
        JPanel southDock = new JPanel();
        southDock.setLayout(new BoxLayout(southDock, BoxLayout.Y_AXIS));
        southDock.setBackground(new Color(0x232428));

        voiceDockPanel = createVoiceDockPanel();
        southDock.add(voiceDockPanel);
        southDock.add(createUserDockPanel());

        sidebar.add(channelsSection, BorderLayout.NORTH);
        sidebar.add(usersSection, BorderLayout.CENTER);
        sidebar.add(southDock, BorderLayout.SOUTH);
        return sidebar;
    }

    private JPanel createVoiceDockPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(new Color(0x1E2923));
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 1, 0, new Color(0x23A55A)),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        p.setVisible(false);

        voiceChannelStatusLbl = new JLabel("Voz Conectada");
        voiceChannelStatusLbl.setForeground(new Color(0x57F287));
        voiceChannelStatusLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));

        voiceMicBar = new JProgressBar(0, 100);
        voiceMicBar.setPreferredSize(new Dimension(220, 6));
        voiceMicBar.setForeground(new Color(0x57F287));
        voiceMicBar.setBackground(new Color(0x1E1F22));

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        btns.setOpaque(false);
        muteVoiceBtn = makeHeaderBtn("Mute");
        muteVoiceBtn.addActionListener(e -> {
            voiceAudioManager.setMuted(!voiceAudioManager.isMuted());
            muteVoiceBtn.setText(voiceAudioManager.isMuted() ? "Unmute" : "Mute");
            muteVoiceBtn.setBackground(voiceAudioManager.isMuted() ? new Color(0xF23F43) : new Color(0x3F4147));
        });
        deafenVoiceBtn = makeHeaderBtn("Deaf");
        deafenVoiceBtn.addActionListener(e -> {
            voiceAudioManager.setDeafened(!voiceAudioManager.isDeafened());
            deafenVoiceBtn.setText(voiceAudioManager.isDeafened() ? "Undeaf" : "Deaf");
            deafenVoiceBtn.setBackground(voiceAudioManager.isDeafened() ? new Color(0xF23F43) : new Color(0x3F4147));
        });
        JButton leaveVoiceBtn = makeHeaderBtn("Colgar");
        leaveVoiceBtn.setBackground(new Color(0xF23F43));
        leaveVoiceBtn.addActionListener(e -> {
            voiceAudioManager.leaveVoice();
            voiceDockPanel.setVisible(false);
        });
        btns.add(muteVoiceBtn);
        btns.add(deafenVoiceBtn);
        btns.add(leaveVoiceBtn);

        p.add(voiceChannelStatusLbl);
        p.add(Box.createVerticalStrut(4));
        p.add(voiceMicBar);
        p.add(btns);
        return p;
    }

    private JPanel createUserDockPanel() {
        JPanel dock = new JPanel(new BorderLayout(8, 0));
        dock.setBackground(new Color(0x232428));
        dock.setBorder(new EmptyBorder(8, 10, 8, 10));

        dockAvatarLabel = new JLabel(ImageUtils.buildAvatarIcon("?", "cyber-neon", null, 34, new Color(0x23A55A)));
        dockAvatarLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        dockAvatarLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                openSelfProfileEditor();
            }
        });

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        dockNameLabel = new JLabel("Sin sesión");
        dockNameLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        dockNameLabel.setForeground(Color.WHITE);
        dockStatusLabel = new JLabel("En línea");
        dockStatusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        dockStatusLabel.setForeground(new Color(0x949BA4));
        info.add(dockNameLabel);
        info.add(dockStatusLabel);

        JButton editBtn = makeHeaderBtn("Perfil");
        editBtn.addActionListener(e -> openSelfProfileEditor());

        JButton statusBtn = makeHeaderBtn("Estado");
        statusBtn.addActionListener(e -> showPresenceMenu(statusBtn));

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
        btns.setOpaque(false);
        btns.add(statusBtn);
        btns.add(editBtn);

        dock.add(dockAvatarLabel, BorderLayout.WEST);
        dock.add(info, BorderLayout.CENTER);
        dock.add(btns, BorderLayout.EAST);
        return dock;
    }

    private JPanel createBottomInputPanel() {
        JPanel bottom = new JPanel(new BorderLayout(0, 4));
        bottom.setBorder(new EmptyBorder(4, 14, 12, 14));
        bottom.setBackground(new Color(0x313338));

        // 1. Indicador de escritura
        typingIndicatorLabel = new JLabel(" ");
        typingIndicatorLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        typingIndicatorLabel.setForeground(new Color(0x949BA4));

        // 2. Barra de respuesta inline (Reply preview)
        replyPreviewBar = new JPanel(new BorderLayout(8, 0));
        replyPreviewBar.setBackground(new Color(0x2B2D31));
        replyPreviewBar.setBorder(new EmptyBorder(5, 10, 5, 10));
        replyPreviewBar.setVisible(false);
        replyPreviewLabel = new JLabel();
        replyPreviewLabel.setForeground(new Color(0xC9CDFB));
        replyPreviewLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        JButton cancelReply = makeHeaderBtn("X");
        cancelReply.addActionListener(e -> clearPendingReply());
        replyPreviewBar.add(replyPreviewLabel, BorderLayout.CENTER);
        replyPreviewBar.add(cancelReply, BorderLayout.EAST);

        // 3. Barra de previsualización de Imagen pegada (Ctrl+V o archivo)
        imagePasteBar = new JPanel(new BorderLayout(8, 0));
        imagePasteBar.setBackground(new Color(0x2B2D31));
        imagePasteBar.setBorder(new EmptyBorder(6, 10, 6, 10));
        imagePasteBar.setVisible(false);
        imagePasteThumb = new JLabel();
        imagePasteInfo = new JLabel();
        imagePasteInfo.setForeground(new Color(0x57F287));
        JButton clearImgBtn = makeHeaderBtn("Quitar imagen");
        clearImgBtn.addActionListener(e -> clearPendingImage());
        imagePasteBar.add(imagePasteThumb, BorderLayout.WEST);
        imagePasteBar.add(imagePasteInfo, BorderLayout.CENTER);
        imagePasteBar.add(clearImgBtn, BorderLayout.EAST);

        JPanel barsStack = new JPanel();
        barsStack.setOpaque(false);
        barsStack.setLayout(new BoxLayout(barsStack, BoxLayout.Y_AXIS));
        barsStack.add(typingIndicatorLabel);
        barsStack.add(replyPreviewBar);
        barsStack.add(imagePasteBar);

        // 4. Input compuesto multilinea + Ctrl+V imágenes + / autocompletado
        JPanel inputRow = new JPanel(new BorderLayout(8, 0));
        inputRow.setBackground(new Color(0x383A40));
        inputRow.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 8));

        inputArea = new JTextArea(2, 40);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        inputArea.setBackground(new Color(0x383A40));
        inputArea.setForeground(new Color(0xDBDEE1));
        inputArea.setCaretColor(Color.WHITE);
        inputArea.setEnabled(false);

        slashPopup = new SlashCommandPopup();
        inputArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { onInputChanged(); }
            @Override public void removeUpdate(DocumentEvent e) { onInputChanged(); }
            @Override public void changedUpdate(DocumentEvent e) { onInputChanged(); }
        });

        // Enter envía, Shift+Enter inserta salto de línea (ideal para bloques de código Markdown!)
        inputArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "send-message");
        inputArea.getActionMap().put("send-message", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (slashPopup.isVisible()) {
                    slashPopup.setVisible(false);
                }
                sendCurrentInput();
            }
        });
        inputArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.SHIFT_DOWN_MASK), "insert-newline");
        inputArea.getActionMap().put("insert-newline", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                inputArea.replaceSelection("\n");
            }
        });
        // Ctrl+V interceptor de imágenes del portapapeles
        inputArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_V, KeyEvent.CTRL_DOWN_MASK), "paste-smart");
        inputArea.getActionMap().put("paste-smart", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!tryHandleClipboardImagePaste()) {
                    inputArea.paste();
                }
            }
        });

        JScrollPane inputScroll = new JScrollPane(inputArea);
        inputScroll.setBorder(null);
        inputScroll.setPreferredSize(new Dimension(400, 48));

        JButton attachBtn = makeHeaderBtn("+Img");
        attachBtn.setToolTipText("Adjuntar imagen desde archivo (o pega con Ctrl+V)");
        attachBtn.addActionListener(e -> chooseAttachmentImage());

        JButton voiceToggleBtn = makeHeaderBtn("Voz");
        voiceToggleBtn.setToolTipText("Unirse/Salir del canal de voz actual");
        voiceToggleBtn.addActionListener(e -> toggleJoinVoiceChannel(connection.getCurrentChannel()));

        sendButton = new JButton("Enviar");
        sendButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        sendButton.setBackground(new Color(0x5865F2));
        sendButton.setForeground(Color.WHITE);
        sendButton.setEnabled(false);
        sendButton.addActionListener(e -> sendCurrentInput());

        JPanel rightBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        rightBtns.setOpaque(false);
        rightBtns.add(attachBtn);
        rightBtns.add(voiceToggleBtn);
        rightBtns.add(sendButton);

        inputRow.add(inputScroll, BorderLayout.CENTER);
        inputRow.add(rightBtns, BorderLayout.EAST);

        bottom.add(barsStack, BorderLayout.NORTH);
        bottom.add(inputRow, BorderLayout.CENTER);
        return bottom;
    }

    private void onInputChanged() {
        lastUserActivityMs = System.currentTimeMillis();
        String text = inputArea.getText();
        slashPopup.updateForInput(inputArea, text);
        if (connection.isConnected() && !text.isBlank()) {
            connection.notifyUserTypingThrottled();
        }
    }

    private boolean tryHandleClipboardImagePaste() {
        try {
            Transferable t = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if (t != null && t.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                Image pasted = (Image) t.getTransferData(DataFlavor.imageFlavor);
                stageAttachedImage(pasted, "captura-portapapeles.jpg");
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void chooseAttachmentImage() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Imágenes (PNG, JPG, GIF, BMP)", "png", "jpg", "jpeg", "gif", "bmp"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            try {
                BufferedImage img = ImageIO.read(f);
                if (img != null) {
                    stageAttachedImage(img, f.getName());
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "No se pudo leer la imagen: " + ex.getMessage());
            }
        }
    }

    private void stageAttachedImage(Image raw, String name) {
        BufferedImage compressed = ImageUtils.scaleToMaxDimensions(raw, 1024, 1024);
        if (compressed == null) return;
        String base64 = ImageUtils.encodeCompressedJpegBase64(compressed, 0.80f);
        if (base64 == null) return;

        pendingImageBase64 = base64;
        pendingImageName = name;
        BufferedImage thumb = ImageUtils.scaleToMaxDimensions(compressed, 64, 48);
        imagePasteThumb.setIcon(thumb != null ? new ImageIcon(thumb) : null);
        imagePasteInfo.setText("Imagen lista (" + compressed.getWidth() + "x" + compressed.getHeight() + " px, " + (base64.length() * 3 / 4096) + " KB): " + name);
        imagePasteBar.setVisible(true);
    }

    private void clearPendingImage() {
        pendingImageBase64 = null;
        pendingImageName = null;
        imagePasteBar.setVisible(false);
    }

    private void clearPendingReply() {
        pendingReplyToId = null;
        pendingReplySender = null;
        pendingReplyText = null;
        replyPreviewBar.setVisible(false);
    }

    // =========================================================================
    // ENVÍO OPTIMISTA, COMANDOS SLASH Y DMs E2EE
    // =========================================================================

    private void sendCurrentInput() {
        lastUserActivityMs = System.currentTimeMillis();
        String text = inputArea.getText().trim();
        if (text.isEmpty() && pendingImageBase64 == null) return;

        inputArea.setText("");
        if (connection.isConnected()) {
            connection.sendTyping(false);
        }

        // Comandos Slash locales o ricos
        if (text.startsWith("/")) {
            if (handleSlashCommand(text)) {
                clearPendingReply();
                clearPendingImage();
                return;
            }
        }

        Message msg = Message.chat(connection.getCurrentChannel(), currentUsername, text);
        msg.setReplyToId(pendingReplyToId);
        msg.setReplySender(pendingReplySender);
        msg.setReplyPreview(pendingReplyText);
        if (pendingImageBase64 != null) {
            msg.setAttachmentName(pendingImageName);
            msg.setAttachmentBase64(pendingImageBase64);
        }

        clearPendingReply();
        clearPendingImage();
        sendMessageWithOptimisticUi(msg);
    }

    private void sendMessageWithOptimisticUi(Message msg) {
        String tempId = connection.sendOptimisticOrQueue(msg);
        msg.setTempId(tempId);
        chatFeedPanel.appendMessage(msg, MessageCardPanel.DeliveryState.SENDING, true);
        if (!connection.isConnected()) {
            updateConnectionBannerOffline();
        }
    }

    private boolean handleSlashCommand(String input) {
        String lower = input.toLowerCase();
        if (lower.equals("/clear")) {
            chatFeedPanel.clearFeed();
            return true;
        }
        if (lower.startsWith("/poll")) {
            PollData poll = SlashCommandHelper.parsePoll(input);
            if (poll == null) {
                JOptionPane.showMessageDialog(this, "Uso: /poll ¿Pregunta? | Opción 1 | Opción 2", "Formato de Encuesta", JOptionPane.INFORMATION_MESSAGE);
                return true;
            }
            Message msg = Message.chat(connection.getCurrentChannel(), currentUsername, "[ENCUESTA] " + poll.getQuestion());
            msg.setPoll(poll);
            sendMessageWithOptimisticUi(msg);
            return true;
        }
        if (lower.startsWith("/roll")) {
            String result = SlashCommandHelper.evaluateRoll(input, currentUsername);
            sendMessageWithOptimisticUi(Message.chat(connection.getCurrentChannel(), currentUsername, result));
            return true;
        }
        if (lower.startsWith("/shrug")) {
            sendMessageWithOptimisticUi(Message.chat(connection.getCurrentChannel(), currentUsername, SlashCommandHelper.evaluateShrug(input)));
            return true;
        }
        if (lower.startsWith("/join ")) {
            connection.switchChannel(input.substring(6).trim());
            return true;
        }
        if (lower.startsWith("/createchannel ")) {
            String[] p = input.substring(15).trim().split("\\s+", 3);
            String name = p[0].startsWith("#") ? p[0] : "#" + p[0];
            String type = (p.length > 1 && "VOICE".equalsIgnoreCase(p[1])) ? ChannelInfo.TYPE_VOICE : ChannelInfo.TYPE_TEXT;
            String topic = p.length > 2 ? p[2] : "Canal creado desde comando";
            connection.createChannel(new ChannelInfo(name, type, topic, currentUsername));
            return true;
        }
        if (lower.startsWith("/deletechannel ")) {
            connection.deleteChannel(input.substring(15).trim());
            return true;
        }
        if (lower.startsWith("/search")) {
            String q = input.length() > 7 ? input.substring(7).trim() : "";
            openSearchDialog(q);
            if (!q.isEmpty()) connection.searchMessages(q);
            return true;
        }
        if (lower.startsWith("/status ")) {
            String[] p = input.substring(8).trim().split("\\s+", 2);
            PresenceStatus st = PresenceStatus.fromString(p[0]);
            manualPresence = st;
            String custom = p.length > 1 ? p[1] : null;
            myProfile.setPresence(st);
            myProfile.setCustomStatus(custom);
            connection.updatePresence(st, custom, myProfile.getStatusSymbol());
            updateOwnDock();
            return true;
        }
        if (lower.equals("/whiteboard")) {
            openWhiteboardDialog();
            return true;
        }
        if (lower.startsWith("/bio ")) {
            myProfile.setBio(input.substring(5).trim());
            connection.updateProfile(myProfile);
            return true;
        }
        if (lower.startsWith("/w ") || lower.startsWith("/msg ")) {
            String[] parts = input.split("\\s+", 3);
            if (parts.length >= 3) {
                sendDirectMessageAutoE2ee(parts[1], parts[2]);
            }
            return true;
        }
        return false;
    }

    private void sendDirectMessageAutoE2ee(String target, String plaintext) {
        if (connection.hasPeerPublicKey(target)) {
            if (connection.sendEncryptedPrivateMessage(target, plaintext)) {
                return;
            }
        }
        connection.sendPrivateMessage(target, plaintext);
    }

    // =========================================================================
    // VENTANAS Y DIALOGOS AUXILIARES
    // =========================================================================

    private void openSelfProfileEditor() {
        if (!connection.isConnected()) return;
        ProfileDialogs.showEditProfileDialog(this, myProfile, updated -> {
            this.myProfile = updated;
            this.manualPresence = updated.getPresence();
            profileCache.put(currentUsername.toLowerCase(), updated);
            connection.updateProfile(updated);
            connection.updatePresence(updated.getPresence(), updated.getCustomStatus(), updated.getStatusSymbol());
            updateOwnDock();
            chatFeedPanel.refreshAvatarsForUser(updated);
            userList.repaint();
        });
    }

    private void openProfileCard(String username) {
        if (username == null || username.isBlank()) return;
        UserProfile prof = profileCache.getOrDefault(username.toLowerCase(), new UserProfile(username));
        if (connection.isConnected()) {
            connection.requestProfile(username);
        }
        boolean isSelf = currentUsername.equalsIgnoreCase(username);
        boolean canManageRoles = Permission.hasPermission(myProfile.getPermissions(), Permission.MANAGE_ROLES);
        ProfileDialogs.showUserProfileCard(
                this,
                prof,
                isSelf,
                canManageRoles,
                connection,
                this::openSelfProfileEditor,
                () -> promptDirectMessageTo(username),
                () -> ChannelManagerDialogs.showPermissionsDialog(
                        this, connection, profileCache, new ArrayList<>(channelInfoMap.values()), username
                )
        );
    }

    private void promptDirectMessageTo(String target) {
        boolean isE2ee = connection.hasPeerPublicKey(target);
        String msg = JOptionPane.showInputDialog(
                this,
                (isE2ee ? "[E2EE AES-256-GCM Activado] " : "") + "Mensaje privado para @" + target + ":",
                "Mensaje Directo",
                JOptionPane.PLAIN_MESSAGE
        );
        if (msg != null && !msg.trim().isEmpty()) {
            sendDirectMessageAutoE2ee(target, msg.trim());
        }
    }

    private void showPresenceMenu(Component anchor) {
        JPopupMenu m = new JPopupMenu();
        for (PresenceStatus st : new PresenceStatus[]{PresenceStatus.ONLINE, PresenceStatus.IDLE, PresenceStatus.DND}) {
            JMenuItem item = new JMenuItem(st.getLabel());
            item.addActionListener(e -> {
                manualPresence = st;
                autoIdled = false;
                myProfile.setPresence(st);
                connection.updatePresence(st, myProfile.getCustomStatus(), myProfile.getStatusSymbol());
                updateOwnDock();
            });
            m.add(item);
        }
        m.show(anchor, 0, -90);
    }

    private void showUserContextMenu(Component invoker, int x, int y, String target) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem viewItem = new JMenuItem("Ver Perfil y Biografía");
        viewItem.addActionListener(e -> openProfileCard(target));
        menu.add(viewItem);

        if (!target.equalsIgnoreCase(currentUsername)) {
            JMenuItem dmItem = new JMenuItem("Enviar DM (E2EE)");
            dmItem.addActionListener(e -> promptDirectMessageTo(target));
            menu.add(dmItem);
        }
        if (Permission.hasPermission(myProfile.getPermissions(), Permission.MANAGE_ROLES)) {
            JMenuItem roleItem = new JMenuItem("Gestionar Rol / Permisos");
            roleItem.addActionListener(e -> ChannelManagerDialogs.showPermissionsDialog(
                    this, connection, profileCache, new ArrayList<>(channelInfoMap.values()), target
            ));
            menu.add(roleItem);
        }
        menu.show(invoker, x, y);
    }

    private void openSearchDialog(String initialQuery) {
        if (searchDialog == null) {
            searchDialog = new SearchDialog(this, connection, matched -> {
                if (matched.getChannel() != null && !matched.getChannel().equalsIgnoreCase(connection.getCurrentChannel())) {
                    connection.switchChannel(matched.getChannel());
                }
                if (matched.getId() != null) {
                    SwingUtilities.invokeLater(() -> chatFeedPanel.scrollToMessage(matched.getId()));
                }
            });
        }
        searchDialog.setInitialQuery(initialQuery);
        searchDialog.setVisible(true);
    }

    private void openWhiteboardDialog() {
        if (whiteboardDialog == null) {
            whiteboardDialog = new WhiteboardDialog(this, connection, connection.getCurrentChannel());
        } else {
            whiteboardDialog.setActiveChannel(connection.getCurrentChannel());
        }
        whiteboardDialog.setVisible(true);
    }

    private void toggleJoinVoiceChannel(String channel) {
        if (voiceAudioManager.isActive() && channel.equalsIgnoreCase(voiceAudioManager.getActiveVoiceChannel())) {
            voiceAudioManager.leaveVoice();
            voiceDockPanel.setVisible(false);
        } else {
            boolean hw = voiceAudioManager.joinVoice(channel);
            voiceChannelStatusLbl.setText((hw ? "Voz: " : "Voz (solo escucha): ") + channel);
            voiceDockPanel.setVisible(true);
        }
    }

    // =========================================================================
    // MONITORES: TYPING DEBOUNCE (3s) & AUTO-IDLE DETECTOR
    // =========================================================================

    private void setupTypingAndIdleMonitors() {
        Toolkit.getDefaultToolkit().addAWTEventListener(
                event -> {
                    lastUserActivityMs = System.currentTimeMillis();
                    if (autoIdled && connection.isConnected() && manualPresence == PresenceStatus.ONLINE) {
                        autoIdled = false;
                        myProfile.setPresence(PresenceStatus.ONLINE);
                        connection.updatePresence(PresenceStatus.ONLINE, myProfile.getCustomStatus(), myProfile.getStatusSymbol());
                        SwingUtilities.invokeLater(this::updateOwnDock);
                    }
                },
                AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK
        );

        Timer maintenanceTimer = new Timer(800, e -> {
            long now = System.currentTimeMillis();
            boolean changed = typingExpiryMap.entrySet().removeIf(entry -> entry.getValue() <= now);
            if (changed) {
                refreshTypingIndicator();
            }

            if (connection.isConnected() && manualPresence == PresenceStatus.ONLINE && !autoIdled
                    && (now - lastUserActivityMs >= IDLE_TIMEOUT_MS)) {
                autoIdled = true;
                myProfile.setPresence(PresenceStatus.IDLE);
                connection.updatePresence(PresenceStatus.IDLE, myProfile.getCustomStatus(), myProfile.getStatusSymbol());
                updateOwnDock();
            }
        });
        maintenanceTimer.start();
    }

    private void setupWindowEvents() {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                voiceAudioManager.leaveVoice();
                connection.disconnect();
            }
        });
    }

    // =========================================================================
    // CALLBACKS REACTIVOS (ChatEventListener)
    // =========================================================================

    @Override
    public void onConnected(String username) {
        SwingUtilities.invokeLater(() -> {
            this.currentUsername = username;
            this.myProfile.setUsername(username);
            this.connection.setAutoReconnect(true);

            setTitle("RealTimeChat - @" + username);
            statusLabel.setText("Conectado @" + username);
            statusLabel.setIcon(new StatusDotIcon(new Color(0x23A55A)));
            statusLabel.setForeground(new Color(0x23A55A));

            inputArea.setEnabled(true);
            sendButton.setEnabled(true);
            inputArea.requestFocusInWindow();

            updateOwnDock();
            refreshPermissionControlledUi();
        });
    }

    @Override
    public void onMessageReceived(Message message) {
        SwingUtilities.invokeLater(() -> {
            if (message == null || message.getType() == null) return;

            // Limpiar indicador de escritura inmediatamente al llegar mensaje del remitente
            if (message.getSender() != null) {
                if (typingExpiryMap.remove(message.getSender().toLowerCase()) != null) {
                    refreshTypingIndicator();
                }
            }

            if (message.getThreadParentId() != null) {
                chatFeedPanel.incrementThreadCount(message.getThreadParentId());
                if (threadSidePanel.getRootMessageId() != null
                        && threadSidePanel.getRootMessageId().equals(message.getThreadParentId())) {
                    threadSidePanel.appendReply(message);
                }
                return;
            }

            chatFeedPanel.appendMessage(message, MessageCardPanel.DeliveryState.SENT, false);

            // Notificación sonora respetando estado DND (No molestar)
            boolean isExternal = message.getSender() != null && !message.getSender().equalsIgnoreCase(currentUsername);
            boolean isDmOrMention = message.getType() == MessageType.PRIVATE
                    || (message.getContent() != null && message.getContent().contains("@" + currentUsername));
            if (isExternal && isDmOrMention && myProfile.getPresence() != PresenceStatus.DND && !isFocused()) {
                Toolkit.getDefaultToolkit().beep();
            }
        });
    }

    @Override
    public void onMessageAck(String tempId, Long id, String channel, long timestamp) {
        SwingUtilities.invokeLater(() -> chatFeedPanel.handleAck(tempId, id, timestamp));
    }

    @Override
    public void onMessageSendError(String tempId, Message failedMessage, String error) {
        SwingUtilities.invokeLater(() -> chatFeedPanel.handleSendError(tempId, error));
    }

    @Override
    public void onMessageDeleted(Long messageId, String channel, String deletedBy) {
        SwingUtilities.invokeLater(() -> chatFeedPanel.removeCard(messageId, null));
    }

    @Override
    public void onReactionUpdated(Long messageId, String channel, String sender, String emoji, boolean added, Map<String, List<String>> reactions) {
        SwingUtilities.invokeLater(() -> chatFeedPanel.updateReactions(messageId, reactions));
    }

    @Override
    public void onMessageEmbedUpdated(Long messageId, String channel, EmbedData embed) {
        SwingUtilities.invokeLater(() -> chatFeedPanel.updateEmbed(messageId, embed));
    }

    @Override
    public void onPollUpdated(Long messageId, String channel, PollData poll) {
        SwingUtilities.invokeLater(() -> chatFeedPanel.updatePoll(messageId, poll));
    }

    @Override
    public void onHistoryReceived(String channel, List<Message> history) {
        SwingUtilities.invokeLater(() -> {
            chatFeedPanel.configure(
                    currentUsername,
                    getEffectiveMyChannelPerms(channel),
                    profileCache,
                    buildCardActions(),
                    oldestId -> {
                        if (connection.isConnected()) {
                            connection.requestOlderHistory(connection.getCurrentChannel(), oldestId, 30);
                        }
                    }
            );
            chatFeedPanel.setInitialHistory(history);
        });
    }

    @Override
    public void onHistoryPageReceived(String channel, List<Message> history, boolean hasMore, Long beforeId, Long afterId, Long threadParentId) {
        SwingUtilities.invokeLater(() -> {
            if (threadParentId != null) {
                if (threadParentId.equals(threadSidePanel.getRootMessageId())) {
                    threadSidePanel.setReplies(history);
                }
                return;
            }
            if (afterId != null) {
                if (history != null) {
                    for (Message m : history) {
                        chatFeedPanel.appendMessage(m, MessageCardPanel.DeliveryState.SENT, true);
                    }
                }
            } else {
                chatFeedPanel.prependOlderHistory(history, hasMore);
            }
        });
    }

    @Override
    public void onUserProfilesUpdated(Map<String, UserProfile> profiles) {
        if (profiles == null) return;
        for (Map.Entry<String, UserProfile> e : profiles.entrySet()) {
            if (e.getValue() != null) {
                profileCache.put(e.getKey().toLowerCase(), e.getValue());
                if (e.getKey().equalsIgnoreCase(currentUsername)) {
                    myProfile = e.getValue();
                }
            }
        }
        SwingUtilities.invokeLater(() -> {
            updateOwnDock();
            refreshPermissionControlledUi();
            userList.repaint();
        });
    }

    @Override
    public void onProfileReceived(UserProfile profile) {
        if (profile == null || profile.getUsername() == null) return;
        profileCache.put(profile.getUsername().toLowerCase(), profile);
        SwingUtilities.invokeLater(() -> {
            if (profile.getUsername().equalsIgnoreCase(currentUsername)) {
                myProfile = profile;
                updateOwnDock();
                refreshPermissionControlledUi();
            }
            chatFeedPanel.refreshAvatarsForUser(profile);
            userList.repaint();
        });
    }

    @Override
    public void onPresenceUpdated(String username, PresenceStatus status, String customStatus, String statusSymbol) {
        if (username == null) return;
        UserProfile p = profileCache.computeIfAbsent(username.toLowerCase(), k -> new UserProfile(username));
        p.setPresence(status != null ? status : PresenceStatus.ONLINE);
        p.setCustomStatus(customStatus);
        p.setStatusSymbol(statusSymbol);
        SwingUtilities.invokeLater(() -> {
            if (username.equalsIgnoreCase(currentUsername)) {
                updateOwnDock();
            }
            userList.repaint();
        });
    }

    @Override
    public void onUserListUpdated(List<String> users) {
        SwingUtilities.invokeLater(() -> {
            userListModel.clear();
            if (users != null) {
                users.forEach(userListModel::addElement);
                userCountLabel.setText("MIEMBROS EN CANAL (" + users.size() + ")");
            }
        });
    }

    @Override
    public void onChannelListUpdated(List<String> channels) {
        SwingUtilities.invokeLater(() -> {
            String active = connection.getCurrentChannel();
            channelListModel.clear();
            if (channels != null) {
                channels.forEach(channelListModel::addElement);
            }
            int idx = channelListModel.indexOf(active);
            if (idx >= 0) channelList.setSelectedIndex(idx);
        });
    }

    @Override
    public void onChannelMetadataUpdated(List<ChannelInfo> channels) {
        if (channels == null) return;
        channelInfoMap.clear();
        for (ChannelInfo info : channels) {
            channelInfoMap.put(info.getName().toLowerCase(), info);
        }
        SwingUtilities.invokeLater(() -> {
            updateHeaderChannelInfo(connection.getCurrentChannel());
            refreshPermissionControlledUi();
            channelList.repaint();
        });
    }

    @Override
    public void onChannelChanged(String newChannel) {
        SwingUtilities.invokeLater(() -> {
            typingExpiryMap.clear();
            refreshTypingIndicator();
            updateHeaderChannelInfo(newChannel);
            chatFeedPanel.clearFeed();
            chatFeedPanel.updatePermissions(getEffectiveMyChannelPerms(newChannel));
            int idx = channelListModel.indexOf(newChannel);
            if (idx >= 0) channelList.setSelectedIndex(idx);
            if (whiteboardDialog != null && whiteboardDialog.isVisible()) {
                whiteboardDialog.setActiveChannel(newChannel);
            }
        });
    }

    @Override
    public void onTypingUpdated(String sender, String channel, boolean isTyping) {
        if (sender == null || sender.equalsIgnoreCase(currentUsername)) return;
        if (!channel.equalsIgnoreCase(connection.getCurrentChannel())) return;
        SwingUtilities.invokeLater(() -> {
            if (isTyping) {
                typingExpiryMap.put(sender.toLowerCase(), System.currentTimeMillis() + TYPING_DEBOUNCE_MS);
            } else {
                typingExpiryMap.remove(sender.toLowerCase());
            }
            refreshTypingIndicator();
        });
    }

    @Override
    public void onWebhookListReceived(String channel, List<WebhookInfo> webhooks) {
        SwingUtilities.invokeLater(() -> {
            webhookCacheList.clear();
            webhookListModel.clear();
            if (webhooks != null) {
                for (WebhookInfo w : webhooks) {
                    webhookCacheList.add(w);
                    webhookListModel.addElement(w.getName() + " -> " + w.getUrl());
                }
            }
        });
    }

    @Override
    public void onSearchResultsReceived(String query, List<Message> results) {
        SwingUtilities.invokeLater(() -> {
            if (searchDialog == null) openSearchDialog(query);
            searchDialog.updateResults(query, results);
        });
    }

    @Override
    public void onWhiteboardEvent(String channel, String sender, String payloadJson) {
        SwingUtilities.invokeLater(() -> {
            if (whiteboardDialog != null) {
                whiteboardDialog.handleRemoteEvent(sender, payloadJson);
            }
        });
    }

    @Override
    public void onVoiceStateUpdated(Message stateMsg) {
        if (stateMsg.getSender() != null && stateMsg.getVoiceLevel() != null) {
            voiceLevelsMap.put(stateMsg.getSender().toLowerCase(), stateMsg.getVoiceLevel());
        }
        synchronized (currentVoiceParticipants) {
            currentVoiceParticipants.clear();
            currentVoiceParticipants.addAll(stateMsg.getUsers());
        }
        SwingUtilities.invokeLater(() -> userList.repaint());
    }

    @Override
    public void onVoiceAudioReceived(String channel, String sender, String base64Audio) {
        voiceAudioManager.playRemoteAudio(channel, base64Audio);
    }

    @Override
    public void onDisconnected(String reason) {
        SwingUtilities.invokeLater(this::updateConnectionBannerOffline);
    }

    @Override
    public void onError(String errorMessage) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, errorMessage, "Aviso de Red", JOptionPane.ERROR_MESSAGE));
    }

    // =========================================================================
    // HELPERS INTERNOS DE UI
    // =========================================================================

    private void updateConnectionBannerOffline() {
        int qSize = connection.getOfflineQueueSize();
        statusLabel.setText("Reconectando..." + (qSize > 0 ? " (Cola: " + qSize + ")" : ""));
        statusLabel.setIcon(new StatusDotIcon(new Color(0xF0B232)));
        statusLabel.setForeground(new Color(0xF0B232));
        // Permitir encolar mensajes offline si el usuario ya inició sesión previamente
        boolean hasSession = !currentUsername.isBlank();
        inputArea.setEnabled(hasSession);
        sendButton.setEnabled(hasSession);
    }

    private void updateOwnDock() {
        dockNameLabel.setText(currentUsername.isBlank() ? "Sin sesión" : "@" + currentUsername + " [" + myProfile.getRole() + "]");
        Color pColor = ImageUtils.parseColor(myProfile.getPresence().getColorHex(), new Color(0x23A55A));
        dockAvatarLabel.setIcon(ImageUtils.buildAvatarIcon(currentUsername, myProfile.getAvatar(), myProfile.getAvatarImageBase64(), 36, pColor));
        String tag = myProfile.getStatusSymbol() != null ? myProfile.getStatusSymbol() + " " : "";
        String custom = myProfile.getCustomStatus() != null ? " - " + myProfile.getCustomStatus() : "";
        dockStatusLabel.setText(myProfile.getPresence().getLabel() + " " + tag + custom);
    }

    private void updateHeaderChannelInfo(String channel) {
        channelTitleLabel.setText(channel);
        ChannelInfo info = channelInfoMap.get(channel.toLowerCase());
        channelTopicLabel.setText(info != null && info.getTopic() != null ? "| " + info.getTopic() : "");
    }

    private long getEffectiveMyChannelPerms(String channel) {
        long base = myProfile.getPermissions();
        ChannelInfo info = channelInfoMap.get(channel.toLowerCase());
        if (info == null) return base;
        return Permission.computeEffectivePermissions(base, info.getDefaultAllowMask(), info.getDefaultDenyMask());
    }

    private void refreshPermissionControlledUi() {
        long perms = myProfile.getPermissions();
        boolean canChan = Permission.hasPermission(perms, Permission.MANAGE_CHANNELS);
        boolean canRoles = Permission.hasPermission(perms, Permission.MANAGE_ROLES);
        boolean canHooks = Permission.hasPermission(perms, Permission.MANAGE_WEBHOOKS);
        createChanBtn.setEnabled(canChan);
        deleteChanBtn.setEnabled(canChan);
        permissionsBtn.setEnabled(canRoles || canChan);
        webhooksBtn.setEnabled(canHooks);
    }

    private void refreshTypingIndicator() {
        if (typingExpiryMap.isEmpty()) {
            typingIndicatorLabel.setText(" ");
            return;
        }
        List<String> users = new ArrayList<>();
        for (String key : typingExpiryMap.keySet()) {
            UserProfile p = profileCache.get(key);
            users.add(p != null ? p.getUsername() : key);
        }
        if (users.size() == 1) {
            typingIndicatorLabel.setText("  " + users.get(0) + " está escribiendo...");
        } else {
            typingIndicatorLabel.setText("  " + String.join(", ", users) + " están escribiendo...");
        }
    }

    private static JButton makeHeaderBtn(String text) {
        JButton b = new JButton(text);
        b.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT);
        b.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        b.setBackground(new Color(0x3F4147));
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private static void styleTinySidebarBtn(JButton b) {
        b.setFont(new Font("Segoe UI", Font.BOLD, 12));
        b.setBackground(new Color(0x3F4147));
        b.setForeground(Color.WHITE);
        b.setMargin(new Insets(1, 6, 1, 6));
        b.setFocusPainted(false);
    }

    public void showConnectionDialog() {
        if (activeDialog != null && activeDialog.isVisible()) {
            activeDialog.toFront();
            return;
        }

        JDialog dialog = new JDialog(this, "Conectar a RealTimeChat NextGen", true);
        activeDialog = dialog;
        dialog.setSize(450, 390);
        dialog.setLocationRelativeTo(this);
        dialog.setResizable(false);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(18, 22, 18, 22));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel("RealTimeChat NextGen", JLabel.CENTER);
        ImageIcon dialogIcon = getAppImageIcon(38);
        if (dialogIcon != null) {
            title.setIcon(dialogIcon);
            title.setIconTextGap(10);
        }
        title.setFont(new Font("Segoe UI", Font.BOLD, 19));
        title.setForeground(new Color(0x5865F2));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        panel.add(title, gbc);

        gbc.gridwidth = 1;
        gbc.gridy = 1;
        panel.add(new JLabel("Servidor IP / Host:"), gbc);
        JTextField hostField = new JTextField(currentServerHost, 18);
        gbc.gridx = 1;
        panel.add(hostField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        panel.add(new JLabel("Puerto TCP:"), gbc);
        JTextField portField = new JTextField(String.valueOf(currentServerPort), 18);
        gbc.gridx = 1;
        panel.add(portField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        panel.add(new JLabel("Nickname:"), gbc);
        JTextField userField = new JTextField(currentUsername, 18);
        gbc.gridx = 1;
        panel.add(userField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        panel.add(new JLabel("Avatar Preset:"), gbc);
        JComboBox<String> avatarCombo = new JComboBox<>(new String[]{
                "cyber-neon", "synthwave", "cosmic", "emerald", "sunset", "bot-matrix", "golden", "retro-pixel"
        });
        avatarCombo.setSelectedItem(myProfile.getAvatar());
        gbc.gridx = 1;
        panel.add(avatarCombo, gbc);

        JLabel feedback = new JLabel(" ");
        feedback.setForeground(new Color(0xF23F43));
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 2;
        panel.add(feedback, gbc);

        JButton connectBtn = new JButton("Entrar al Chat");
        connectBtn.setBackground(new Color(0x5865F2));
        connectBtn.setForeground(Color.WHITE);
        connectBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        gbc.gridy = 6;
        panel.add(connectBtn, gbc);

        connectBtn.addActionListener(e -> {
            String host = hostField.getText().trim();
            String portStr = portField.getText().trim();
            String nick = userField.getText().trim();
            if (host.isEmpty() || nick.isEmpty()) {
                feedback.setText("Completa host y nickname.");
                return;
            }
            int port;
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException ex) {
                feedback.setText("Puerto inválido.");
                return;
            }
            connectBtn.setEnabled(false);
            feedback.setText("Conectando...");

            new Thread(() -> {
                boolean ok = connection.connect(host, port, nick, this);
                SwingUtilities.invokeLater(() -> {
                    connectBtn.setEnabled(true);
                    if (ok) {
                        currentServerHost = host;
                        currentServerPort = port;
                        myProfile.setUsername(nick);
                        myProfile.setAvatar((String) avatarCombo.getSelectedItem());
                        connection.updateProfile(myProfile);
                        dialog.dispose();
                    } else {
                        feedback.setText("No se pudo conectar (nick ocupado o servidor caído).");
                    }
                });
            }, "gui-connect").start();
        });

        dialog.setContentPane(panel);
        dialog.getRootPane().setDefaultButton(connectBtn);
        dialog.setVisible(true);
    }

    private class MemberListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            String name = value != null ? value.toString() : "";
            UserProfile prof = profileCache.get(name.toLowerCase());
            PresenceStatus presence = prof != null ? prof.getPresence() : PresenceStatus.ONLINE;
            Color ringColor = ImageUtils.parseColor(presence.getColorHex(), new Color(0x23A55A));

            Double voiceLevel = voiceLevelsMap.get(name.toLowerCase());
            boolean speaking = currentVoiceParticipants.stream().anyMatch(u -> u.equalsIgnoreCase(name))
                    && voiceLevel != null && voiceLevel > 0.05;
            if (speaking) {
                ringColor = new Color(0x57F287);
            }

            String roleTag = "";
            if (prof != null && Permission.ROLE_ADMIN.equalsIgnoreCase(prof.getRole())) roleTag = " [ADMIN]";
            else if (prof != null && Permission.ROLE_MODERATOR.equalsIgnoreCase(prof.getRole())) roleTag = " [MOD]";

            String custom = prof != null && prof.getCustomStatus() != null ? prof.getCustomStatus() : "";
            String sym = prof != null && prof.getStatusSymbol() != null ? prof.getStatusSymbol() + " " : "";
            String subLine = speaking ? "[Hablando...]" : (sym + custom).trim();

            String html = subLine.isEmpty()
                    ? "<html><b>" + name + "</b><span style='color:#949BA4;font-size:10px;'>" + roleTag + "</span></html>"
                    : "<html><b>" + name + "</b><span style='color:#949BA4;font-size:10px;'>" + roleTag + "</span><br/><span style='color:#949BA4;font-size:10px;'>" + subLine.replace("<", "&lt;") + "</span></html>";

            JLabel lbl = (JLabel) super.getListCellRendererComponent(list, html, index, isSelected, cellHasFocus);
            lbl.setIcon(ImageUtils.buildAvatarIcon(
                    name,
                    prof != null ? prof.getAvatar() : "cyber-neon",
                    prof != null ? prof.getAvatarImageBase64() : null,
                    28,
                    ringColor
            ));
            lbl.setIconTextGap(8);
            lbl.setBorder(new EmptyBorder(4, 6, 4, 6));
            lbl.setBackground(isSelected ? new Color(0x404249) : new Color(0x2B2D31));
            lbl.setForeground(name.equalsIgnoreCase(currentUsername) ? new Color(0x949CF7) : new Color(0xDBDEE1));
            return lbl;
        }
    }

    private static class StatusDotIcon implements javax.swing.Icon {
        private final Color color;
        StatusDotIcon(Color color) { this.color = color; }
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillOval(x + 1, y + 2, 9, 9);
            g2.dispose();
        }
        @Override public int getIconWidth() { return 12; }
        @Override public int getIconHeight() { return 12; }
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
