package com.danicaju.chat.client.ui;

import com.danicaju.chat.model.Message;
import com.danicaju.chat.model.UserProfile;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Panel lateral de Hilos (Threads) estilo Discord para conversaciones secundarias
 * anidadas sobre cualquier mensaje sin saturar el canal principal.
 */
public class ThreadSidePanel extends JPanel {

    private Long rootMessageId;
    private String channelName;

    private final JLabel titleLabel;
    private final JLabel rootSummaryLabel;
    private final JPanel repliesContainer;
    private final JScrollPane scrollPane;
    private final JTextField replyInput;

    private Map<String, UserProfile> profileCache;
    private String currentUsername;
    private BiConsumer<Long, String> onSendReply;

    public ThreadSidePanel(Runnable onClose) {
        setLayout(new BorderLayout(0, 8));
        setPreferredSize(new Dimension(340, 0));
        setBackground(new Color(0x2B2D31));
        setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(0x1E1F22)));
        setVisible(false);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(0x232428));
        header.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 10));

        titleLabel = new JLabel("Hilo");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleLabel.setForeground(Color.WHITE);

        JButton closeBtn = new JButton("X");
        closeBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        closeBtn.setBackground(new Color(0x3F4147));
        closeBtn.setForeground(Color.WHITE);
        closeBtn.setFocusPainted(false);
        closeBtn.addActionListener(e -> {
            setVisible(false);
            if (onClose != null) onClose.run();
        });

        header.add(titleLabel, BorderLayout.WEST);
        header.add(closeBtn, BorderLayout.EAST);

        rootSummaryLabel = new JLabel(" ");
        rootSummaryLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        rootSummaryLabel.setForeground(new Color(0xB5BAC1));
        rootSummaryLabel.setBorder(BorderFactory.createEmptyBorder(4, 12, 6, 12));

        JPanel north = new JPanel(new BorderLayout());
        north.setBackground(new Color(0x2B2D31));
        north.add(header, BorderLayout.NORTH);
        north.add(rootSummaryLabel, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        repliesContainer = new JPanel();
        repliesContainer.setLayout(new BoxLayout(repliesContainer, BoxLayout.Y_AXIS));
        repliesContainer.setBackground(new Color(0x313338));

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(new Color(0x313338));
        wrap.add(repliesContainer, BorderLayout.NORTH);

        scrollPane = new JScrollPane(wrap);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(14);
        add(scrollPane, BorderLayout.CENTER);

        JPanel inputBar = new JPanel(new BorderLayout(6, 0));
        inputBar.setBackground(new Color(0x232428));
        inputBar.setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));

        replyInput = new JTextField();
        replyInput.setBackground(new Color(0x1E1F22));
        replyInput.setForeground(Color.WHITE);
        replyInput.setCaretColor(Color.WHITE);
        replyInput.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        JButton sendBtn = new JButton("Enviar");
        sendBtn.setBackground(new Color(0x5865F2));
        sendBtn.setForeground(Color.WHITE);
        sendBtn.setFocusPainted(false);

        Runnable submit = () -> {
            String txt = replyInput.getText().trim();
            if (!txt.isEmpty() && rootMessageId != null && onSendReply != null) {
                onSendReply.accept(rootMessageId, txt);
                replyInput.setText("");
            }
        };
        sendBtn.addActionListener(e -> submit.run());
        replyInput.addActionListener(e -> submit.run());

        inputBar.add(replyInput, BorderLayout.CENTER);
        inputBar.add(sendBtn, BorderLayout.EAST);
        add(inputBar, BorderLayout.SOUTH);
    }

    public void configure(String currentUsername, Map<String, UserProfile> profileCache, BiConsumer<Long, String> onSendReply) {
        this.currentUsername = currentUsername;
        this.profileCache = profileCache;
        this.onSendReply = onSendReply;
    }

    public void openThread(Message rootMessage) {
        if (rootMessage == null || rootMessage.getId() == null) return;
        this.rootMessageId = rootMessage.getId();
        this.channelName = rootMessage.getChannel();
        titleLabel.setText("Hilo en " + (channelName != null ? channelName : "#general"));
        String preview = rootMessage.getContent() != null ? rootMessage.getContent() : "";
        if (preview.length() > 70) preview = preview.substring(0, 67) + "...";
        rootSummaryLabel.setText("<html><b>@" + rootMessage.getSender() + "</b>: " + preview.replace("<", "&lt;") + "</html>");
        repliesContainer.removeAll();
        repliesContainer.revalidate();
        repliesContainer.repaint();
        setVisible(true);
    }

    public Long getRootMessageId() {
        return isVisible() ? rootMessageId : null;
    }

    public void setReplies(List<Message> replies) {
        repliesContainer.removeAll();
        if (replies != null) {
            for (Message r : replies) {
                addReplyCard(r);
            }
        }
        repliesContainer.revalidate();
        repliesContainer.repaint();
        scrollToBottom();
    }

    public void appendReply(Message reply) {
        if (reply == null || rootMessageId == null || !rootMessageId.equals(reply.getThreadParentId())) {
            return;
        }
        addReplyCard(reply);
        repliesContainer.revalidate();
        repliesContainer.repaint();
        scrollToBottom();
    }

    private void addReplyCard(Message r) {
        UserProfile prof = (profileCache != null && r.getSender() != null)
                ? profileCache.get(r.getSender().toLowerCase()) : null;
        MessageCardPanel card = new MessageCardPanel(r, prof, currentUsername, 0L, MessageCardPanel.DeliveryState.SENT, null);
        repliesContainer.add(card);
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar bar = scrollPane.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        });
    }
}
