package com.raks.apiurlcomparison;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import spark.Service;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

import static spark.Spark.*;

/**
 * A simple, self-contained web server to mock two API endpoints for testing.
 * Run the main method in this class to start the servers before running the
 * comparison tool.
 */
public class MockApiServer {

    private static final Logger logger = LoggerFactory.getLogger(MockApiServer.class);

    public static void main(String[] args) {
        ObjectMapper mapper = new ObjectMapper();

        // --- Create and configure REST API 1 on port 8081 ---
        Service api1 = Service.ignite();
        api1.port(8081);

        // Add auth check filter
        api1.before((req, res) -> {
            String auth = req.headers("Authorization");
            if (auth != null) {
                logger.info("Received Authorization header: {}", auth);
                // In a real scenario, we would validate the credentials here.
                // For now, we just log it to confirm it's being sent.
            } else {
                logger.warn("No Authorization header received on API 1");
            }
        });

        // Handler for POST /api/resource
        api1.post("/api/resource", (req, res) -> {
            res.type("application/json");
            try {
                JsonNode payload = mapper.readTree(req.body());
                String account = payload.has("account") ? payload.get("account").asText() : "";

                // Simulate different responses based on the account number
                if ("999".equals(account)) {
                    // This response will be different from API 2 for this specific account
                    return "{\"status\":\"success\",\"id\":\"api1-specific-id-for-999\",\"timestamp\":\"2025-12-01T11:00:00Z\"}";
                } else {
                    // Default response for all other accounts
                    return "{\"status\":\"success\",\"id\":\"shared-id-12345\",\"timestamp\":\"2025-12-01T10:00:00Z\"}";
                }
            } catch (Exception e) {
                res.status(400);
                return "{\"error\":\"Invalid JSON payload\"}";
            }
        });
        // Handler for GET /api/resource
        api1.get("/api/resource", (req, res) -> {
            res.type("application/json");
            // Return a generic GET response
            return "{\"status\":\"success\",\"message\":\"Resource details retrieved via GET\"}";
        });
        // Handler for GET /api/anotherResource
        api1.get("/api/anotherResource", (req, res) -> {
            res.type("application/json");
            // Return a specific response for this other resource
            return "{\"status\":\"success\",\"data\":\"This is the other resource\"}";
        });

        api1.awaitInitialization();
        logger.info("Mock API 1 started on http://localhost:8081");

        // --- Create and configure REST API 2 on port 8082 ---
        Service api2 = Service.ignite();
        api2.port(8082);
        api2.before((req, res) -> {
            String auth = req.headers("Authorization");
            if (auth != null)
                logger.info("Received Authorization header on API 2: {}", auth);
        });
        api2.post("/api/resource", (req, res) -> {
            res.type("application/json");

            // API 2 always returns the "standard" POST response.
            // This will cause a mismatch when API 1 gets account "999".
            return "{\"status\":\"success\",\"id\":\"shared-id-12345\",\"timestamp\":\"2025-12-01T10:00:00Z\"}";
        });
        // Adding a GET handler to api2 for more comprehensive comparison against api1
        api2.get("/api/resource", (req, res) -> {
            res.type("application/json");
            // Return a different GET response than api1 to test mismatches
            return "{\"status\":\"success\",\"message\":\"Resource details retrieved from API 2\"}";
        });

        // Add GET /api/anotherResource to api2 for comparison
        api2.get("/api/anotherResource", (req, res) -> {
            res.type("application/json");
            return "{\"status\":\"success\",\"data\":\"This is the other resource from API 2\"}";
        });
        api2.awaitInitialization();
        logger.info("Mock API 2 started on http://localhost:8082");

        // --- Create and configure SOAP API 1 on port 8083 ---
        Service soapApi1 = Service.ignite();
        soapApi1.port(8083);
        soapApi1.before((req, res) -> {
            String auth = req.headers("Authorization");
            if (auth != null)
                logger.info("Received Authorization header on SOAP API 1: {}", auth);
        });
        soapApi1.post("/ws/AccountService", (req, res) -> {
            String soapAction = req.headers("SOAPAction");
            res.type("text/xml");

            // Differentiate response based on SOAPAction
            if ("\"getAccountDetails\"".equals(soapAction) || "getAccountDetails".equals(soapAction)) {
                String account = extractAccountFromSoap(req.body());
                if ("999".equals(account)) {
                    // Special response for this account
                    return "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>"
                            + "<ns2:getAccountDetailsResponse xmlns:ns2=\"http://service.ws.myorg.com/\">"
                            + "<return><status>SUCCESS</status><transactionId>soap-api1-specific-id-for-999</transactionId>"
                            + "<timestamp>2025-12-01T11:00:00Z</timestamp></return>"
                            + "</ns2:getAccountDetailsResponse></soap:Body></soap:Envelope>";
                } else {
                    // Default response
                    return "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>"
                            + "<ns2:getAccountDetailsResponse xmlns:ns2=\"http://service.ws.myorg.com/\">"
                            + "<return><status>SUCCESS</status><transactionId>shared-soap-id-12345</transactionId>"
                            + "<timestamp>2025-12-01T10:00:00Z</timestamp></return>"
                            + "</ns2:getAccountDetailsResponse></soap:Body></soap:Envelope>";
                }
            } else if ("\"/Service/orderService.serviceagent/createOrderEndpoint1/Operation\"".equals(soapAction)) {
                // Response for the createOrder operation
                return "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>"
                        + "<ns2:createOrderResponse xmlns:ns2=\"http://service.ws.myorg.com/\">"
                        + "<return><status>ORDER_CREATED</status><orderId>order-9876</orderId></return>"
                        + "</ns2:createOrderResponse></soap:Body></soap:Envelope>";
            } else {
                res.status(400);
                return "Unsupported SOAPAction: " + soapAction;
            }
        });
        soapApi1.awaitInitialization();
        logger.info("Mock SOAP API 1 started on http://localhost:8083");

        // --- Create and configure SOAP API 2 on port 8084 ---
        Service soapApi2 = Service.ignite();
        soapApi2.port(8084);
        soapApi2.before((req, res) -> {
            String auth = req.headers("Authorization");
            if (auth != null)
                logger.info("Received Authorization header on SOAP API 2: {}", auth);
        });
        soapApi2.post("/ws/AccountService", (req, res) -> {
            String soapAction = req.headers("SOAPAction");
            res.type("text/xml");
            // This API always returns the standard response for the configured SOAPAction
            if ("\"getAccountDetails\"".equals(soapAction) || "getAccountDetails".equals(soapAction)) {
                return "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>"
                        + "<ns2:getAccountDetailsResponse xmlns:ns2=\"http://service.ws.myorg.com/\">"
                        + "<return><status>SUCCESS</status><transactionId>shared-soap-id-12345</transactionId>"
                        + "<timestamp>2025-12-01T10:00:00Z</timestamp></return>"
                        + "</ns2:getAccountDetailsResponse></soap:Body></soap:Envelope>";
            } else {
                res.status(400);
                return "Unsupported SOAPAction: " + soapAction;
            }
        });
        soapApi2.awaitInitialization();
        logger.info("Mock SOAP API 2 started on http://localhost:8084");

        logger.info("All mock servers are running. Press Ctrl+C to stop.");
    }

    /**
     * Extracts the account number from a SOAP XML payload using standard DOM
     * parsing.
     */
    private static String extractAccountFromSoap(String soapPayload) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Important: disable DTDs for security (XXE prevention)
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(soapPayload)));

            // Search for "AccountNumber" tag (case-sensitive)
            NodeList nodes = doc.getElementsByTagName("AccountNumber");
            if (nodes.getLength() > 0) {
                return nodes.item(0).getTextContent();
            }

            // Fallback: also try lowercase "account" for compatibility
            nodes = doc.getElementsByTagName("account");
            if (nodes.getLength() > 0) {
                return nodes.item(0).getTextContent();
            }
        } catch (Exception e) {
            logger.warn("Failed to parse SOAP payload to extract account. Payload snippet: {}",
                    soapPayload.substring(0, Math.min(soapPayload.length(), 100)));
        }
        return "";
    }
}