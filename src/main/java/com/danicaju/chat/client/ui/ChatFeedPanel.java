package com.danicaju.chat.client.ui;

import com.danicaju.chat.model.EmbedData;
import com.danicaju.chat.model.Message;
import com.danicaju.chat.model.PollData;
import com.danicaju.chat.model.UserProfile;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongConsumer;

/**
 * Contenedor scrollable de mensajes estilo Discord con:
 * - Paginación infinita hacia arriba sin saltos visuales (beforeId)
 * - Ventana virtualizada (poda de nodos antiguos cuando supera MAX_RENDERED_CARDS)
 * - Reconciliación optimista (tempId -> id real) y actualizaciones reactivas in-place
 */
public class ChatFeedPanel extends JPanel {

    public static final int MAX_RENDERED_CARDS = 160;

    private final JPanel listContainer;
    private final JScrollPane scrollPane;
    private final JLabel topStatusLabel;

    private final List<MessageCardPanel> cards = new ArrayList<>();
    private final Map<Long, MessageCardPanel> byMessageId = new LinkedHashMap<>();
    private final Map<String, MessageCardPanel> byTempId = new LinkedHashMap<>();

    private String currentUsername;
    private long viewerChannelPerms;
    private Map<String, UserProfile> profileCache = new LinkedHashMap<>();
    private MessageCardPanel.CardActions cardActions;

    private boolean hasMoreOlder = true;
    private boolean loadingOlder = false;
    private LongConsumer loadOlderCallback;

    public ChatFeedPanel() {
        setLayout(new BorderLayout());
        setBackground(new Color(0x313338));

        topStatusLabel = new JLabel("Inicio del historial", SwingConstants.CENTER);
        topStatusLabel.setForeground(new Color(0x949BA4));
        topStatusLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        topStatusLabel.setBorder(javax.swing.BorderFactory.createEmptyBorder(6, 0, 6, 0));

        listContainer = new JPanel();
        listContainer.setLayout(new BoxLayout(listContainer, BoxLayout.Y_AXIS));
        listContainer.setBackground(new Color(0x313338));
        listContainer.add(topStatusLabel);
        listContainer.add(Box.createVerticalGlue());

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(new Color(0x313338));
        wrapper.add(listContainer, BorderLayout.NORTH);

        scrollPane = new JScrollPane(wrapper);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);
        scrollPane.getViewport().setBackground(new Color(0x313338));

        scrollPane.getVerticalScrollBar().addAdjustmentListener(e -> {
            if (!e.getValueIsAdjusting() && e.getValue() <= 35 && hasMoreOlder && !loadingOlder && !cards.isEmpty()) {
                Long oldestId = findOldestPersistedId();
                if (oldestId != null && loadOlderCallback != null) {
                    loadingOlder = true;
                    topStatusLabel.setText("Cargando mensajes anteriores...");
                    loadOlderCallback.accept(oldestId);
                }
            }
        });

