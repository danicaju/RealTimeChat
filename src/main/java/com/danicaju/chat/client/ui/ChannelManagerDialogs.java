package com.danicaju.chat.client.ui;

import com.danicaju.chat.client.ChatClientConnection;
import com.danicaju.chat.model.ChannelInfo;
import com.danicaju.chat.model.UserProfile;
import com.danicaju.chat.model.WebhookInfo;
import com.danicaju.chat.security.Permission;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Diálogos modales para gestión de canales (crear/eliminar), matriz de permisos Bitfield
 * (roles globales y overrides por canal) y Webhooks HTTP por canal.
 */
public final class ChannelManagerDialogs {

    private ChannelManagerDialogs() {
    }

    public static void showCreateChannelDialog(Window owner, ChatClientConnection connection) {
        JDialog d = new JDialog(owner, "Crear Canal", JDialog.ModalityType.APPLICATION_MODAL);
        d.setLayout(new BorderLayout(10, 10));
        d.getContentPane().setBackground(new Color(0x2B2D31));

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(new Color(0x2B2D31));
        form.setBorder(BorderFactory.createEmptyBorder(14, 16, 10, 16));

        JTextField nameField = new JTextField(20);
        nameField.setBackground(new Color(0x1E1F22));
        nameField.setForeground(Color.WHITE);

        JComboBox<String> typeBox = new JComboBox<>(new String[]{ChannelInfo.TYPE_TEXT, ChannelInfo.TYPE_VOICE});
        JTextField topicField = new JTextField(24);
        topicField.setBackground(new Color(0x1E1F22));
        topicField.setForeground(Color.WHITE);

        JCheckBox readOnlyCheck = new JCheckBox("Canal de solo lectura por defecto (denegar SEND_MESSAGES)");
        readOnlyCheck.setBackground(new Color(0x2B2D31));
        readOnlyCheck.setForeground(new Color(0xDBDEE1));

        form.add(lbl("Nombre del canal (sin espacios, ej. proyectos):"));
        form.add(Box.createVerticalStrut(4));
        form.add(nameField);
        form.add(Box.createVerticalStrut(10));
        form.add(lbl("Tipo de canal:"));
        form.add(Box.createVerticalStrut(4));
        form.add(typeBox);
        form.add(Box.createVerticalStrut(10));
        form.add(lbl("Descripción / Tema:"));
        form.add(Box.createVerticalStrut(4));
        form.add(topicField);
        form.add(Box.createVerticalStrut(10));
        form.add(readOnlyCheck);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.setBackground(new Color(0x1E1F22));
        JButton cancel = new JButton("Cancelar");
        JButton create = new JButton("Crear Canal");
        create.setBackground(new Color(0x23A55A));
        create.setForeground(Color.WHITE);

        cancel.addActionListener(e -> d.dispose());
        create.addActionListener(e -> {
            String rawName = nameField.getText().trim().toLowerCase();
            if (rawName.isEmpty()) {
                JOptionPane.showMessageDialog(d, "Introduce un nombre de canal válido.");
                return;
            }
            if (!rawName.startsWith("#")) rawName = "#" + rawName;
            ChannelInfo info = new ChannelInfo(rawName, (String) typeBox.getSelectedItem(),
                    topicField.getText().trim(), connection.getUsername());
            if (readOnlyCheck.isSelected()) {
                info.setDefaultDenyMask(Permission.SEND_MESSAGES);
            }
            connection.createChannel(info);
            d.dispose();
        });

        actions.add(cancel);
        actions.add(create);
        d.add(form, BorderLayout.CENTER);
        d.add(actions, BorderLayout.SOUTH);
        d.pack();
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
    }

