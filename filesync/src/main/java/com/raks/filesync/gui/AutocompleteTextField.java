package com.raks.filesync.gui;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Autocomplete text field with suggestion dropdown using JList
 */
public class AutocompleteTextField extends JTextField {
    private final JPopupMenu suggestionPopup;
    private final JList<String> suggestionList;
    private final DefaultListModel<String> listModel;
    private final List<String> suggestions;
    private boolean isUpdating = false;
    
    public AutocompleteTextField(int columns) {
        super(columns);
        this.suggestions = new ArrayList<>();
        this.listModel = new DefaultListModel<>();
        this.suggestionList = new JList<>(listModel);
        this.suggestionPopup = new JPopupMenu();
        
        initializeComponent();
        setupListeners();
    }
    
    private void initializeComponent() {
        suggestionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        suggestionList.setFont(new Font("Arial", Font.PLAIN, 12));
        suggestionList.setFocusable(false);
        
        // Add scroll pane to popup
        JScrollPane scrollPane = new JScrollPane(suggestionList);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setFocusable(false);
        scrollPane.getHorizontalScrollBar().setFocusable(false);
        
        suggestionPopup.add(scrollPane);
        suggestionPopup.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        suggestionPopup.setFocusable(false);
        
        // Handle list selection
        suggestionList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1) {
                    selectSuggestion();
                }
            }
        });
    }
    
    private void setupListeners() {
        // Document listener to filter suggestions as user types
        getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                if (!isUpdating) updateSuggestions();
            }
            
            @Override
            public void removeUpdate(DocumentEvent e) {
                if (!isUpdating) updateSuggestions();
            }
            
            @Override
            public void changedUpdate(DocumentEvent e) {
                if (!isUpdating) updateSuggestions();
            }
        });
        
        // Show suggestions on focus
        addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                if (getText().trim().isEmpty()) {
                    updateSuggestions();
                }
            }
        });
        
        // Key listener for navigation
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    if (!suggestionPopup.isVisible()) {
                        updateSuggestions();
                    } else {
                        // Move selection down
                        int next = suggestionList.getSelectedIndex() + 1;
                        if (next < listModel.getSize()) {
                            suggestionList.setSelectedIndex(next);
                            suggestionList.ensureIndexIsVisible(next);
                        }
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_UP) {
                    if (suggestionPopup.isVisible()) {
                        // Move selection up
                        int prev = suggestionList.getSelectedIndex() - 1;
                        if (prev >= 0) {
                            suggestionList.setSelectedIndex(prev);
                            suggestionList.ensureIndexIsVisible(prev);
                        }
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (suggestionPopup.isVisible() && suggestionList.getSelectedIndex() != -1) {
                        selectSuggestion();
                        e.consume();
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    suggestionPopup.setVisible(false);
                }
            }
        });
    }
    
    private void updateSuggestions() {
        String text = getText().trim().toLowerCase();
        listModel.clear();
        
        List<String> filtered;
        if (text.isEmpty()) {
            filtered = new ArrayList<>(suggestions);
        } else {
            // Priority: StartsWith > Contains > Alphabetical
            filtered = suggestions.stream()
                .filter(s -> s.toLowerCase().contains(text))
                .sorted((s1, s2) -> {
                    boolean s1Starts = s1.toLowerCase().startsWith(text);
                    boolean s2Starts = s2.toLowerCase().startsWith(text);
                    if (s1Starts && !s2Starts) return -1;
                    if (!s1Starts && s2Starts) return 1;
                    return s1.compareToIgnoreCase(s2);
                })
                .limit(20)
                .collect(Collectors.toList());
        }
        
        if (filtered.isEmpty()) {
            suggestionPopup.setVisible(false);
            return;
        }
        
        // Add to model
        for (String s : filtered) {
            listModel.addElement(s);
        }
        
        // Calculate size - match text field width (min)
        int width = getWidth();
        int height = Math.min(filtered.size() * 20 + 5, 200); // Max 200px height
        
        suggestionPopup.setPreferredSize(new Dimension(width, height));
        suggestionPopup.pack(); // Important to layout components
        
        // Show logic
        if (!suggestionPopup.isVisible()) {
            suggestionPopup.show(this, 0, getHeight());
        }
        
        // Select first item if typing
        if (!text.isEmpty()) {
            suggestionList.setSelectedIndex(0);
        }
        
        requestFocusInWindow();
    }
    
    private void selectSuggestion() {
        String selected = suggestionList.getSelectedValue();
        if (selected != null) {
            isUpdating = true;
            setText(selected);
            isUpdating = false;
            suggestionPopup.setVisible(false);
            // Move caret to end
            setCaretPosition(selected.length());
        }
    }
    
    /**
     * Add a suggestion to the list
     */
    public void addSuggestion(String suggestion) {
        if (suggestion != null && !suggestion.trim().isEmpty() && !suggestions.contains(suggestion)) {
            suggestions.add(suggestion);
        }
    }
    
    /**
     * Set all suggestions at once
     */
    public void setSuggestions(List<String> newSuggestions) {
        suggestions.clear();
        if (newSuggestions != null) {
            suggestions.addAll(newSuggestions);
        }
    }
}
