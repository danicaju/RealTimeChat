package com.danicaju.chat.client.ui;

import com.danicaju.chat.client.ChatClientConnection;
import com.danicaju.chat.model.PresenceStatus;
import com.danicaju.chat.model.UserProfile;
import com.danicaju.chat.security.E2EECrypto;
import com.danicaju.chat.security.Permission;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Diálogos modales estilo Discord para editar el perfil propio (con avatar propio redimensionable,
 * biografía amplia con límite estricto y contador en vivo, presencia y estado custom) y visualizar perfiles.
 */
public final class ProfileDialogs {

    private static final String[] AVATAR_PRESETS = {
            "cyber-neon", "synthwave", "cosmic", "emerald", "sunset", "bot-matrix", "golden", "retro-pixel"
    };
    private static final String[] BANNER_PRESETS = {
            "#5865F2", "#EB459E", "#23A55A", "#F0B232", "#7C3AED", "gradient-cyber", "gradient-sunset", "gradient-cosmic"
    };
    private static final String[] STATUS_TAGS = {
            "", "[CODE]", "[GAME]", "[MUSIC]", "[CAFE]", "[WORK]", "[FOCUS]", "[AFK]"
    };

    private ProfileDialogs() {
    }

    public static void showEditProfileDialog(
            Window owner,
            UserProfile currentProfile,
            Consumer<UserProfile> onSave
    ) {
        JDialog dialog = new JDialog(owner, "Personalizar Perfil - Discord Style", JDialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout(12, 12));
        dialog.getContentPane().setBackground(new Color(0x2B2D31));

        final AtomicReference<String> customAvatarBase64 = new AtomicReference<>(currentProfile.getAvatarImageBase64());

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(new Color(0x2B2D31));
        form.setBorder(BorderFactory.createEmptyBorder(16, 18, 12, 18));

        // Vista previa de Avatar + Botones subir/quitar
        JPanel avatarRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        avatarRow.setBackground(new Color(0x2B2D31));
        JComboBox<String> avatarCombo = new JComboBox<>(AVATAR_PRESETS);
        avatarCombo.setSelectedItem(currentProfile.getAvatar());

        JLabel avatarPreview = new JLabel(ImageUtils.buildAvatarIcon(
                currentProfile.getUsername(),
                currentProfile.getAvatar(),
                customAvatarBase64.get(),
                76,
                ImageUtils.parseColor(currentProfile.getPresence().getColorHex(), new Color(0x23A55A))
        ));

        JButton uploadAvatarBtn = new JButton("Subir y Redimensionar Foto...");
        uploadAvatarBtn.setBackground(new Color(0x5865F2));
        uploadAvatarBtn.setForeground(Color.WHITE);
        JButton clearAvatarBtn = new JButton("Usar Preset");

        Runnable refreshAvatarPreview = () -> avatarPreview.setIcon(ImageUtils.buildAvatarIcon(
                currentProfile.getUsername(),
                (String) avatarCombo.getSelectedItem(),
                customAvatarBase64.get(),
                76,
                ImageUtils.parseColor(currentProfile.getPresence().getColorHex(), new Color(0x23A55A))
        ));

        uploadAvatarBtn.addActionListener(e -> {
            String cropped = AvatarCropDialog.chooseAndCropAvatar(dialog);
            if (cropped != null) {
                customAvatarBase64.set(cropped);
                refreshAvatarPreview.run();
            }
        });
        clearAvatarBtn.addActionListener(e -> {
            customAvatarBase64.set(null);
            refreshAvatarPreview.run();
        });
        avatarCombo.addActionListener(e -> refreshAvatarPreview.run());

        JPanel avatarButtons = new JPanel();
        avatarButtons.setLayout(new BoxLayout(avatarButtons, BoxLayout.Y_AXIS));
        avatarButtons.setBackground(new Color(0x2B2D31));
        JPanel btnLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        btnLine.setBackground(new Color(0x2B2D31));
        btnLine.add(uploadAvatarBtn);
        btnLine.add(clearAvatarBtn);
        avatarButtons.add(btnLine);
        avatarButtons.add(Box.createVerticalStrut(6));
        avatarButtons.add(avatarCombo);

        avatarRow.add(avatarPreview);
        avatarRow.add(avatarButtons);
        form.add(avatarRow);
        form.add(Box.createVerticalStrut(10));

        // Selector de Banner
        JLabel bannerLbl = makeLabel("Color o degradado de Banner:");
        JComboBox<String> bannerCombo = new JComboBox<>(BANNER_PRESETS);
        bannerCombo.setEditable(true);
        bannerCombo.setSelectedItem(currentProfile.getBanner());
        bannerCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        form.add(bannerLbl);
        form.add(Box.createVerticalStrut(4));
        form.add(bannerCombo);
        form.add(Box.createVerticalStrut(12));

        // Slot amplio de Biografía con máximo estricto de caracteres
        JPanel bioHeader = new JPanel(new BorderLayout());
        bioHeader.setBackground(new Color(0x2B2D31));
        bioHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        JLabel bioLbl = makeLabel("Biografía (Sobre mí):");
        JLabel bioCounter = new JLabel("0 / " + UserProfile.MAX_BIO_LENGTH);
        bioCounter.setFont(new Font("Segoe UI", Font.BOLD, 12));
        bioHeader.add(bioLbl, BorderLayout.WEST);
        bioHeader.add(bioCounter, BorderLayout.EAST);

        JTextArea bioArea = new JTextArea(6, 36);
        bioArea.setLineWrap(true);
        bioArea.setWrapStyleWord(true);
        bioArea.setBackground(new Color(0x1E1F22));
        bioArea.setForeground(new Color(0xDBDEE1));
        bioArea.setCaretColor(Color.WHITE);
        bioArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        bioArea.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        ((AbstractDocument) bioArea.getDocument()).setDocumentFilter(new LengthLimitFilter(UserProfile.MAX_BIO_LENGTH));
        bioArea.setText(currentProfile.getBio());

        Runnable updateCounter = () -> {
            int len = bioArea.getText().length();
            bioCounter.setText(len + " / " + UserProfile.MAX_BIO_LENGTH);
            if (len >= UserProfile.MAX_BIO_LENGTH) {
                bioCounter.setForeground(new Color(0xF23F43));
            } else if (len >= UserProfile.MAX_BIO_LENGTH - 30) {
                bioCounter.setForeground(new Color(0xF0B232));
            } else {
                bioCounter.setForeground(new Color(0x949BA4));
            }
        };
        bioArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { updateCounter.run(); }
            @Override public void removeUpdate(DocumentEvent e) { updateCounter.run(); }
            @Override public void changedUpdate(DocumentEvent e) { updateCounter.run(); }
        });
        updateCounter.run();

        JScrollPane bioScroll = new JScrollPane(bioArea);
        bioScroll.setPreferredSize(new Dimension(440, 140));
        bioScroll.setBorder(BorderFactory.createLineBorder(new Color(0x3F4147)));

        form.add(bioHeader);
        form.add(Box.createVerticalStrut(4));
        form.add(bioScroll);
        form.add(Box.createVerticalStrut(12));

        // Presencia + Estado personalizado + Tag
        JLabel statusLbl = makeLabel("Estado de Presencia y Estado Personalizado:");
        JPanel presenceRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        presenceRow.setBackground(new Color(0x2B2D31));

        JComboBox<PresenceStatus> presenceBox = new JComboBox<>(new PresenceStatus[]{
                PresenceStatus.ONLINE, PresenceStatus.IDLE, PresenceStatus.DND
        });
        presenceBox.setSelectedItem(currentProfile.getPresence() == PresenceStatus.OFFLINE
                ? PresenceStatus.ONLINE : currentProfile.getPresence());

        JComboBox<String> symbolBox = new JComboBox<>(STATUS_TAGS);
        symbolBox.setEditable(true);
        symbolBox.setSelectedItem(currentProfile.getStatusSymbol() != null ? currentProfile.getStatusSymbol() : "");
        symbolBox.setPreferredSize(new Dimension(95, 28));

        JTextField customStatusField = new JTextField(18);
        customStatusField.setBackground(new Color(0x1E1F22));
        customStatusField.setForeground(new Color(0xDBDEE1));
        ((AbstractDocument) customStatusField.getDocument()).setDocumentFilter(new LengthLimitFilter(UserProfile.MAX_CUSTOM_STATUS_LENGTH));
        customStatusField.setText(currentProfile.getCustomStatus() != null ? currentProfile.getCustomStatus() : "");
        customStatusField.setToolTipText("Texto de estado personalizado (máx. " + UserProfile.MAX_CUSTOM_STATUS_LENGTH + " caracteres)");

        presenceRow.add(presenceBox);
        presenceRow.add(symbolBox);
        presenceRow.add(customStatusField);

        form.add(statusLbl);
        form.add(Box.createVerticalStrut(4));
        form.add(presenceRow);

        dialog.add(form, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        footer.setBackground(new Color(0x1E1F22));
        JButton cancelBtn = new JButton("Cancelar");
        JButton saveBtn = new JButton("Guardar Cambios");
        saveBtn.setBackground(new Color(0x23A55A));
        saveBtn.setForeground(Color.WHITE);

        cancelBtn.addActionListener(e -> dialog.dispose());
        saveBtn.addActionListener(e -> {
            UserProfile updated = new UserProfile(
                    currentProfile.getUsername(),
                    bioArea.getText(),
                    (String) avatarCombo.getSelectedItem(),
                    bannerCombo.getSelectedItem() != null ? bannerCombo.getSelectedItem().toString() : UserProfile.DEFAULT_BANNER
            );
            updated.setAvatarImageBase64(customAvatarBase64.get());
            updated.setPresence((PresenceStatus) presenceBox.getSelectedItem());
            updated.setStatusSymbol(symbolBox.getSelectedItem() != null ? symbolBox.getSelectedItem().toString() : null);
            updated.setCustomStatus(customStatusField.getText());
            updated.setRole(currentProfile.getRole());
            updated.setPermissions(currentProfile.getPermissions());
            updated.setPublicKeyBase64(currentProfile.getPublicKeyBase64());
            onSave.accept(updated);
            dialog.dispose();
        });

        footer.add(cancelBtn);
        footer.add(saveBtn);
        dialog.add(footer, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    public static void showUserProfileCard(
            Window owner,
            UserProfile profile,
            boolean isSelf,
            boolean viewerCanManageRoles,
            ChatClientConnection connection,
            Runnable onEditSelf,
            Runnable onSendEncryptedDm,
            Runnable onManageRoles
    ) {
        JDialog dialog = new JDialog(owner, "Perfil de @" + profile.getUsername(), JDialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout());
        dialog.getContentPane().setBackground(new Color(0x1E1F22));

        // Banner superior
        JPanel banner = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 14));
        banner.setPreferredSize(new Dimension(420, 95));
        banner.setBackground(ImageUtils.parseColor(profile.getBanner(), new Color(0x5865F2)));

        Color presenceColor = ImageUtils.parseColor(profile.getPresence().getColorHex(), new Color(0x23A55A));
        JLabel avatarLbl = new JLabel(ImageUtils.buildAvatarIcon(
                profile.getUsername(),
                profile.getAvatar(),
                profile.getAvatarImageBase64(),
                68,
                presenceColor
        ));
        banner.add(avatarLbl);

        JPanel nameBox = new JPanel();
        nameBox.setOpaque(false);
        nameBox.setLayout(new BoxLayout(nameBox, BoxLayout.Y_AXIS));
        JLabel nameLbl = new JLabel("@" + profile.getUsername());
        nameLbl.setFont(new Font("Segoe UI", Font.BOLD, 18));
        nameLbl.setForeground(Color.WHITE);
        String tag = profile.getStatusSymbol() != null ? profile.getStatusSymbol() + " " : "";
        String custom = profile.getCustomStatus() != null ? " - " + profile.getCustomStatus() : "";
        JLabel statusLbl = new JLabel(profile.getPresence().getLabel() + " " + tag + custom);
        statusLbl.setForeground(new Color(0xF2F3F5));
        statusLbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        nameBox.add(nameLbl);
        nameBox.add(statusLbl);
        banner.add(nameBox);

        dialog.add(banner, BorderLayout.NORTH);

        // Contenido Bio + Rol + Permisos + E2EE Fingerprint
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(new Color(0x2B2D31));
        body.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

        JLabel roleLbl = new JLabel("Rol: " + profile.getRole() + "  |  " + Permission.describePermissions(profile.getPermissions()));
        roleLbl.setForeground(new Color(0x949BA4));
        roleLbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        body.add(roleLbl);
        body.add(Box.createVerticalStrut(10));

        JLabel bioTitle = makeLabel("BIOGRAFÍA");
        bioTitle.setFont(new Font("Segoe UI", Font.BOLD, 11));
        body.add(bioTitle);
        body.add(Box.createVerticalStrut(4));

        JTextArea bioView = new JTextArea(profile.getBio());
        bioView.setEditable(false);
        bioView.setLineWrap(true);
        bioView.setWrapStyleWord(true);
        bioView.setBackground(new Color(0x1E1F22));
        bioView.setForeground(new Color(0xDBDEE1));
        bioView.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        JScrollPane bioPane = new JScrollPane(bioView);
        bioPane.setPreferredSize(new Dimension(380, 100));
        bioPane.setBorder(BorderFactory.createLineBorder(new Color(0x3F4147)));
        body.add(bioPane);

        String fp = E2EECrypto.fingerprint(profile.getPublicKeyBase64());
        if (!"N/A".equals(fp)) {
            body.add(Box.createVerticalStrut(8));
            JLabel e2eeLbl = new JLabel("[E2EE] Clave pública ECDH: " + fp);
            e2eeLbl.setForeground(new Color(0x23A55A));
            e2eeLbl.setFont(new Font("Consolas", Font.PLAIN, 11));
            body.add(e2eeLbl);
        }

        dialog.add(body, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        footer.setBackground(new Color(0x1E1F22));
        if (isSelf && onEditSelf != null) {
            JButton editBtn = new JButton("Editar Mi Perfil");
            editBtn.setBackground(new Color(0x5865F2));
            editBtn.setForeground(Color.WHITE);
            editBtn.addActionListener(e -> {
                dialog.dispose();
                onEditSelf.run();
            });
            footer.add(editBtn);
        } else if (!isSelf && onSendEncryptedDm != null) {
            JButton dmBtn = new JButton("Mensaje Directo (E2EE)");
            dmBtn.setBackground(new Color(0x23A55A));
            dmBtn.setForeground(Color.WHITE);
            dmBtn.addActionListener(e -> {
                dialog.dispose();
                onSendEncryptedDm.run();
            });
            footer.add(dmBtn);
        }
        if (viewerCanManageRoles && onManageRoles != null) {
            JButton roleBtn = new JButton("Permisos / Rol");
            roleBtn.addActionListener(e -> {
                dialog.dispose();
                onManageRoles.run();
            });
            footer.add(roleBtn);
        }
        JButton closeBtn = new JButton("Cerrar");
        closeBtn.addActionListener(e -> dialog.dispose());
        footer.add(closeBtn);

        dialog.add(footer, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    private static JLabel makeLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(new Color(0xB5BAC1));
        l.setFont(new Font("Segoe UI", Font.BOLD, 12));
        return l;
    }

    private static class LengthLimitFilter extends DocumentFilter {
        private final int maxChars;

        LengthLimitFilter(int maxChars) {
            this.maxChars = maxChars;
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
            if (string == null) return;
            int available = maxChars - fb.getDocument().getLength();
            if (available > 0) {
                super.insertString(fb, offset, string.length() > available ? string.substring(0, available) : string, attr);
            }
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
            if (text == null) {
                super.replace(fb, offset, length, null, attrs);
                return;
            }
            int currentLen = fb.getDocument().getLength();
            int available = maxChars - (currentLen - length);
            if (available > 0) {
                super.replace(fb, offset, length, text.length() > available ? text.substring(0, available) : text, attrs);
            }
        }
    }
}