        add(scrollPane, BorderLayout.CENTER);
    }

    public void configure(
            String currentUsername,
            long viewerChannelPerms,
            Map<String, UserProfile> profileCache,
            MessageCardPanel.CardActions cardActions,
            LongConsumer loadOlderCallback
    ) {
        this.currentUsername = currentUsername;
        this.viewerChannelPerms = viewerChannelPerms;
        this.profileCache = profileCache;
        this.cardActions = cardActions;
        this.loadOlderCallback = loadOlderCallback;
    }

    public void updatePermissions(long viewerChannelPerms) {
        this.viewerChannelPerms = viewerChannelPerms;
    }

    public void clearFeed() {
        cards.clear();
        byMessageId.clear();
        byTempId.clear();
        listContainer.removeAll();
        hasMoreOlder = true;
        loadingOlder = false;
        topStatusLabel.setText("Cargando canal...");
        listContainer.add(topStatusLabel);
        listContainer.revalidate();
        listContainer.repaint();
    }

    public void setInitialHistory(List<Message> history) {
        clearFeed();
        hasMoreOlder = history != null && history.size() >= 30;
        topStatusLabel.setText(hasMoreOlder ? "Desliza hacia arriba para cargar mensajes anteriores..." : "Inicio de la conversación");
        if (history != null) {
            for (Message m : history) {
                appendMessage(m, MessageCardPanel.DeliveryState.SENT, false);
            }
        }
        scrollToBottom();
    }

    /**
     * Inserta página histórica al principio manteniendo la posición visual del scroll intacta.
     */
    public void prependOlderHistory(List<Message> olderMessages, boolean hasMore) {
        loadingOlder = false;
        this.hasMoreOlder = hasMore;
        topStatusLabel.setText(hasMore ? "Desliza hacia arriba para cargar mensajes anteriores..." : "Inicio del historial del canal");

        if (olderMessages == null || olderMessages.isEmpty()) {
            return;
        }

        JScrollBar bar = scrollPane.getVerticalScrollBar();
        int oldMax = bar.getMaximum();
        int oldVal = bar.getValue();

        int insertIndex = 1; // Justo después de topStatusLabel
        for (Message m : olderMessages) {
            if (m.getId() != null && byMessageId.containsKey(m.getId())) {
                continue;
            }
            MessageCardPanel card = buildCard(m, MessageCardPanel.DeliveryState.SENT);
            cards.add(insertIndex - 1, card);
            if (m.getId() != null) byMessageId.put(m.getId(), card);
            listContainer.add(card, insertIndex++);
        }

        listContainer.revalidate();
        listContainer.repaint();

        SwingUtilities.invokeLater(() -> {
            int newMax = bar.getMaximum();
            int delta = Math.max(0, newMax - oldMax);
            bar.setValue(oldVal + delta);
        });
    }

    public void appendMessage(Message msg, MessageCardPanel.DeliveryState state, boolean autoScroll) {
        if (msg == null) return;

        // Si es respuesta a un optimista propio ya mostrado por tempId, actualizar en el sitio
        if (msg.getTempId() != null && byTempId.containsKey(msg.getTempId())) {
            MessageCardPanel existing = byTempId.remove(msg.getTempId());
            if (msg.getId() != null) {
                byMessageId.put(msg.getId(), existing);
            }
            existing.markSent(msg.getId(), msg.getTimestamp());
            if (msg.getEmbed() != null) existing.updateEmbed(msg.getEmbed());
            if (msg.getPoll() != null) existing.updatePoll(msg.getPoll());
            return;
        }

        // Deduplicación por id real
        if (msg.getId() != null && byMessageId.containsKey(msg.getId())) {
            return;
        }

        boolean wasNearBottom = isNearBottom();

        MessageCardPanel card = buildCard(msg, state);
        cards.add(card);
        if (msg.getId() != null) byMessageId.put(msg.getId(), card);
        if (msg.getTempId() != null) byTempId.put(msg.getTempId(), card);

        listContainer.add(card);
        enforceVirtualWindow();
        listContainer.revalidate();
        listContainer.repaint();

        if (autoScroll || wasNearBottom) {
            scrollToBottom();
        }
    }

    private void enforceVirtualWindow() {
        while (cards.size() > MAX_RENDERED_CARDS) {
            MessageCardPanel oldest = cards.remove(0);
            if (oldest.getMessage().getId() != null) {
                byMessageId.remove(oldest.getMessage().getId());
            }
            if (oldest.getMessage().getTempId() != null) {
                byTempId.remove(oldest.getMessage().getTempId());
            }
            listContainer.remove(oldest);
            hasMoreOlder = true;
            topStatusLabel.setText("Desliza hacia arriba para recargar mensajes anteriores...");
        }
    }

    private MessageCardPanel buildCard(Message msg, MessageCardPanel.DeliveryState state) {
        UserProfile prof = msg.getSender() != null ? profileCache.get(msg.getSender().toLowerCase()) : null;
        return new MessageCardPanel(msg, prof, currentUsername, viewerChannelPerms, state, cardActions);
    }

    public void handleAck(String tempId, Long realId, long timestamp) {
        if (tempId == null) return;
        MessageCardPanel card = byTempId.remove(tempId);
        if (card != null) {
            if (realId != null) byMessageId.put(realId, card);
            card.markSent(realId, timestamp);
        }
    }

    public void handleSendError(String tempId, String errorMsg) {
        if (tempId == null) return;
        MessageCardPanel card = byTempId.get(tempId);
        if (card != null) {
            card.markError(errorMsg);
        }
    }

    public void removeCard(Long messageId, String tempId) {
        MessageCardPanel target = messageId != null ? byMessageId.remove(messageId) : null;
        if (target == null && tempId != null) {
            target = byTempId.remove(tempId);
        }
        if (target != null) {
            if (target.getMessage().getTempId() != null) byTempId.remove(target.getMessage().getTempId());
            if (target.getMessage().getId() != null) byMessageId.remove(target.getMessage().getId());
            cards.remove(target);
            listContainer.remove(target);
            listContainer.revalidate();
            listContainer.repaint();
        }
    }

    public void updateReactions(Long messageId, Map<String, List<String>> reactions) {
        MessageCardPanel card = byMessageId.get(messageId);
        if (card != null) {
            card.updateReactions(reactions);
        }
    }

    public void updateEmbed(Long messageId, EmbedData embed) {
        MessageCardPanel card = byMessageId.get(messageId);
        if (card != null) {
            card.updateEmbed(embed);
        }
    }

    public void updatePoll(Long messageId, PollData poll) {
        MessageCardPanel card = byMessageId.get(messageId);
        if (card != null) {
            card.updatePoll(poll);
        }
    }

    public void incrementThreadCount(Long rootMessageId) {
        MessageCardPanel card = byMessageId.get(rootMessageId);
        if (card != null) {
            Integer cur = card.getMessage().getThreadReplyCount();
            card.updateThreadReplyCount((cur != null ? cur : 0) + 1);
        }
    }

    public void refreshAvatarsForUser(UserProfile profile) {
        if (profile == null || profile.getUsername() == null) return;
        for (MessageCardPanel card : cards) {
            if (profile.getUsername().equalsIgnoreCase(card.getMessage().getSender())) {
                card.refreshAvatar(profile);
            }
        }
        repaint();
    }

    public boolean scrollToMessage(Long messageId) {
        if (messageId == null) return false;
        MessageCardPanel card = byMessageId.get(messageId);
        if (card == null) return false;
        SwingUtilities.invokeLater(() -> {
            listContainer.scrollRectToVisible(card.getBounds());
            card.flashHighlight();
        });
        return true;
    }

    public Long findOldestPersistedId() {
        for (MessageCardPanel card : cards) {
            if (card.getMessage().getId() != null) {
                return card.getMessage().getId();
            }
        }
        return null;
    }

    public void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar bar = scrollPane.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        });
    }

    private boolean isNearBottom() {
        JScrollBar bar = scrollPane.getVerticalScrollBar();
        return bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - 90;
    }
}
