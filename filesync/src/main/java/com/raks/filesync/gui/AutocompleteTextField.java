package com.raks.filesync.gui;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Autocomplete text field with suggestion dropdown
 */
public class AutocompleteTextField extends JTextField {
    private final JPopupMenu suggestionPopup;
    private final List<String> suggestions;
    private List<String> filteredSuggestions;
    
    public AutocompleteTextField(int columns) {
        super(columns);
        this.suggestions = new ArrayList<>();
        this.filteredSuggestions = new ArrayList<>();
        this.suggestionPopup = new JPopupMenu();
        
        setupListeners();
    }
    
    private void setupListeners() {
        // Document listener to filter suggestions as user types
        getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateSuggestions();
            }
            
            @Override
            public void removeUpdate(DocumentEvent e) {
                updateSuggestions();
            }
            
            @Override
            public void changedUpdate(DocumentEvent e) {
                updateSuggestions();
            }
        });
        
        // Key listener for navigation
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    suggestionPopup.setVisible(false);
                }
            }
        });
    }
    
    private void updateSuggestions() {
        String text = getText().trim().toLowerCase();
        
        if (text.isEmpty()) {
            suggestionPopup.setVisible(false);
            return;
        }
        
        // Filter suggestions based on input
        filteredSuggestions = suggestions.stream()
                .filter(s -> s.toLowerCase().contains(text))
                .limit(10) // Show max 10 suggestions
                .collect(Collectors.toList());
        
        if (filteredSuggestions.isEmpty()) {
            suggestionPopup.setVisible(false);
            return;
        }
        
        // Update popup menu
        suggestionPopup.removeAll();
        
        for (String suggestion : filteredSuggestions) {
            JMenuItem item = new JMenuItem(suggestion);
            item.setFont(new Font("Arial", Font.PLAIN, 12));
            item.addActionListener(e -> {
                setText(suggestion);
                suggestionPopup.setVisible(false);
                requestFocus();
            });
            suggestionPopup.add(item);
        }
        
        // Show popup below the text field
        if (!suggestionPopup.isVisible()) {
            suggestionPopup.show(this, 0, getHeight());
        } else {
            suggestionPopup.revalidate();
            suggestionPopup.repaint();
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
    
    /**
     * Get all suggestions
     */
    public List<String> getSuggestions() {
        return new ArrayList<>(suggestions);
    }
    
    /**
     * Clear all suggestions
     */
    public void clearSuggestions() {
        suggestions.clear();
        filteredSuggestions.clear();
        suggestionPopup.setVisible(false);
    }
}
