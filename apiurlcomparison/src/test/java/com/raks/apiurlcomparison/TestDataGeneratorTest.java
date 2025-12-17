package com.raks.apiurlcomparison;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TestDataGeneratorTest {

    @Test
    void testAllCombinations() {
        Map<String, List<Object>> tokens = new HashMap<>();
        tokens.put("A", Arrays.asList(1, 2));
        tokens.put("B", Arrays.asList(3, 4, 5));

        List<Map<String, Object>> iterations = TestDataGenerator.generate(tokens, 100, "ALL_COMBINATIONS");

        // Size should be 2 * 3 = 6
        assertEquals(6, iterations.size());
    }

    @Test
    void testOneByOne() {
        Map<String, List<Object>> tokens = new HashMap<>();
        tokens.put("A", Arrays.asList("a1", "a2")); // 2 values
        tokens.put("B", Arrays.asList("b1", "b2", "b3")); // 3 values

        List<Map<String, Object>> iterations = TestDataGenerator.generate(tokens, 100, "ONE_BY_ONE");

        // Logic: Baseline + Unique Deviations
        // 1. Baseline: (a1, b1)
        // 2. Token A: (a1 skip), (a2) -> +1
        // 3. Token B: (b1 skip), (b2, b3) -> +2
        // Total = 4
        assertEquals(4, iterations.size());

        // Verify duplicates check (just optional, to confirm logic)
        long a1b1 = iterations.stream()
                .filter(m -> "a1".equals(m.get("A")) && "b1".equals(m.get("B")))
                .count();
        assertTrue(a1b1 >= 1);
    }

    @Test
    void testOneByOne_DefaultToFirst() {
        Map<String, List<Object>> tokens = new HashMap<>();
        tokens.put("A", Arrays.asList("a1", "a2"));

        List<Map<String, Object>> iterations = TestDataGenerator.generate(tokens, 100, "ONE_BY_ONE");
        // Baseline(1) + Val(a2) = 2
        assertEquals(2, iterations.size());
        assertEquals("a1", iterations.get(0).get("A")); // Baseline
        assertEquals("a2", iterations.get(1).get("A")); // Val 2 (a1 skipped)
    }
}
