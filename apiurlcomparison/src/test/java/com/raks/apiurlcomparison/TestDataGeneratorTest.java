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
        assertEquals(6, iterations.size());
    }
    @Test
    void testOneByOne() {
        Map<String, List<Object>> tokens = new HashMap<>();
        tokens.put("A", Arrays.asList("a1", "a2")); 
        tokens.put("B", Arrays.asList("b1", "b2", "b3")); 
        List<Map<String, Object>> iterations = TestDataGenerator.generate(tokens, 100, "ONE_BY_ONE");
        assertEquals(4, iterations.size());
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
        assertEquals(2, iterations.size());
        assertEquals("a1", iterations.get(0).get("A")); 
        assertEquals("a2", iterations.get(1).get("A")); 
    }
}
