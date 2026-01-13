package com.raks.filesync.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Styled button with ApiGuard purple theme
 */
public class StyledButton extends JButton {
    
    private int arcWidth = 15;
    private int arcHeight = 15;
    
    public StyledButton(String text) {
        super(text);
        applyStyle();
    }
    
    public StyledButton(String text, boolean isHeadline) {
        super(text);
        applyStyle();
        if (isHeadline) {
            setBackground(ThemeConfig.PANEL_BACKGROUND);
            setForeground(ThemeConfig.PRIMARY_COLOR);
            // No content area filled for headline buttons by default
            setContentAreaFilled(false);
        }
    }
    
    private void applyStyle() {
        setBackground(ThemeConfig.BUTTON_BACKGROUND);
        setForeground(ThemeConfig.BUTTON_TEXT);
        setFont(ThemeConfig.getScaledFont("Arial", Font.BOLD, 12));
        setFocusPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        setBorder(new EmptyBorder(8, 20, 8, 20));
        
        // Add hover effect
        addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                repaint();
            }
            
            public void mouseExited(java.awt.event.MouseEvent evt) {
                repaint();
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        if (getModel().isPressed()) {
            g2.setColor(ThemeConfig.PRIMARY_DARK);
        } else if (getModel().isRollover()) {
            g2.setColor(ThemeConfig.PRIMARY_LIGHT);
        } else {
            g2.setColor(getBackground());
        }
        
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), arcWidth, arcHeight);
        
        // Border
        g2.setColor(ThemeConfig.BUTTON_BORDER);
        g2.setStroke(new BasicStroke(1));
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arcWidth, arcHeight);
        
        g2.dispose();
        super.paintComponent(g);
    }
}
