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
    private HelpPanel helpPanel;
    
    public MainWindow() {
        initializeUI();
        applyTheme();
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
            java.net.URL logoUrl = getClass().getResource("/logo.png");
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
        helpPanel = new HelpPanel();
        
        // Add tabs
        tabbedPane.addTab(ThemeConfig.getString("tab.discovery"), discoveryPanel);
        tabbedPane.addTab(ThemeConfig.getString("tab.mapping"), mappingPanel);
        tabbedPane.addTab(ThemeConfig.getString("tab.execute"), executePanel);
        tabbedPane.addTab(ThemeConfig.getString("tab.help"), helpPanel);
        
        // Add listener to refresh mapping panel when switching to it
        tabbedPane.addChangeListener(e -> {
            int selectedIndex = tabbedPane.getSelectedIndex();
            if (selectedIndex == 1) { // Mapping tab
                mappingPanel.refreshSourceFiles();
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
        
        // RAKS Logo
        try {
            java.net.URL logoUrl = getClass().getResource("/logo.png");
            if (logoUrl != null) {
                ImageIcon originalIcon = new ImageIcon(logoUrl);
                Image scaledImage = originalIcon.getImage().getScaledInstance(45, 45, Image.SCALE_SMOOTH);
                JLabel logoLabel = new JLabel(new ImageIcon(scaledImage));
                titlePanel.add(logoLabel);
            }
        } catch (Exception e) {
            // Fallback to text if logo not found
            JLabel logoLabel = new JLabel("RAKS");
            logoLabel.setFont(new Font("Arial", Font.BOLD, 24));
            logoLabel.setForeground(Color.WHITE);
            titlePanel.add(logoLabel);
        }
        
        // Title and subtitle
        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setBackground(ThemeConfig.HEADER_BACKGROUND);
        
        JLabel titleLabel = new JLabel(ThemeConfig.getString("app.name"));
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        titleLabel.setForeground(Color.WHITE);
        
        JLabel subtitleLabel = new JLabel(ThemeConfig.getString("app.subtitle"));
        subtitleLabel.setFont(new Font("Arial", Font.BOLD, 12));
        subtitleLabel.setForeground(Color.WHITE);
        
        textPanel.add(titleLabel);
        textPanel.add(Box.createVerticalStrut(3));
        textPanel.add(subtitleLabel);
        
        titlePanel.add(textPanel);
        header.add(titlePanel, BorderLayout.WEST);
        
        // Version label
        JLabel versionLabel = new JLabel("v" + ThemeConfig.getString("app.version"));
        versionLabel.setFont(new Font("Arial", Font.BOLD, 14));
        versionLabel.setForeground(Color.WHITE);
        header.add(versionLabel, BorderLayout.EAST);
        
        return header;
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
        guideItem.addActionListener(e -> tabbedPane.setSelectedIndex(3)); // Switch to Help tab
        
        JMenuItem aboutItem = new JMenuItem(ThemeConfig.getString("menu.help.about"));
        aboutItem.addActionListener(e -> showAboutDialog());
        
        helpMenu.add(guideItem);
        helpMenu.addSeparator();
        helpMenu.add(aboutItem);
        
        menuBar.add(fileMenu);
        menuBar.add(helpMenu);
        
        setJMenuBar(menuBar);
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
            java.net.URL logoUrl = getClass().getResource("/logo.png");
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
        
        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.setBackground(Color.WHITE);
        
        JButton okButton = new JButton("OK");
        okButton.setBackground(ThemeConfig.BUTTON_BACKGROUND);
        okButton.setForeground(Color.WHITE);
        okButton.setFont(new Font("Arial", Font.BOLD, 12));
        okButton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ThemeConfig.BUTTON_BORDER, 1),
            new EmptyBorder(8, 30, 8, 30)
        ));
        okButton.setFocusPainted(false);
        okButton.setOpaque(true);
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
            
            // Customize UI defaults for better button visibility
            UIManager.put("Button.background", ThemeConfig.BUTTON_BACKGROUND);
            UIManager.put("Button.foreground", ThemeConfig.BUTTON_TEXT);
            UIManager.put("Button.font", new Font("Arial", Font.BOLD, 12));
            UIManager.put("Button.select", ThemeConfig.PRIMARY_DARK);
            
            // Panel and general UI
            UIManager.put("Panel.background", ThemeConfig.PANEL_BACKGROUND);
            UIManager.put("TabbedPane.selected", ThemeConfig.PRIMARY_COLOR);
            UIManager.put("TabbedPane.contentBorderInsets", new Insets(2, 2, 2, 2));
            
            // Text components
            UIManager.put("TextField.background", Color.WHITE);
            UIManager.put("TextArea.background", Color.WHITE);
            
            // Option pane buttons
            UIManager.put("OptionPane.buttonFont", new Font("Arial", Font.BOLD, 12));
            UIManager.put("OptionPane.messageFont", new Font("Arial", Font.PLAIN, 13));
            
        } catch (Exception e) {
            // Use default look and feel
        }
        
        // Apply custom button renderer to ensure colors are applied
        UIManager.put("ButtonUI", "javax.swing.plaf.basic.BasicButtonUI");
    }
    
    public static void launch() {
        SwingUtilities.invokeLater(() -> {
            MainWindow window = new MainWindow();
            window.setVisible(true);
        });
    }
}
