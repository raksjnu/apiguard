package com.raks.apiurlcomparison;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ComparisonEngineTest {

    @Test
    void testCompare_RestMatch() {
        ComparisonResult result = new ComparisonResult();
        ApiCallResult api1 = new ApiCallResult();
        api1.setResponsePayload("{\"status\":\"success\",\"id\":1}");
        ApiCallResult api2 = new ApiCallResult();
        api2.setResponsePayload("{\"status\":\"success\",\"id\":1}");

        result.setApi1(api1);
        result.setApi2(api2);

        ComparisonEngine.compare(result, "REST");

        assertEquals(ComparisonResult.Status.MATCH.name(), result.getStatus());
        assertTrue(result.getDifferences() == null || result.getDifferences().isEmpty());
    }

    @Test
    void testCompare_RestMismatch() {
        ComparisonResult result = new ComparisonResult();
        ApiCallResult api1 = new ApiCallResult();
        api1.setResponsePayload("{\"status\":\"success\",\"id\":1}");
        ApiCallResult api2 = new ApiCallResult();
        api2.setResponsePayload("{\"status\":\"success\",\"id\":2}");

        result.setApi1(api1);
        result.setApi2(api2);

        ComparisonEngine.compare(result, "REST");

        assertEquals(ComparisonResult.Status.MISMATCH.name(), result.getStatus());
        assertNotNull(result.getDifferences());
        assertFalse(result.getDifferences().isEmpty());
    }

    @Test
    void testCompare_RestStructureMismatch() {
        ComparisonResult result = new ComparisonResult();
        ApiCallResult api1 = new ApiCallResult();
        api1.setResponsePayload("{\"status\":\"success\"}");
        ApiCallResult api2 = new ApiCallResult();
        api2.setResponsePayload("{\"status\":\"success\",\"extra\":\"field\"}");

        result.setApi1(api1);
        result.setApi2(api2);

        ComparisonEngine.compare(result, "REST");

        assertEquals(ComparisonResult.Status.MISMATCH.name(), result.getStatus());
    }

    @Test
    void testCompare_SoapMatch() {
        ComparisonResult result = new ComparisonResult();
        ApiCallResult api1 = new ApiCallResult();
        api1.setResponsePayload("<root><status>ok</status></root>");
        ApiCallResult api2 = new ApiCallResult();
        api2.setResponsePayload("<root><status>ok</status></root>");

        result.setApi1(api1);
        result.setApi2(api2);

        ComparisonEngine.compare(result, "SOAP");

        assertEquals(ComparisonResult.Status.MATCH.name(), result.getStatus());
    }

    @Test
    void testCompare_SoapMismatch() {
        ComparisonResult result = new ComparisonResult();
        ApiCallResult api1 = new ApiCallResult();
        api1.setResponsePayload("<root><status>ok</status></root>");
        ApiCallResult api2 = new ApiCallResult();
        api2.setResponsePayload("<root><status>failed</status></root>");

        result.setApi1(api1);
        result.setApi2(api2);

        ComparisonEngine.compare(result, "SOAP");

        assertEquals(ComparisonResult.Status.MISMATCH.name(), result.getStatus());
    }

    @Test
    void testCompare_SoapWhitespaceIgnored() {
        ComparisonResult result = new ComparisonResult();
        ApiCallResult api1 = new ApiCallResult();
        api1.setResponsePayload("<root>  <status>ok</status>  </root>");
        ApiCallResult api2 = new ApiCallResult();
        api2.setResponsePayload("<root><status>ok</status></root>");

        result.setApi1(api1);
        result.setApi2(api2);

        ComparisonEngine.compare(result, "SOAP");

        assertEquals(ComparisonResult.Status.MATCH.name(), result.getStatus());
    }
}
