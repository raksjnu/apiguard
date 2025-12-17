package com.raks.apiurlcomparison;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TestDataGenerator {
    private static final Logger logger = LoggerFactory.getLogger(TestDataGenerator.class);

    public static List<Map<String, Object>> generate(Map<String, List<Object>> tokens, int maxIterations) {
        return generate(tokens, maxIterations, "ALL_COMBINATIONS");
    }

    public static List<Map<String, Object>> generate(Map<String, List<Object>> tokens, int maxIterations,
            String strategy) {
        if ("ONE_BY_ONE".equalsIgnoreCase(strategy)) {
            return generateOneByOne(tokens, maxIterations);
        }
        return generateAllCombinations(tokens, maxIterations);
    }

    private static List<Map<String, Object>> generateOneByOne(Map<String, List<Object>> tokens, int maxIterations) {
        List<Map<String, Object>> iterations = new ArrayList<>();
        if (tokens == null || tokens.isEmpty()) {
            iterations.add(Map.of());
            return iterations;
        }

        // 1. Identify "defaults" (first value of each token)
        Map<String, Object> defaults = new java.util.HashMap<>();
        for (Map.Entry<String, List<Object>> entry : tokens.entrySet()) {
            List<Object> values = entry.getValue();
            if (values != null && !values.isEmpty()) {
                defaults.put(entry.getKey(), values.get(0));
            } else {
                defaults.put(entry.getKey(), ""); // fallback
            }
        }

        // Add Baseline (All Defaults)
        iterations.add(new java.util.HashMap<>(defaults));

        // 2. Iterate each token and each of its values
        for (Map.Entry<String, List<Object>> entry : tokens.entrySet()) {
            String currentToken = entry.getKey();
            List<Object> values = entry.getValue();

            if (values == null || values.isEmpty())
                continue;

            Object mainDefault = defaults.get(currentToken);

            for (Object value : values) {
                // Skip if value equals default (already covered by Baseline)
                if (mainDefault != null && mainDefault.equals(value)) {
                    continue;
                }

                if (iterations.size() >= maxIterations) {
                    logger.warn("Maximum number of iterations ({}) reached via ONE_BY_ONE.", maxIterations);
                    return iterations;
                }

                // Create a copy of defaults, but overwrite the current token's value
                Map<String, Object> combination = new java.util.HashMap<>(defaults);
                combination.put(currentToken, value);
                iterations.add(combination);
            }
        }

        return iterations;
    }

    private static List<Map<String, Object>> generateAllCombinations(Map<String, List<Object>> tokens,
            int maxIterations) {
        List<Map<String, Object>> iterations = new ArrayList<>();
        if (tokens == null || tokens.isEmpty()) {
            iterations.add(Map.of()); // Ensure at least one run if no tokens
            return iterations;
        }

        // Start with a list containing one empty map, which will be built upon.
        iterations.add(new java.util.HashMap<>());

        // Loop through each token and its list of values to build the Cartesian product
        for (Map.Entry<String, List<Object>> tokenEntry : tokens.entrySet()) {
            String tokenName = tokenEntry.getKey();
            List<Object> tokenValues = tokenEntry.getValue();
            List<Map<String, Object>> newIterations = new ArrayList<>();

            // For each existing combination, create new ones by adding the current token's
            // values
            for (Map<String, Object> existingCombination : iterations) {
                for (Object value : tokenValues) {
                    Map<String, Object> newCombination = new java.util.HashMap<>(existingCombination);
                    newCombination.put(tokenName, value);
                    newIterations.add(newCombination);

                    // Early exit if maxIterations is reached
                    if (newIterations.size() >= maxIterations) {
                        logger.warn("Maximum number of iterations ({}) reached. Halting combination generation.",
                                maxIterations);
                        return newIterations;
                    }
                }
            }
            // Replace the old list of iterations with the newly expanded one
            iterations = newIterations;
        }
        return iterations;
    }
}
