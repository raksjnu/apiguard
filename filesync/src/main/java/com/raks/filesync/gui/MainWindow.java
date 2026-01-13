package com.raks.filesync.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Main GUI window for FileSync Tool with ApiGuard Purple Theme
 */
public class MainWindow extends JFrame {
    private JTabbedPane tabbedPane;
    private DiscoveryPanel discoveryPanel;
    private MappingPanel mappingPanel;
    private ExecutePanel executePanel;
    
    public MainWindow() {
        initializeUI();
        applyTheme();
        setupZoomShortcuts();
    }
    
    private void initializeUI() {
        setTitle(ThemeConfig.getString("window.title"));
        setSize(
            ThemeConfig.getInt("window.width", 1200),
            ThemeConfig.getInt("window.height", 800)
        );
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        
        // Set window icon to RAKS logo
        try {
            java.net.URL logoUrl = getClass().getResource("/raksLogo.png");
            if (logoUrl != null) {
                ImageIcon icon = new ImageIcon(logoUrl);
                setIconImage(icon.getImage());
            }
        } catch (Exception e) {
            // Continue without icon if not found
        }
        
        // Main container
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(ThemeConfig.BACKGROUND_COLOR);
        
        // Header
        mainPanel.add(createHeader(), BorderLayout.NORTH);
        
        // Center - Tabbed pane
        tabbedPane = new JTabbedPane();
        tabbedPane.setBackground(ThemeConfig.BACKGROUND_COLOR);
        tabbedPane.setForeground(ThemeConfig.TEXT_PRIMARY);
        tabbedPane.setFont(new Font("Arial", Font.BOLD, 13));
        
        // Create panels
        discoveryPanel = new DiscoveryPanel();
        mappingPanel = new MappingPanel(discoveryPanel);
        executePanel = new ExecutePanel(mappingPanel);
        
        // Add tabs (Help removed - accessible via menu)
        tabbedPane.addTab(ThemeConfig.getString("tab.discovery"), discoveryPanel);
        tabbedPane.addTab(ThemeConfig.getString("tab.mapping"), mappingPanel);
        tabbedPane.addTab(ThemeConfig.getString("tab.execute"), executePanel);
        
        // Add listener to refresh panels when switching tabs
        tabbedPane.addChangeListener(e -> {
            int selectedIndex = tabbedPane.getSelectedIndex();
            if (selectedIndex == 1) { // Mapping tab
                mappingPanel.refreshMappingFiles();
            } else if (selectedIndex == 2) { // Execute tab
                executePanel.refreshFromMappingTab();
            }
        });
        
        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        
        // Footer
        mainPanel.add(createFooter(), BorderLayout.SOUTH);
        
        // Add to frame
        add(mainPanel);
        
        // Menu bar
        createMenuBar();
    }
    
    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(ThemeConfig.HEADER_BACKGROUND);
        header.setBorder(new EmptyBorder(15, 20, 15, 20));
        
        // Logo and title panel
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        titlePanel.setBackground(ThemeConfig.HEADER_BACKGROUND);
        
