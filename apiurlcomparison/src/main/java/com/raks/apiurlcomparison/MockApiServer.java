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
    private static Service api1;
    private static Service api2;
    private static Service soapApi1;
    private static Service soapApi2;
    private static boolean isRunning = false;

    public static void main(String[] args) {
        start();
    }

    public static synchronized void start() {
        if (isRunning) {
            logger.info("Mock servers are already running.");
            return;
        }

        ObjectMapper mapper = new ObjectMapper();

        // API 1 (REST)
        api1 = Service.ignite();
        api1.port(9091);
        api1.before((req, res) -> {
            String auth = req.headers("Authorization");
            if (auth != null) {
                logger.info("Received Authorization header on API 1: {}", auth);
            } else {
                logger.warn("No Authorization header received on API 1");
            }
        });
        api1.post("/api/resource", (req, res) -> {
            res.header("Content-Type", "application/json; charset=utf-8");
            try {
                JsonNode payload = mapper.readTree(req.body());
                String account = payload.has("account") ? payload.get("account").asText() : "";
                String timestamp = java.time.Instant.now().toString();
                res.header("X-Dynamic-Header", java.util.UUID.randomUUID().toString());
                String extraFields = ",\"processId\":\"pid-" + java.util.UUID.randomUUID().toString().substring(0,8) + "\",\"randomId\":\"" + java.util.UUID.randomUUID() + "\"";
                if ("999".equals(account)) {
                    return "{\"status\":\"success\",\"id\":\"api1-specific-id-for-999\",\"timestamp\":\"" + timestamp + "\"" + extraFields + "}";
                } else {
                    return "{\"status\":\"success\",\"id\":\"shared-id-12345\",\"timestamp\":\"" + timestamp + "\"" + extraFields + "}";
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
        logger.info("Mock API 1 started on http://localhost:9091");

        // API 2 (REST)
        api2 = Service.ignite();
        api2.port(9092);
        api2.before((req, res) -> {
            String auth = req.headers("Authorization");
            if (auth != null)
                logger.info("Received Authorization header on API 2: {}", auth);
        });
        api2.post("/api/resource", (req, res) -> {
            res.header("Content-Type", "application/json; charset=utf-8");
            String timestamp = java.time.Instant.now().toString();
            res.header("X-Dynamic-Header", java.util.UUID.randomUUID().toString());
            String extraFields = ",\"processId\":\"" + java.util.UUID.randomUUID().toString().substring(0,8) + "\",\"randomId\":\"" + java.util.UUID.randomUUID() + "\"";
            return "{\"status\":\"success\",\"id\":\"shared-id-12345\",\"timestamp\":\"" + timestamp + "\"" + extraFields + "}";
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
        logger.info("Mock API 2 started on http://localhost:9092");

        // SOAP API 1
        soapApi1 = Service.ignite();
        soapApi1.port(9093);
        soapApi1.before((req, res) -> {
            String auth = req.headers("Authorization");
            if (auth != null)
                logger.info("Received Authorization header on SOAP API 1: {}", auth);
        });
        soapApi1.post("/ws/AccountService", (req, res) -> {
            String soapAction = req.headers("SOAPAction");
            res.header("Content-Type", "text/xml; charset=utf-8");
            if ("\"getAccountDetails\"".equals(soapAction) || "getAccountDetails".equals(soapAction)) {
                String account = extractAccountFromSoap(req.body());
                String timestamp = java.time.Instant.now().toString();
                String extraXml = "<timestamp>" + timestamp + "</timestamp><processId>pid-" + java.util.UUID.randomUUID().toString().substring(0,8) + "</processId><randomId>" + java.util.UUID.randomUUID() + "</randomId>";
                if ("999".equals(account)) {
                    return "<?xml version='1.0' encoding='UTF-8'?>\n<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>"
                            + "<ns2:getAccountDetailsResponse xmlns:ns2=\"http://service.ws.myorg.com/\">"
                            + "<return><status>SUCCESS</status><transactionId>soap-api1-specific-id-for-999</transactionId>" + extraXml + "</return>"
                            + "</ns2:getAccountDetailsResponse></soap:Body></soap:Envelope>";
                } else {
                    return "<?xml version='1.0' encoding='UTF-8'?>\n<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>"
                            + "<ns2:getAccountDetailsResponse xmlns:ns2=\"http://service.ws.myorg.com/\">"
                            + "<return><status>SUCCESS</status><transactionId>shared-soap-id-12345</transactionId>" + extraXml + "</return>"
                            + "</ns2:getAccountDetailsResponse></soap:Body></soap:Envelope>";
                }
            } else if ("\"/Service/orderService.serviceagent/createOrderEndpoint1/Operation\"".equals(soapAction)) {
                return "<?xml version='1.0' encoding='UTF-8'?>\n<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>"
                        + "<ns2:createOrderResponse xmlns:ns2=\"http://service.ws.myorg.com/\">"
                        + "<return><status>ORDER_CREATED</status><orderId>order-9876</orderId></return>"
                        + "</ns2:createOrderResponse></soap:Body></soap:Envelope>";
            } else {
                res.status(400);
                return "Unsupported SOAPAction: " + soapAction;
            }
        });
        soapApi1.awaitInitialization();
        logger.info("Mock SOAP API 1 started on http://localhost:9093");

        // SOAP API 2
        soapApi2 = Service.ignite();
        soapApi2.port(9094);
        soapApi2.before((req, res) -> {
            String auth = req.headers("Authorization");
            if (auth != null)
                logger.info("Received Authorization header on SOAP API 2: {}", auth);
        });
        soapApi2.post("/ws/AccountService", (req, res) -> {
            String soapAction = req.headers("SOAPAction");
            res.header("Content-Type", "text/xml; charset=utf-8");
            if ("\"getAccountDetails\"".equals(soapAction) || "getAccountDetails".equals(soapAction)) {
                String timestamp = java.time.Instant.now().toString();
                String extraXml = "<timestamp>" + timestamp + "</timestamp><processId>pid-" + java.util.UUID.randomUUID().toString().substring(0,8) + "</processId><randomId>" + java.util.UUID.randomUUID() + "</randomId>";
                return "<?xml version='1.0' encoding='UTF-8'?>\n<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>"
                        + "<ns2:getAccountDetailsResponse xmlns:ns2=\"http://service.ws.myorg.com/\">"
                        + "<return><status>SUCCESS</status><transactionId>shared-soap-id-12345</transactionId>" + extraXml + "</return>"
                        + "</ns2:getAccountDetailsResponse></soap:Body></soap:Envelope>";
            } else {
                res.status(400);
                return "Unsupported SOAPAction: " + soapAction;
            }
        });
        soapApi2.awaitInitialization();
        logger.info("Mock SOAP API 2 started on http://localhost:9094");

        isRunning = true;
        logger.info("All mock servers are running.");
    }

    public static synchronized void stop() {
        if (!isRunning) {
            logger.info("Mock servers are not running.");
            return;
        }

        if (api1 != null) { api1.stop(); api1 = null; }
        if (api2 != null) { api2.stop(); api2 = null; }
        if (soapApi1 != null) { soapApi1.stop(); soapApi1 = null; }
        if (soapApi2 != null) { soapApi2.stop(); soapApi2 = null; }

        isRunning = false;
        logger.info("All mock servers have been stopped.");
    }

    public static boolean isRunning() {
        return isRunning;
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