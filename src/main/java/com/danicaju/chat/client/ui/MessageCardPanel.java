package com.danicaju.chat.client.ui;

import com.danicaju.chat.model.EmbedData;
import com.danicaju.chat.model.Message;
import com.danicaju.chat.model.MessageType;
import com.danicaju.chat.model.PollData;
import com.danicaju.chat.model.UserProfile;
import com.danicaju.chat.security.Permission;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Image;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Tarjeta visual modular estilo Discord para un mensaje individual.
 * Soporta estados optimistas (sending/sent/error), avatares custom circulares,
 * citas interactivas, reacciones delta, encuestas en vivo, adjuntos e embeds OpenGraph.
 */
public class MessageCardPanel extends JPanel {

    public enum DeliveryState {
        SENDING, SENT, ERROR
    }

    public interface CardActions {
        void onReplyClicked(Message msg);
        void onOpenThreadClicked(Message msg);
        void onReactionToggle(Message msg, String emoji, boolean add);
        void onDeleteClicked(Message msg);
        void onRetryClicked(Message msg);
        void onDiscardErrorClicked(Message msg);
        void onJumpToMessage(Long messageId);
        void onPollVote(Message msg, int optionIndex);
        void onUserClicked(String username);
    }

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault());

    private static final String[] QUICK_REACTIONS = {"\uD83D\uDC4D", "\u2764\uFE0F", "\uD83D\uDD25", "\uD83D\uDE80", "\uD83D\uDC40", "\u2705"};

    private static final Color BG_CARD = new Color(0x313338);
    private static final Color BG_ERROR = new Color(0x3F272B);
    private static final Color BG_HIGHLIGHT = new Color(0x3F436E);

    private final Message message;
    private final String currentUsername;
    private final long viewerChannelPerms;
    private final CardActions actions;

    private DeliveryState deliveryState;
    private String errorText;

    private final JLabel avatarLabel;
    private final JLabel timeLabel;
    private final JLabel stateLabel;
    private final JPanel bodyContainer;
    private final JPanel errorBar;
    private final JPanel reactionsBar;
    private final JPanel pollContainer;
    private final JPanel embedContainer;
    private final JPanel threadBadgeBar;

    public MessageCardPanel(
            Message message,
            UserProfile senderProfile,
            String currentUsername,
            long viewerChannelPerms,
            DeliveryState initialState,
            CardActions actions
    ) {
        this.message = message;
        this.currentUsername = currentUsername;
        this.viewerChannelPerms = viewerChannelPerms;
        this.deliveryState = initialState;
        this.actions = actions;

        setLayout(new BorderLayout(10, 4));
        setOpaque(true);
        setBackground(BG_CARD);
        setBorder(BorderFactory.createEmptyBorder(7, 12, 7, 12));
        setAlignmentX(Component.LEFT_ALIGNMENT);

        if (message.getType() == MessageType.SYSTEM) {
            JLabel sys = new JLabel("[SISTEMA] " + (message.getContent() != null ? message.getContent() : ""));
            sys.setFont(new Font("Segoe UI", Font.ITALIC, 12));
            sys.setForeground(new Color(0x949BA4));
            add(sys, BorderLayout.CENTER);
            avatarLabel = new JLabel();
            timeLabel = new JLabel();
            stateLabel = new JLabel();
            bodyContainer = new JPanel();
            errorBar = new JPanel();
            reactionsBar = new JPanel();
            pollContainer = new JPanel();
            embedContainer = new JPanel();
            threadBadgeBar = new JPanel();
            return;
        }

        // Columna izquierda: Avatar circular (custom upload o preset)
        avatarLabel = new JLabel();
        avatarLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        avatarLabel.setVerticalAlignment(JLabel.TOP);
        refreshAvatar(senderProfile);
        avatarLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (actions != null && message.getSender() != null) {
                    actions.onUserClicked(message.getSender());
                }
            }
        });
        JPanel avatarWrapper = new JPanel(new BorderLayout());
        avatarWrapper.setOpaque(false);
        avatarWrapper.add(avatarLabel, BorderLayout.NORTH);
        add(avatarWrapper, BorderLayout.WEST);

        // Columna principal
        JPanel main = new JPanel();
        main.setOpaque(false);
        main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));

        // 1. Cita compacta superior si es respuesta a otro mensaje
        if (message.getReplyToId() != null) {
            JPanel replyBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 1));
            replyBar.setOpaque(false);
            replyBar.setAlignmentX(Component.LEFT_ALIGNMENT);
            replyBar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            String preview = message.getReplyPreview() != null ? message.getReplyPreview() : "Mensaje #" + message.getReplyToId();
            String rSender = message.getReplySender() != null ? message.getReplySender() : "usuario";
            JLabel replyLbl = new JLabel("  |_ En respuesta a @" + rSender + ": " + preview);
            replyLbl.setFont(new Font("Segoe UI", Font.ITALIC, 11));
            replyLbl.setForeground(new Color(0x949BA4));
            replyBar.add(replyLbl);
            replyBar.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (actions != null) actions.onJumpToMessage(message.getReplyToId());
                }
            });
            main.add(replyBar);
        }

        // 2. Cabecera: Autor + Badges + Timestamp + Estado + Acciones
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel authorGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        authorGroup.setOpaque(false);

        String displayAuthor = message.getSender() != null ? message.getSender() : "Sistema";
        if (message.getType() == MessageType.PRIVATE) {
            if (currentUsername != null && currentUsername.equalsIgnoreCase(displayAuthor)) {
                displayAuthor = "DM a @" + message.getTarget();
            } else {
                displayAuthor = "DM de @" + displayAuthor;
            }
        }
        JLabel authorLbl = new JLabel(displayAuthor);
        authorLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        authorLbl.setForeground(resolveAuthorColor(message, senderProfile));
        authorLbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        authorLbl.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (actions != null && message.getSender() != null) actions.onUserClicked(message.getSender());
            }
        });
        authorGroup.add(authorLbl);

        if ("RealTimeBot".equalsIgnoreCase(message.getSender())) {
            authorGroup.add(makeBadge("BOT", new Color(0x5865F2)));
        } else if (message.getSender() != null && message.getSender().contains("[Webhook]")) {
            authorGroup.add(makeBadge("WEBHOOK", new Color(0x10B981)));
        }
        if (Boolean.TRUE.equals(message.getEncrypted())) {
            authorGroup.add(makeBadge("E2EE", new Color(0x23A55A)));
        }

        timeLabel = new JLabel(TIME_FMT.format(Instant.ofEpochMilli(message.getTimestamp())));
        timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        timeLabel.setForeground(new Color(0x949BA4));
        authorGroup.add(timeLabel);

        stateLabel = new JLabel();
        stateLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        authorGroup.add(stateLabel);

        header.add(authorGroup, BorderLayout.WEST);

        // Botonera compacta derecha
        if (actions != null) {
            JPanel quickBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            quickBtns.setOpaque(false);

            JButton reactBtn = makeMiniBtn("+Reac");
            reactBtn.addActionListener(e -> showReactionMenu(reactBtn));
            quickBtns.add(reactBtn);

            if (message.getType() == MessageType.CHAT && message.getThreadParentId() == null) {
                JButton replyBtn = makeMiniBtn("Responder");
                replyBtn.addActionListener(e -> actions.onReplyClicked(message));
                quickBtns.add(replyBtn);

                JButton threadBtn = makeMiniBtn("Hilo");
                threadBtn.addActionListener(e -> actions.onOpenThreadClicked(message));
                quickBtns.add(threadBtn);
            }

            boolean isAuthor = currentUsername != null && currentUsername.equalsIgnoreCase(message.getSender());
            boolean canDelete = isAuthor || Permission.hasPermission(viewerChannelPerms, Permission.MANAGE_MESSAGES);
            if (canDelete && message.getId() != null) {
                JButton delBtn = makeMiniBtn("Borrar");
                delBtn.setForeground(new Color(0xF87171));
                delBtn.addActionListener(e -> actions.onDeleteClicked(message));
                quickBtns.add(delBtn);
            }
            header.add(quickBtns, BorderLayout.EAST);
        }
        main.add(header);

        // 3. Cuerpo Markdown + Código + Imágenes adjuntas
        bodyContainer = new JPanel();
        bodyContainer.setOpaque(false);
        bodyContainer.setLayout(new BoxLayout(bodyContainer, BoxLayout.Y_AXIS));
        bodyContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
        renderBody();
        main.add(bodyContainer);

        // 4. Encuesta interactiva
        pollContainer = new JPanel();
        pollContainer.setOpaque(false);
        pollContainer.setLayout(new BoxLayout(pollContainer, BoxLayout.Y_AXIS));
        pollContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
        updatePoll(message.getPoll());
        main.add(pollContainer);

        // 5. Embed OpenGraph / Webhook
        embedContainer = new JPanel();
        embedContainer.setOpaque(false);
        embedContainer.setLayout(new BoxLayout(embedContainer, BoxLayout.Y_AXIS));
        embedContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
        updateEmbed(message.getEmbed());
        main.add(embedContainer);

        // 6. Reacciones delta en vivo
        reactionsBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 3));
        reactionsBar.setOpaque(false);
        reactionsBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        updateReactions(message.getReactions());
        main.add(reactionsBar);

        // 7. Pill de Hilo activo
        threadBadgeBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        threadBadgeBar.setOpaque(false);
        threadBadgeBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        updateThreadReplyCount(message.getThreadReplyCount());
        main.add(threadBadgeBar);

        // 8. Barra de error optimista (Reintentar / Eliminar)
        errorBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        errorBar.setOpaque(false);
        errorBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        main.add(errorBar);

        add(main, BorderLayout.CENTER);
        applyDeliveryVisuals();
    }

    public Message getMessage() {
        return message;
    }

    public void refreshAvatar(UserProfile profile) {
        if (avatarLabel == null) return;
        String preset = profile != null ? profile.getAvatar() : "cyber-neon";
        String custom = profile != null ? profile.getAvatarImageBase64() : null;
        avatarLabel.setIcon(ImageUtils.buildAvatarIcon(message.getSender(), preset, custom, 38, null));
    }

    public void markSent(Long realId, long serverTimestamp) {
        if (realId != null) message.setId(realId);
        if (serverTimestamp > 0) {
            message.setTimestamp(serverTimestamp);
            timeLabel.setText(TIME_FMT.format(Instant.ofEpochMilli(serverTimestamp)));
        }
        this.deliveryState = DeliveryState.SENT;
        this.errorText = null;
        applyDeliveryVisuals();
    }

    public void markError(String error) {
        this.deliveryState = DeliveryState.ERROR;
        this.errorText = error != null ? error : "Error al enviar";
        applyDeliveryVisuals();
    }

    public void markSending() {
        this.deliveryState = DeliveryState.SENDING;
        this.errorText = null;
        applyDeliveryVisuals();
    }

    public void flashHighlight() {
        setBackground(BG_HIGHLIGHT);
        repaint();
        Timer t = new Timer(1400, e -> {
            setBackground(deliveryState == DeliveryState.ERROR ? BG_ERROR : BG_CARD);
            repaint();
        });
        t.setRepeats(false);
        t.start();
    }

    public void updateReactions(Map<String, List<String>> reactions) {
        message.setReactions(reactions);
        reactionsBar.removeAll();
        if (reactions == null || reactions.isEmpty()) {
            reactionsBar.setVisible(false);
            revalidate();
            repaint();
            return;
        }
        reactionsBar.setVisible(true);
        for (Map.Entry<String, List<String>> entry : reactions.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) continue;
            String emoji = entry.getKey();
            List<String> users = entry.getValue();
            boolean iReacted = currentUsername != null && users.stream().anyMatch(u -> u.equalsIgnoreCase(currentUsername));

            JButton pill = new JButton(emoji + " " + users.size());
            pill.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 12));
            pill.setFocusPainted(false);
            pill.setBackground(iReacted ? new Color(0x3C4270) : new Color(0x2B2D31));
            pill.setForeground(iReacted ? new Color(0xC9CDFB) : new Color(0xDBDEE1));
            pill.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(iReacted ? new Color(0x5865F2) : new Color(0x3F4147)),
                    BorderFactory.createEmptyBorder(2, 7, 2, 7)
            ));
            pill.setToolTipText("Reaccionaron: " + String.join(", ", users));
            pill.addActionListener(e -> {
                if (actions != null) actions.onReactionToggle(message, emoji, !iReacted);
            });
            reactionsBar.add(pill);
        }
        reactionsBar.revalidate();
        reactionsBar.repaint();
    }

    public void updatePoll(PollData poll) {
        message.setPoll(poll);
        pollContainer.removeAll();
        if (poll == null) {
            pollContainer.setVisible(false);
            revalidate();
            repaint();
            return;
        }
        pollContainer.setVisible(true);
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBackground(new Color(0x2B2D31));
        box.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x5865F2), 1),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        box.setAlignmentX(Component.LEFT_ALIGNMENT);

        int total = poll.getTotalVotes();
        JLabel qLbl = new JLabel("[ENCUESTA] " + poll.getQuestion() + "  (" + total + " votos)");
        qLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        qLbl.setForeground(new Color(0xF2F3F5));
        box.add(qLbl);
        box.add(Box.createVerticalStrut(6));

        List<String> opts = poll.getOptions();
        for (int i = 0; i < opts.size(); i++) {
            final int optIdx = i;
            int count = poll.getVoteCount(i);
            int pct = total > 0 ? (int) Math.round(count * 100.0 / total) : 0;
            boolean voted = poll.hasVoted(i, currentUsername);

            JPanel row = new JPanel(new BorderLayout(8, 2));
            row.setOpaque(false);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);

            JButton voteBtn = new JButton((voted ? "[X] " : "[ ] ") + opts.get(i));
            voteBtn.setFont(new Font("Segoe UI", voted ? Font.BOLD : Font.PLAIN, 12));
            voteBtn.setHorizontalAlignment(JButton.LEFT);
            voteBtn.setBackground(voted ? new Color(0x3C4270) : new Color(0x1E1F22));
            voteBtn.setForeground(Color.WHITE);
            voteBtn.setFocusPainted(false);
            voteBtn.addActionListener(e -> {
                if (actions != null) actions.onPollVote(message, optIdx);
            });

            JProgressBar bar = new JProgressBar(0, 100);
            bar.setValue(pct);
            bar.setStringPainted(true);
            bar.setString(count + " (" + pct + "%)");
            bar.setForeground(voted ? new Color(0x5865F2) : new Color(0x4E5058));
            bar.setBackground(new Color(0x1E1F22));
            bar.setPreferredSize(new Dimension(110, 22));

            row.add(voteBtn, BorderLayout.CENTER);
            row.add(bar, BorderLayout.EAST);
            box.add(row);
            box.add(Box.createVerticalStrut(4));
        }

        pollContainer.add(box);
        pollContainer.revalidate();
        pollContainer.repaint();
    }

    public void updateEmbed(EmbedData embed) {
        message.setEmbed(embed);
        embedContainer.removeAll();
        if (embed == null) {
            embedContainer.setVisible(false);
            revalidate();
            repaint();
            return;
        }
        embedContainer.setVisible(true);
        Color accent = ImageUtils.parseColor(embed.getColorHex(), new Color(0x5865F2));
        JPanel card = new JPanel(new BorderLayout(8, 4));
        card.setBackground(new Color(0x2B2D31));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, accent),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel textPart = new JPanel();
        textPart.setOpaque(false);
        textPart.setLayout(new BoxLayout(textPart, BoxLayout.Y_AXIS));

        if (embed.getSiteName() != null) {
            JLabel site = new JLabel(embed.getSiteName());
            site.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            site.setForeground(new Color(0x949BA4));
            textPart.add(site);
        }
        if (embed.getTitle() != null) {
            JLabel title = new JLabel("<html><u>" + embed.getTitle().replace("<", "&lt;") + "</u></html>");
            title.setFont(new Font("Segoe UI", Font.BOLD, 13));
            title.setForeground(new Color(0x00A8FC));
            if (embed.getUrl() != null) {
                title.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                title.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        try {
                            if (Desktop.isDesktopSupported()) {
                                Desktop.getDesktop().browse(URI.create(embed.getUrl()));
                            }
                        } catch (Exception ignored) {
                        }
                    }
                });
            }
            textPart.add(title);
        }
        if (embed.getDescription() != null) {
            JLabel desc = new JLabel("<html><body style='width:340px;'>" + embed.getDescription().replace("<", "&lt;") + "</body></html>");
            desc.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            desc.setForeground(new Color(0xDBDEE1));
            textPart.add(Box.createVerticalStrut(3));
            textPart.add(desc);
        }
        card.add(textPart, BorderLayout.CENTER);

        if (embed.getImageUrl() != null && embed.getImageUrl().startsWith("http")) {
            JLabel thumb = new JLabel();
            card.add(thumb, BorderLayout.SOUTH);
            CompletableFuture.runAsync(() -> {
                try {
                    BufferedImage img = ImageIO.read(URI.create(embed.getImageUrl()).toURL());
                    if (img != null) {
                        BufferedImage scaled = ImageUtils.scaleToMaxDimensions(img, 280, 140);
                        if (scaled != null) {
                            SwingUtilities.invokeLater(() -> {
                                thumb.setIcon(new ImageIcon(scaled));
                                embedContainer.revalidate();
                                embedContainer.repaint();
                            });
                        }
                    }
                } catch (Exception ignored) {
                }
            });
        }

        embedContainer.add(card);
        embedContainer.revalidate();
        embedContainer.repaint();
    }

    public void updateThreadReplyCount(Integer count) {
        message.setThreadReplyCount(count);
        threadBadgeBar.removeAll();
        if (count == null || count <= 0 || message.getThreadParentId() != null) {
            threadBadgeBar.setVisible(false);
            revalidate();
            return;
        }
        threadBadgeBar.setVisible(true);
        JButton openBtn = new JButton(count + (count == 1 ? " respuesta en hilo -> Abrir" : " respuestas en hilo -> Abrir"));
        openBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        openBtn.setBackground(new Color(0x2B2D31));
        openBtn.setForeground(new Color(0x949CF7));
        openBtn.setFocusPainted(false);
        openBtn.addActionListener(e -> {
            if (actions != null) actions.onOpenThreadClicked(message);
        });
        threadBadgeBar.add(openBtn);
        threadBadgeBar.revalidate();
    }

    private void renderBody() {
        bodyContainer.removeAll();
        if (message.getContent() != null && !message.getContent().isBlank()) {
            bodyContainer.add(MarkdownCodeRenderer.buildContentPanel(message.getContent()));
        }
        if (message.getAttachmentBase64() != null && !message.getAttachmentBase64().isBlank()) {
            BufferedImage img = ImageUtils.decodeBase64(message.getAttachmentBase64());
            if (img != null) {
                BufferedImage preview = ImageUtils.scaleToMaxDimensions(img, 360, 220);
                JLabel imgLbl = new JLabel(new ImageIcon(preview));
                imgLbl.setBorder(BorderFactory.createLineBorder(new Color(0x3F4147)));
                imgLbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                imgLbl.setToolTipText((message.getAttachmentName() != null ? message.getAttachmentName() : "Imagen") + " (clic para ampliar)");
                imgLbl.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        showImageLightbox(img, message.getAttachmentName());
                    }
                });
                bodyContainer.add(Box.createVerticalStrut(4));
                bodyContainer.add(imgLbl);
            }
        }
    }

    private void showImageLightbox(BufferedImage fullImg, String name) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog d = new JDialog(owner, name != null ? name : "Vista previa de imagen", JDialog.ModalityType.MODELESS);
        Image scaled = ImageUtils.scaleToMaxDimensions(fullImg, 900, 680);
        JLabel lbl = new JLabel(new ImageIcon(scaled != null ? scaled : fullImg));
        d.add(new JScrollPane(lbl));
        d.pack();
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
    }

    private void applyDeliveryVisuals() {
        if (message.getType() == MessageType.SYSTEM) return;
        errorBar.removeAll();
        switch (deliveryState) {
            case SENDING -> {
                setBackground(BG_CARD);
                stateLabel.setText("(enviando...)");
                stateLabel.setForeground(new Color(0x949BA4));
                bodyContainer.setEnabled(false);
                errorBar.setVisible(false);
            }
            case SENT -> {
                setBackground(BG_CARD);
                stateLabel.setText("");
                bodyContainer.setEnabled(true);
                errorBar.setVisible(false);
            }
            case ERROR -> {
                setBackground(BG_ERROR);
                stateLabel.setText("[" + (errorText != null ? errorText : "Error de envío") + "]");
                stateLabel.setForeground(new Color(0xF87171));
                errorBar.setVisible(true);

                JButton retryBtn = makeMiniBtn("Reintentar");
                retryBtn.setBackground(new Color(0x5865F2));
                retryBtn.setForeground(Color.WHITE);
                retryBtn.addActionListener(e -> {
                    if (actions != null) actions.onRetryClicked(message);
                });

                JButton discardBtn = makeMiniBtn("Eliminar");
                discardBtn.setForeground(new Color(0xF87171));
                discardBtn.addActionListener(e -> {
                    if (actions != null) actions.onDiscardErrorClicked(message);
                });

                errorBar.add(retryBtn);
                errorBar.add(discardBtn);
            }
        }
        revalidate();
        repaint();
    }

    private void showReactionMenu(Component anchor) {
        JPopupMenu popup = new JPopupMenu();
        for (String emoji : QUICK_REACTIONS) {
            JMenuItem item = new JMenuItem(emoji);
            item.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 14));
            item.addActionListener(e -> {
                if (actions != null) {
                    boolean already = false;
                    if (message.getReactions() != null && message.getReactions().containsKey(emoji) && currentUsername != null) {
                        already = message.getReactions().get(emoji).stream().anyMatch(u -> u.equalsIgnoreCase(currentUsername));
                    }
                    actions.onReactionToggle(message, emoji, !already);
                }
            });
            popup.add(item);
        }
        popup.show(anchor, 0, anchor.getHeight());
    }

    private static JButton makeMiniBtn(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        b.setBackground(new Color(0x2B2D31));
        b.setForeground(new Color(0xB5BAC1));
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        return b;
    }

    private static JLabel makeBadge(String text, Color bg) {
        JLabel l = new JLabel(" " + text + " ");
        l.setOpaque(true);
        l.setBackground(bg);
        l.setForeground(Color.WHITE);
        l.setFont(new Font("Segoe UI", Font.BOLD, 10));
        return l;
    }

    private Color resolveAuthorColor(Message msg, UserProfile profile) {
        if (msg.getType() == MessageType.PRIVATE) return new Color(0xEB459E);
        if ("RealTimeBot".equalsIgnoreCase(msg.getSender())) return new Color(0x57F287);
        if (profile != null && Permission.ROLE_ADMIN.equalsIgnoreCase(profile.getRole())) return new Color(0xF0B232);
        if (profile != null && Permission.ROLE_MODERATOR.equalsIgnoreCase(profile.getRole())) return new Color(0x00A8FC);
        if (currentUsername != null && currentUsername.equalsIgnoreCase(msg.getSender())) return new Color(0x949CF7);
        return Color.WHITE;
    }
}