        // RAKS Logo wrapped in rounded white box
        JPanel logoWrapper = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
                g2.dispose();
                super.paintComponent(g); // Paint children
            }
        };
        logoWrapper.setOpaque(false);
        logoWrapper.setBorder(new EmptyBorder(5, 5, 5, 5));
        logoWrapper.setLayout(new BorderLayout());
        
        try {
            java.net.URL logoUrl = getClass().getResource("/raksLogo.png");
            if (logoUrl != null) {
                ImageIcon originalIcon = new ImageIcon(logoUrl);
                Image scaledImage = originalIcon.getImage().getScaledInstance(80, 80, Image.SCALE_SMOOTH);
                JLabel logoLabel = new JLabel(new ImageIcon(scaledImage));
                logoWrapper.add(logoLabel, BorderLayout.CENTER);
            } else {
                throw new Exception("Logo not found");
            }
        } catch (Exception e) {
            // Fallback to text if logo not found
            JLabel logoLabel = new JLabel("RAKS");
            logoLabel.setFont(ThemeConfig.getScaledFont("Arial", Font.BOLD, 20));
            logoLabel.setForeground(ThemeConfig.PRIMARY_COLOR); // Purple text on white box
            logoWrapper.add(logoLabel, BorderLayout.CENTER);
        }
        
        titlePanel.add(logoWrapper);
        
        // Title and subtitle
        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setBackground(ThemeConfig.HEADER_BACKGROUND);
        
        JLabel titleLabel = new JLabel(ThemeConfig.getString("app.name"));
        titleLabel.setFont(ThemeConfig.getScaledFont("Arial", Font.BOLD, 24));
        titleLabel.setForeground(Color.WHITE);
        
        JLabel subtitleLabel = new JLabel(ThemeConfig.getString("app.subtitle"));
        subtitleLabel.setFont(ThemeConfig.getScaledFont("Arial", Font.BOLD, 12));
        subtitleLabel.setForeground(Color.WHITE);
        
        textPanel.add(titleLabel);
        textPanel.add(Box.createVerticalStrut(3));
        textPanel.add(subtitleLabel);
        
        titlePanel.add(textPanel);
        header.add(titlePanel, BorderLayout.WEST);
        
        // Version label
        JLabel versionLabel = new JLabel("v" + ThemeConfig.getString("app.version"));
        versionLabel.setFont(ThemeConfig.getScaledFont("Arial", Font.BOLD, 14));
        versionLabel.setForeground(Color.WHITE);
        header.add(versionLabel, BorderLayout.EAST);
        
        return header;
    }

    private void setupZoomShortcuts() {
        JRootPane root = getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("control EQUALS"), "zoomIn");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("control ADD"), "zoomIn");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("control MINUS"), "zoomOut");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("control SUBTRACT"), "zoomOut");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("control 0"), "zoomReset");

        root.getActionMap().put("zoomIn", new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                ThemeConfig.setFontScale(ThemeConfig.getFontScale() + 0.1f);
                refreshUI();
            }
        });
        root.getActionMap().put("zoomOut", new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                ThemeConfig.setFontScale(ThemeConfig.getFontScale() - 0.1f);
                refreshUI();
            }
        });
        root.getActionMap().put("zoomReset", new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                ThemeConfig.setFontScale(1.0f);
                refreshUI();
            }
        });
    }

    private void refreshUI() {
        // Redraw current tab and components
        applyTheme();
        SwingUtilities.updateComponentTreeUI(this);
        // We might need to manually tell panels to update their scaled fonts if they don't use UI defaults
    }
    
    private JPanel createFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(ThemeConfig.PANEL_BACKGROUND);
        footer.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(2, 0, 0, 0, ThemeConfig.BORDER_COLOR),
            new EmptyBorder(10, 20, 10, 20)
        ));
        
        JLabel copyrightLabel = new JLabel(ThemeConfig.getString("app.copyright"));
        copyrightLabel.setFont(new Font("Arial", Font.PLAIN, 11));
        copyrightLabel.setForeground(ThemeConfig.TEXT_SECONDARY);
        footer.add(copyrightLabel, BorderLayout.CENTER);
        
        return footer;
    }
    
    private void createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.setBackground(ThemeConfig.BACKGROUND_COLOR);
        menuBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeConfig.BORDER_COLOR));
        
        // File menu
        JMenu fileMenu = new JMenu(ThemeConfig.getString("menu.file"));
        fileMenu.setForeground(ThemeConfig.TEXT_PRIMARY);
        JMenuItem exitItem = new JMenuItem(ThemeConfig.getString("menu.file.exit"));
        exitItem.addActionListener(e -> System.exit(0));
        fileMenu.add(exitItem);
        
        // Help menu
        JMenu helpMenu = new JMenu(ThemeConfig.getString("menu.help"));
        helpMenu.setForeground(ThemeConfig.TEXT_PRIMARY);
        
        JMenuItem guideItem = new JMenuItem(ThemeConfig.getString("menu.help.guide"));
        guideItem.addActionListener(e -> showHelpDialog());
        
        JMenuItem aboutItem = new JMenuItem(ThemeConfig.getString("menu.help.about"));
        aboutItem.addActionListener(e -> showAboutDialog());
        
        helpMenu.add(guideItem);
        helpMenu.addSeparator();
        helpMenu.add(aboutItem);
        
        menuBar.add(fileMenu);
        menuBar.add(helpMenu);
        
        setJMenuBar(menuBar);
    }
    
    private void showHelpDialog() {
        JDialog helpDialog = new JDialog(this, "Help & Guide", false);
        helpDialog.setSize(900, 650);
        helpDialog.setLocationRelativeTo(this);
        helpDialog.add(new HelpPanel());
        helpDialog.setVisible(true);
    }
    
    private void showAboutDialog() {
        // Create custom dialog
        JDialog dialog = new JDialog(this, ThemeConfig.getString("about.title"), true);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setSize(500, 280);
        dialog.setLocationRelativeTo(this);
        
        // Content panel
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(Color.WHITE);
        contentPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        
        // RAKS Logo
        try {
            java.net.URL logoUrl = getClass().getResource("/raksLogo.png");
            if (logoUrl != null) {
                ImageIcon originalIcon = new ImageIcon(logoUrl);
                Image scaledImage = originalIcon.getImage().getScaledInstance(60, 60, Image.SCALE_SMOOTH);
                JLabel iconLabel = new JLabel(new ImageIcon(scaledImage));
                iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
                contentPanel.add(iconLabel);
            }
        } catch (Exception e) {
            // Fallback icon
            JLabel iconLabel = new JLabel("⬢");
            iconLabel.setFont(new Font("Arial Unicode MS", Font.BOLD, 48));
            iconLabel.setForeground(ThemeConfig.PRIMARY_COLOR);
            iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            contentPanel.add(iconLabel);
        }
        
        contentPanel.add(Box.createVerticalStrut(15));
        
        // Title
        JLabel titleLabel = new JLabel(ThemeConfig.getString("app.name") + " v" + ThemeConfig.getString("app.version"));
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18));
        titleLabel.setForeground(Color.BLACK);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        // Subtitle
        JLabel subtitleLabel = new JLabel(ThemeConfig.getString("app.subtitle"));
        subtitleLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        subtitleLabel.setForeground(Color.BLACK);
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        // Copyright
        JLabel copyrightLabel = new JLabel(ThemeConfig.getString("app.copyright"));
        copyrightLabel.setFont(new Font("Arial", Font.PLAIN, 11));
        copyrightLabel.setForeground(new Color(74, 85, 104));
        copyrightLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        contentPanel.add(titleLabel);
        contentPanel.add(Box.createVerticalStrut(5));
        contentPanel.add(subtitleLabel);
        contentPanel.add(Box.createVerticalStrut(15));
        contentPanel.add(copyrightLabel);
        contentPanel.add(Box.createVerticalStrut(5));
        
        // Contact Info (Configurable)
        JLabel contactLabel = new JLabel(ThemeConfig.getString("about.contact"));
        contactLabel.setFont(new Font("Arial", Font.PLAIN, 11));
        contactLabel.setForeground(ThemeConfig.PRIMARY_COLOR);
        contactLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        contentPanel.add(contactLabel);
        
        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.setBackground(Color.WHITE);
        
        JButton okButton = new JButton("OK");
        // Force Basic UI to ensure background color works on all platforms/LAFs
        okButton.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        okButton.setBackground(ThemeConfig.BUTTON_BACKGROUND);
        okButton.setForeground(Color.WHITE);
        okButton.setFont(new Font("Arial", Font.BOLD, 12));
        // Thicken border to 2px
        okButton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ThemeConfig.BUTTON_BORDER, 2),
            new EmptyBorder(8, 30, 8, 30)
        ));
        okButton.setFocusPainted(false);
        okButton.setOpaque(true); // Required for background color
        okButton.addActionListener(e -> dialog.dispose());
        
        buttonPanel.add(okButton);
        
        dialog.add(contentPanel, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }
    
    private void applyTheme() {
        // Apply theme to all components
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            
            // Customize UI defaults
            // UIManager.put("Button.background", ThemeConfig.BUTTON_BACKGROUND);
            // UIManager.put("Button.foreground", ThemeConfig.BUTTON_TEXT);
            // UIManager.put("Button.font", ThemeConfig.getScaledFont("Arial", Font.BOLD, 12));
            
            // Headlines and Titles in Purple
            UIManager.put("TitledBorder.titleColor", ThemeConfig.PRIMARY_COLOR);
            UIManager.put("TitledBorder.font", ThemeConfig.getScaledFont("Arial", Font.BOLD, 14));
            
            // Tabbed Pane
            UIManager.put("TabbedPane.foreground", ThemeConfig.PRIMARY_COLOR);
            UIManager.put("TabbedPane.font", ThemeConfig.getScaledFont("Arial", Font.BOLD, 13));
            
            // Panel and general UI
            UIManager.put("Panel.background", ThemeConfig.PANEL_BACKGROUND);
            UIManager.put("TabbedPane.selected", ThemeConfig.PRIMARY_COLOR);
            
            // Text components
            UIManager.put("TextField.font", ThemeConfig.getScaledFont("Arial", Font.PLAIN, 13));
            UIManager.put("TextArea.font", ThemeConfig.getScaledFont("Consolas", Font.PLAIN, 12));
            
        } catch (Exception e) {
            // Use default look and feel
        }
    }
    
    public static void launch() {
        SwingUtilities.invokeLater(() -> {
            MainWindow window = new MainWindow();
            window.setVisible(true);
        });
    }
}
