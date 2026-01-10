package com.raks.filesync.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Styled button with ApiGuard purple theme
 */
public class StyledButton extends JButton {
    
    public StyledButton(String text) {
        super(text);
        applyStyle();
    }
    
    private void applyStyle() {
        setBackground(ThemeConfig.BUTTON_BACKGROUND);
        setForeground(ThemeConfig.BUTTON_TEXT);
        setFont(new Font("Arial", Font.BOLD, 12));
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ThemeConfig.BUTTON_BORDER, 1, true),
            new EmptyBorder(8, 15, 8, 15)
        ));
        setFocusPainted(false);
        setContentAreaFilled(true);
        setOpaque(true);
        
        // Ensure colors are visible
        setForeground(Color.WHITE);
        
        // Add hover effect
        addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                setBackground(ThemeConfig.PRIMARY_DARK);
            }
            
            public void mouseExited(java.awt.event.MouseEvent evt) {
                setBackground(ThemeConfig.BUTTON_BACKGROUND);
            }
        });
    }
}
