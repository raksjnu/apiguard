package com.raks.raksanalyzer.core.utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
public class PropertyResolverTest {
    private PropertyResolver resolver;
    @BeforeEach
    public void setUp() {
        resolver = new PropertyResolver();
    }
    @Test
    public void testSimpleDollarBracePlaceholder() {
        resolver.setProperty("host", "localhost");
        resolver.setProperty("port", "8081");
        String result = resolver.resolve("${host}:${port}");
        assertEquals("localhost:8081", result);
    }
    @Test
    public void testPFunctionPlaceholder() {
        resolver.setProperty("db.host", "dbserver");
        resolver.setProperty("db.port", "3306");
        String result = resolver.resolve("p('db.host'):p('db.port')");
        assertEquals("dbserver:3306", result);
    }
    @Test
    public void testMixedPlaceholders() {
        resolver.setProperty("http.host", "0.0.0.0");
        resolver.setProperty("http.port", "8080");
        String result = resolver.resolve("${http.host}:p('http.port')/api");
        assertEquals("0.0.0.0:8080/api", result);
    }
    @Test
    public void testMissingProperty() {
        String result = resolver.resolve("${missing.property}");
        assertEquals("${missing.property}", result); 
    }
    @Test
    public void testCircularReference() {
        resolver.setProperty("prop1", "${prop2}");
        resolver.setProperty("prop2", "${prop1}");
        String result = resolver.resolve("${prop1}");
        assertTrue(result.contains("${prop")); 
    }
    @Test
    public void testNoPlaceholders() {
        String result = resolver.resolve("plain text");
        assertEquals("plain text", result);
    }
    @Test
    public void testNullValue() {
        String result = resolver.resolve(null);
        assertNull(result);
    }
    @Test
    public void testEmptyValue() {
        String result = resolver.resolve("");
        assertEquals("", result);
    }
    @Test
    public void testHasUnresolvedPlaceholders() {
        assertTrue(resolver.hasUnresolvedPlaceholders("${host}"));
        assertTrue(resolver.hasUnresolvedPlaceholders("p('port')"));
        assertFalse(resolver.hasUnresolvedPlaceholders("localhost"));
        assertFalse(resolver.hasUnresolvedPlaceholders(null));
    }
    @Test
    public void testLoadPropertiesFile(@TempDir Path tempDir) throws IOException {
        Path propFile = tempDir.resolve("test.properties");
        Files.writeString(propFile, 
            "http.host=0.0.0.0\n" +
            "http.port=8081\n" +
            "db.host=localhost\n" +
            "db.port=3306\n"
        );
        assertTrue(resolver.loadPropertiesFile(propFile));
        assertEquals(4, resolver.getPropertyCount());
        assertEquals("0.0.0.0", resolver.getProperty("http.host"));
        assertEquals("8081", resolver.getProperty("http.port"));
    }
    @Test
    public void testLoadMultiplePropertiesFiles(@TempDir Path tempDir) throws IOException {
        Path propFile1 = tempDir.resolve("app.properties");
        Files.writeString(propFile1, 
            "http.host=0.0.0.0\n" +
            "http.port=8081\n"
        );
        Path propFile2 = tempDir.resolve("override.properties");
        Files.writeString(propFile2, 
            "http.port=9090\n" +
            "db.host=dbserver\n"
        );
        resolver.loadPropertiesFile(propFile1);
        resolver.loadPropertiesFile(propFile2);
        assertEquals(3, resolver.getPropertyCount());
        assertEquals("0.0.0.0", resolver.getProperty("http.host"));
        assertEquals("9090", resolver.getProperty("http.port")); 
        assertEquals("dbserver", resolver.getProperty("db.host"));
    }
    @Test
    public void testGetPropertyWithDefault() {
        resolver.setProperty("existing", "value");
        assertEquals("value", resolver.getProperty("existing", "default"));
        assertEquals("default", resolver.getProperty("missing", "default"));
    }
    @Test
    public void testSetProperties() {
        Map<String, String> props = new HashMap<>();
        props.put("key1", "value1");
        props.put("key2", "value2");
        resolver.setProperties(props);
        assertEquals(2, resolver.getPropertyCount());
        assertEquals("value1", resolver.getProperty("key1"));
        assertEquals("value2", resolver.getProperty("key2"));
    }
    @Test
    public void testGetAllProperties() {
        resolver.setProperty("prop1", "value1");
        resolver.setProperty("prop2", "value2");
        Map<String, String> allProps = resolver.getAllProperties();
        assertEquals(2, allProps.size());
        assertEquals("value1", allProps.get("prop1"));
        assertEquals("value2", allProps.get("prop2"));
        assertThrows(UnsupportedOperationException.class, () -> 
            allProps.put("prop3", "value3")
        );
    }
    @Test
    public void testClear() {
        resolver.setProperty("prop1", "value1");
        resolver.setProperty("prop2", "value2");
        assertEquals(2, resolver.getPropertyCount());
        resolver.clear();
        assertEquals(0, resolver.getPropertyCount());
    }
    @Test
    public void testComplexRealWorldScenario() {
        resolver.setProperty("http.host", "localhost");
        resolver.setProperty("http.port", "8081");
        resolver.setProperty("smtp.host", "smtp.gmail.com");
        resolver.setProperty("smtp.port", "587");
        String httpEndpoint = resolver.resolve("${http.host}:${http.port}/api");
        assertEquals("localhost:8081/api", httpEndpoint);
        String smtpEndpoint = resolver.resolve("smtp://p('smtp.host'):p('smtp.port')");
        assertEquals("smtp://smtp.gmail.com:587", smtpEndpoint);
    }
}
