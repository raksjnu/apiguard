package com.raks.raksanalyzer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ApplicationTest {
    @Test
    void testArgumentParsing_Default() {
        String[] args = {};
        ApplicationArguments arguments = ApplicationArguments.parse(args);
        assertFalse(arguments.getServerPort().isPresent(), "Default should not have port");
        assertTrue(arguments.shouldAutoOpenBrowser(), "Default should auto-open browser");
    }
    @Test
    void testArgumentParsing_CustomPort() {
        String[] args = {"-port", "9090"};
        ApplicationArguments arguments = ApplicationArguments.parse(args);
        assertTrue(arguments.getServerPort().isPresent());
        assertEquals(9090, arguments.getServerPort().get());
    }
    @Test
    void testArgumentParsing_NoBrowser() {
        String[] args = {"-no-browser"};
        ApplicationArguments arguments = ApplicationArguments.parse(args);
        assertFalse(arguments.shouldAutoOpenBrowser());
    }
}
