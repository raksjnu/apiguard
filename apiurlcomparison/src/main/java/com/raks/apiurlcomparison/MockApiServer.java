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
public class MockApiServer {
    private static final Logger logger = LoggerFactory.getLogger(MockApiServer.class);
    public static void main(String[] args) {
        ObjectMapper mapper = new ObjectMapper();
        Service api1 = Service.ignite();
        api1.port(8081);
        api1.before((req, res) -> {
            String auth = req.headers("Authorization");
            if (auth != null) {
                logger.info("Received Authorization header: {}", auth);
            } else {
                logger.warn("No Authorization header received on API 1");
            }
        });
        api1.post("/api/resource", (req, res) -> {
            res.type("application/json");
            try {
                JsonNode payload = mapper.readTree(req.body());
                String account = payload.has("account") ? payload.get("account").asText() : "";
                if ("999".equals(account)) {
                    return "{\"status\":\"success\",\"id\":\"api1-specific-id-for-999\",\"timestamp\":\"2025-12-01T11:00:00Z\"}";
                } else {
                    return "{\"status\":\"success\",\"id\":\"shared-id-12345\",\"timestamp\":\"2025-12-01T10:00:00Z\"}";
                }
            } catch (Exception e) {
                res.status(400);
                return "{\"error\":\"Invalid JSON payload\"}";
            }
        });
        api1.get("/api/resource", (req, res) -> {
            res.type("application/json");
            return "{\"status\":\"success\",\"message\":\"Resource details retrieved via GET\"}";
        });
        api1.get("/api/anotherResource", (req, res) -> {
            res.type("application/json");
            return "{\"status\":\"success\",\"data\":\"This is the other resource\"}";
        });
        api1.awaitInitialization();
        logger.info("Mock API 1 started on http://localhost:8081");
        Service api2 = Service.ignite();
        api2.port(8082);
        api2.before((req, res) -> {
            String auth = req.headers("Authorization");
            if (auth != null)
                logger.info("Received Authorization header on API 2: {}", auth);
        });
        api2.post("/api/resource", (req, res) -> {
            res.type("application/json");
            return "{\"status\":\"success\",\"id\":\"shared-id-12345\",\"timestamp\":\"2025-12-01T10:00:00Z\"}";
        });
        api2.get("/api/resource", (req, res) -> {
            res.type("application/json");
            return "{\"status\":\"success\",\"message\":\"Resource details retrieved from API 2\"}";
        });
        api2.get("/api/anotherResource", (req, res) -> {
            res.type("application/json");
            return "{\"status\":\"success\",\"data\":\"This is the other resource from API 2\"}";
        });
        api2.awaitInitialization();
        logger.info("Mock API 2 started on http://localhost:8082");
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
            if ("\"getAccountDetails\"".equals(soapAction) || "getAccountDetails".equals(soapAction)) {
                String account = extractAccountFromSoap(req.body());
                if ("999".equals(account)) {
                    return "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>"
                            + "<ns2:getAccountDetailsResponse xmlns:ns2=\"http://service.ws.myorg.com/\">"
                            + "<return><status>SUCCESS</status><transactionId>soap-api1-specific-id-for-999</transactionId>"
                            + "<timestamp>2025-12-01T11:00:00Z</timestamp></return>"
                            + "</ns2:getAccountDetailsResponse></soap:Body></soap:Envelope>";
                } else {
                    return "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>"
                            + "<ns2:getAccountDetailsResponse xmlns:ns2=\"http://service.ws.myorg.com/\">"
                            + "<return><status>SUCCESS</status><transactionId>shared-soap-id-12345</transactionId>"
                            + "<timestamp>2025-12-01T10:00:00Z</timestamp></return>"
                            + "</ns2:getAccountDetailsResponse></soap:Body></soap:Envelope>";
                }
            } else if ("\"/Service/orderService.serviceagent/createOrderEndpoint1/Operation\"".equals(soapAction)) {
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
    private static String extractAccountFromSoap(String soapPayload) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(soapPayload)));
            NodeList nodes = doc.getElementsByTagName("AccountNumber");
            if (nodes.getLength() > 0) {
                return nodes.item(0).getTextContent();
            }
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