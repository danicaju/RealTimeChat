package com.danicaju.chat.client.ui;

import com.danicaju.chat.service.SlashCommandHelper;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextArea;
import java.awt.Color;
import java.awt.Font;
import java.util.List;

/**
 * Menú desplegable de autocompletado en tiempo real cuando el usuario escribe "/" en el input.
 */
public class SlashCommandPopup extends JPopupMenu {

    public SlashCommandPopup() {
        setFocusable(false);
        setBackground(new Color(0x1E1F22));
    }

    public void updateForInput(JTextArea inputField, String currentText) {
        removeAll();
        if (currentText == null || !currentText.startsWith("/") || currentText.contains("\n")) {
            setVisible(false);
            return;
        }
        // Si ya escribió espacio completo y argumentos, ocultar menú
        if (currentText.contains(" ")) {
            setVisible(false);
            return;
        }
        List<SlashCommandHelper.CommandSpec> matches = SlashCommandHelper.autocomplete(currentText);
        if (matches.isEmpty()) {
            setVisible(false);
            return;
        }
        for (SlashCommandHelper.CommandSpec spec : matches) {
            JMenuItem item = new JMenuItem("<html><b>" + spec.usage().replace("<", "&lt;").replace(">", "&gt;")
                    + "</b><br/><span style='color:#949BA4;font-size:10px;'>" + spec.description() + "</span></html>");
            item.setBackground(new Color(0x1E1F22));
            item.setForeground(new Color(0xF2F3F5));
            item.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            item.addActionListener(e -> {
                inputField.setText(spec.name() + " ");
                inputField.requestFocusInWindow();
                setVisible(false);
            });
            add(item);
        }
        pack();
        int popupHeight = getPreferredSize().height;
        show(inputField, 0, -popupHeight - 4);
    }
}