    public static void showDeleteChannelDialog(Window owner, ChatClientConnection connection, List<String> channels) {
        List<String> deletable = channels.stream().filter(c -> !"#general".equalsIgnoreCase(c)).toList();
        if (deletable.isEmpty()) {
            JOptionPane.showMessageDialog(owner, "No hay canales eliminables adicionales a #general.", "Canales", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String chosen = (String) JOptionPane.showInputDialog(
                owner,
                "Selecciona el canal a eliminar permanentemente:",
                "Eliminar Canal",
                JOptionPane.WARNING_MESSAGE,
                null,
                deletable.toArray(),
                deletable.get(0)
        );
        if (chosen != null) {
            int confirm = JOptionPane.showConfirmDialog(owner,
                    "¿Eliminar el canal " + chosen + " y todos sus mensajes?",
                    "Confirmar eliminación",
                    JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                connection.deleteChannel(chosen);
            }
        }
    }

    public static void showPermissionsDialog(
            Window owner,
            ChatClientConnection connection,
            Map<String, UserProfile> knownProfiles,
            List<ChannelInfo> channels,
            String preselectedUser
    ) {
        JDialog d = new JDialog(owner, "Motor de Permisos Bitfield y Roles", JDialog.ModalityType.APPLICATION_MODAL);
        d.setLayout(new BorderLayout());
        d.getContentPane().setBackground(new Color(0x2B2D31));

        JTabbedPane tabs = new JTabbedPane();

        // --- TAB 1: Roles Globales y Bitfield por Usuario ---
        JPanel userTab = new JPanel();
        userTab.setLayout(new BoxLayout(userTab, BoxLayout.Y_AXIS));
        userTab.setBackground(new Color(0x2B2D31));
        userTab.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        JComboBox<String> userBox = new JComboBox<>(knownProfiles.values().stream()
                .map(UserProfile::getUsername).sorted().toArray(String[]::new));
        if (preselectedUser != null) userBox.setSelectedItem(preselectedUser);

        JComboBox<String> roleBox = new JComboBox<>(new String[]{
                Permission.ROLE_ADMIN, Permission.ROLE_MODERATOR, Permission.ROLE_MEMBER, Permission.ROLE_READ_ONLY
        });

        Map<Long, JCheckBox> bitChecks = new LinkedHashMap<>();
        JPanel bitsGrid = new JPanel(new GridLayout(0, 2, 8, 6));
        bitsGrid.setBackground(new Color(0x1E1F22));
        bitsGrid.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        for (Map.Entry<Long, String> entry : Permission.getAllPermissionLabels().entrySet()) {
            JCheckBox cb = new JCheckBox(entry.getValue() + " (1<<" + Long.numberOfTrailingZeros(entry.getKey()) + ")");
            cb.setBackground(new Color(0x1E1F22));
            cb.setForeground(new Color(0xDBDEE1));
            bitChecks.put(entry.getKey(), cb);
            bitsGrid.add(cb);
        }

        Runnable loadSelectedUser = () -> {
            String sel = (String) userBox.getSelectedItem();
            if (sel == null) return;
            UserProfile p = knownProfiles.get(sel.toLowerCase());
            if (p != null) {
                roleBox.setSelectedItem(p.getRole());
                long mask = p.getPermissions();
                for (Map.Entry<Long, JCheckBox> e : bitChecks.entrySet()) {
                    e.getValue().setSelected((mask & e.getKey()) == e.getKey());
                }
            }
        };
        userBox.addActionListener(e -> loadSelectedUser.run());
        roleBox.addActionListener(e -> {
            String role = (String) roleBox.getSelectedItem();
            long preset = Permission.getDefaultPermissionsForRole(role);
            for (Map.Entry<Long, JCheckBox> en : bitChecks.entrySet()) {
                en.getValue().setSelected((preset & en.getKey()) == en.getKey());
            }
        });
        loadSelectedUser.run();

        JButton applyUserBtn = new JButton("Aplicar Rol y Máscara de Bits");
        applyUserBtn.setBackground(new Color(0x5865F2));
        applyUserBtn.setForeground(Color.WHITE);
        applyUserBtn.addActionListener(e -> {
            String target = (String) userBox.getSelectedItem();
            if (target == null) return;
            long mask = 0L;
            for (Map.Entry<Long, JCheckBox> en : bitChecks.entrySet()) {
                if (en.getValue().isSelected()) mask |= en.getKey();
            }
            connection.updateRoleOrOverride(target, (String) roleBox.getSelectedItem(), mask, null);
            JOptionPane.showMessageDialog(d, "Permisos enviados al servidor para @" + target + ".");
        });

        userTab.add(lbl("Usuario:"));
        userTab.add(userBox);
        userTab.add(Box.createVerticalStrut(8));
        userTab.add(lbl("Plantilla de Rol:"));
        userTab.add(roleBox);
        userTab.add(Box.createVerticalStrut(8));
        userTab.add(lbl("Operadores Bitfield Personalizados:"));
        userTab.add(bitsGrid);
        userTab.add(Box.createVerticalStrut(10));
        userTab.add(applyUserBtn);

        // --- TAB 2: Overrides por Canal ---
        JPanel chanTab = new JPanel();
        chanTab.setLayout(new BoxLayout(chanTab, BoxLayout.Y_AXIS));
        chanTab.setBackground(new Color(0x2B2D31));
        chanTab.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        JComboBox<String> chBox = new JComboBox<>(channels.stream().map(ChannelInfo::getName).toArray(String[]::new));
        JCheckBox denySend = new JCheckBox("Denegar SEND_MESSAGES por defecto (Canal Solo Lectura)");
        JCheckBox denyView = new JCheckBox("Denegar VIEW_CHANNEL por defecto (Canal Privado)");
        JCheckBox denyThread = new JCheckBox("Denegar CREATE_THREADS por defecto");
        for (JCheckBox cb : List.of(denySend, denyView, denyThread)) {
            cb.setBackground(new Color(0x2B2D31));
            cb.setForeground(new Color(0xDBDEE1));
        }

        Runnable loadChan = () -> {
            String name = (String) chBox.getSelectedItem();
            if (name == null) return;
            ChannelInfo info = channels.stream().filter(c -> c.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
            if (info != null) {
                denySend.setSelected((info.getDefaultDenyMask() & Permission.SEND_MESSAGES) != 0);
                denyView.setSelected((info.getDefaultDenyMask() & Permission.VIEW_CHANNEL) != 0);
                denyThread.setSelected((info.getDefaultDenyMask() & Permission.CREATE_THREADS) != 0);
            }
        };
        chBox.addActionListener(e -> loadChan.run());
        loadChan.run();

        JButton applyChanBtn = new JButton("Guardar Overrides del Canal");
        applyChanBtn.setBackground(new Color(0x23A55A));
        applyChanBtn.setForeground(Color.WHITE);
        applyChanBtn.addActionListener(e -> {
            String name = (String) chBox.getSelectedItem();
            if (name == null) return;
            long deny = 0L;
            if (denySend.isSelected()) deny |= Permission.SEND_MESSAGES;
            if (denyView.isSelected()) deny |= Permission.VIEW_CHANNEL;
            if (denyThread.isSelected()) deny |= Permission.CREATE_THREADS;

            ChannelInfo upd = new ChannelInfo();
            upd.setName(name);
            upd.setDefaultDenyMask(deny);
            connection.updateRoleOrOverride(null, null, null, upd);
            JOptionPane.showMessageDialog(d, "Overrides de " + name + " actualizados.");
        });

        chanTab.add(lbl("Canal:"));
        chanTab.add(chBox);
        chanTab.add(Box.createVerticalStrut(10));
        chanTab.add(denySend);
        chanTab.add(denyView);
        chanTab.add(denyThread);
        chanTab.add(Box.createVerticalStrut(12));
        chanTab.add(applyChanBtn);

        tabs.addTab("Roles y Bitfields de Usuario", userTab);
        tabs.addTab("Overrides de Canal", chanTab);
        d.add(tabs, BorderLayout.CENTER);
        d.pack();
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
    }

    public static void showWebhooksDialog(
            Window owner,
            ChatClientConnection connection,
            String channel,
            DefaultListModel<String> liveModel,
            List<WebhookInfo> liveBackingList
    ) {
        connection.listWebhooks(channel);
        JDialog d = new JDialog(owner, "Webhooks REST del canal " + channel, JDialog.ModalityType.MODELESS);
        d.setLayout(new BorderLayout(8, 8));
        d.getContentPane().setBackground(new Color(0x2B2D31));

        JList<String> list = new JList<>(liveModel);
        list.setBackground(new Color(0x1E1F22));
        list.setForeground(new Color(0xDBDEE1));
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(540, 180));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        top.setBackground(new Color(0x2B2D31));
        JTextField hookNameField = new JTextField("GitHub-CI-Bot", 18);
        JButton createBtn = new JButton("Crear Webhook");
        createBtn.setBackground(new Color(0x5865F2));
        createBtn.setForeground(Color.WHITE);
        createBtn.addActionListener(e -> connection.createWebhook(channel, hookNameField.getText().trim()));
        top.add(lbl("Nombre:"));
        top.add(hookNameField);
        top.add(createBtn);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        bottom.setBackground(new Color(0x1E1F22));
        JButton copyBtn = new JButton("Copiar URL POST");
        copyBtn.addActionListener(e -> {
            int idx = list.getSelectedIndex();
            if (idx >= 0 && idx < liveBackingList.size()) {
                WebhookInfo w = liveBackingList.get(idx);
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(w.getUrl()), null);
                JOptionPane.showMessageDialog(d, "URL copiada:\n" + w.getUrl()
                        + "\n\nEjemplo curl:\ncurl -X POST \"" + w.getUrl() + "\" -H \"Content-Type: application/json\" -d \"{\\\"content\\\":\\\"Hola desde Webhook!\\\",\\\"title\\\":\\\"Deploy OK\\\"}\"");
            }
        });
        JButton delBtn = new JButton("Eliminar");
        delBtn.setForeground(new Color(0xF87171));
        delBtn.addActionListener(e -> {
            int idx = list.getSelectedIndex();
            if (idx >= 0 && idx < liveBackingList.size()) {
                connection.deleteWebhook(channel, liveBackingList.get(idx).getId());
            }
        });
        JButton closeBtn = new JButton("Cerrar");
        closeBtn.addActionListener(e -> d.dispose());
        bottom.add(copyBtn);
        bottom.add(delBtn);
        bottom.add(closeBtn);

        d.add(top, BorderLayout.NORTH);
        d.add(scroll, BorderLayout.CENTER);
        d.add(bottom, BorderLayout.SOUTH);
        d.pack();
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
    }

    private static JLabel lbl(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(new Color(0xB5BAC1));
        l.setFont(new Font("Segoe UI", Font.BOLD, 12));
        return l;
    }
}
