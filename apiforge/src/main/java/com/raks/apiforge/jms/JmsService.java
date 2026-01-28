package com.raks.apiforge.jms;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JmsService {
    private static final Logger logger = LoggerFactory.getLogger(JmsService.class);
    
    private static JmsService instance;
    private Connection connection;
    private Session session;
    private JmsConnectorFactory.JmsConfig currentConfig;

    private JmsService() {}

    public static synchronized JmsService getInstance() {
        if (instance == null) instance = new JmsService();
        return instance;
    }

    public synchronized void connect(JmsConnectorFactory.JmsConfig config) throws Exception {
        if (connection != null) {
            try { close(); } catch (Exception e) { /* ignore */ }
        }
        
        ConnectionFactory factory = JmsConnectorFactory.createFactory(config);
        connection = factory.createConnection(config.getUsername(), config.getPassword());
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        this.currentConfig = config;
        logger.info("Connected to JMS Provider: " + config.getProvider());
    }
    
    public synchronized void close() {
        try {
            if (session != null) session.close();
            if (connection != null) connection.close();
        } catch (Exception e) {
            logger.warn("Error closing JMS resources", e);
        } finally {
            connection = null;
            session = null;
            currentConfig = null;
        }
    }

    public synchronized List<String> getDestinations() throws Exception {
        ensureConnected();
        List<String> dests = new ArrayList<>();
        if (EmbeddedBrokerService.isRunning()) {
             dests.addAll(EmbeddedBrokerService.getDestinations());
        }
        
        // Fallback or external provider logic
        if (connection instanceof org.apache.activemq.ActiveMQConnection) {
            try {
                org.apache.activemq.ActiveMQConnection amqConn = (org.apache.activemq.ActiveMQConnection) connection;
                org.apache.activemq.advisory.DestinationSource source = amqConn.getDestinationSource();
                source.start(); // Ensure it's started
                if (source != null) {
                    Set<org.apache.activemq.command.ActiveMQQueue> queues = source.getQueues();
                    if (queues != null) {
                        for (org.apache.activemq.command.ActiveMQQueue q : queues) {
                            if (!dests.contains(q.getPhysicalName())) dests.add(q.getPhysicalName());
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to fetch ActiveMQ destinations via Advisory", e);
            }
        }
        
        return dests;
    }

    public synchronized Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("connected", connection != null);
        status.put("isListening", isListening);
        status.put("activeDestination", activeDestination);
        status.put("activeConsumersCount", activeConsumersCount);
        if (currentConfig != null) {
            status.put("provider", currentConfig.getProvider());
            status.put("url", currentConfig.getUrl());
        }
        return status;
    }

    // -- Listener / Consumer Logic --
    
    private List<MessageConsumer> activeConsumers = new ArrayList<>();
    private final List<Map<String, Object>> messageBuffer = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean isListening = false;
    private long totalConsumed = 0;
    private long startTime = 0;
    private String activeDestination;
    private int activeConsumersCount;
    
    public synchronized void startListeners(String countStr, String destination, String type) throws Exception {
        if (isListening) stopListeners();
        
        ensureConnected();
        int count = 1;
        try { count = Integer.parseInt(countStr); } catch (Exception e) {}
        if (count < 1) count = 1;
        
        activeConsumers.clear();
        messageBuffer.clear();
        totalConsumed = 0;
        startTime = System.currentTimeMillis();
        isListening = true;
        this.activeDestination = destination;
        this.activeConsumersCount = count;
        
        Destination dest = createDestination(destination, type);
        
        logger.info("Starting {} concurrent consumers on {}", count, destination);
        
        for (int i = 0; i < count; i++) {
            Session consumerSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageConsumer consumer = consumerSession.createConsumer(dest);
            consumer.setMessageListener(message -> {
                try {
                    Map<String, Object> data = mapMessage(message);
                    synchronized (messageBuffer) {
                        messageBuffer.add(0, data); // Add to top
                        if (messageBuffer.size() > 50) messageBuffer.remove(messageBuffer.size() - 1); // Keep last 50
                    }
                    totalConsumed++;
                } catch (Exception e) {
                    logger.error("Error processing message", e);
                }
            });
            activeConsumers.add(consumer);
        }
    }
    
    public synchronized void stopListeners() {
        isListening = false;
        for (MessageConsumer c : activeConsumers) {
            try { c.close(); } catch (Exception e) {}
        }
        activeConsumers.clear();
        // Don't close session/connection as they are shared
    }
    
    public Map<String, Object> getListenerStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("isListening", isListening);
        stats.put("totalConsumed", totalConsumed);
        
        long outputRate = 0;
        long duration = (System.currentTimeMillis() - startTime) / 1000;
        if (duration > 0) outputRate = totalConsumed / duration;
        
        stats.put("rate", outputRate); // msg/sec
        
        synchronized (messageBuffer) {
            stats.put("buffer", new ArrayList<>(messageBuffer));
            // clear buffer after read to avoid dups in UI? 
            // Better to keep it and let UI handle diffs, or just return snapshot.
            // Let's return snapshot. UI can dedupe or just replace.
        }
        return stats;
    }

    public void sendMessage(String destinationName, String payload, Map<String, Object> properties, String destType) throws Exception {
        ensureConnected();
        Destination dest = createDestination(destinationName, destType);
        MessageProducer prod = session.createProducer(dest);
        
        try {
            TextMessage msg = session.createTextMessage(payload);
            if (properties != null) {
                for (Map.Entry<String, Object> entry : properties.entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();
                    if (key != null && key.startsWith("JMS")) {
                        if ("JMSCorrelationID".equals(key)) msg.setJMSCorrelationID(value != null ? String.valueOf(value) : null);
                        else if ("JMSType".equals(key)) msg.setJMSType(value != null ? String.valueOf(value) : null);
                        // Skip others like JMSExpiration, JMSPriority etc as they are handled by producer
                        continue;
                    }
                    msg.setObjectProperty(key, value);
                }
            }
            prod.send(msg);
        } finally {
            prod.close();
        }
    }
    
    public void sendBatch(String destinationName, List<String> payloads, int msgsPerMinute, String destType) throws Exception {
        ensureConnected();
        Destination dest = createDestination(destinationName, destType);
        MessageProducer prod = session.createProducer(dest);
        
        try {
            long delayMs = 0;
            if (msgsPerMinute > 0) {
                delayMs = 60000 / msgsPerMinute;
            }
            
            for (String payload : payloads) {
                TextMessage msg = session.createTextMessage(payload);
                prod.send(msg);
                if (delayMs > 0) Thread.sleep(delayMs);
            }
        } finally {
            prod.close();
        }
    }

    public List<Map<String, Object>> browse(String queueName) throws Exception {
        ensureConnected();
        Queue queue = session.createQueue(queueName);
        QueueBrowser browser = session.createBrowser(queue);
        Enumeration<?> msgs = browser.getEnumeration();
        
        List<Map<String, Object>> results = new ArrayList<>();
        int limit = 100; // Cap browsing for safety
        while (msgs.hasMoreElements() && results.size() < limit) {
             Message m = (Message) msgs.nextElement();
             results.add(mapMessage(m));
        }
        return results;
    }
    
    public Map<String, Object> receiveOnce(String destName, String destType, int timeoutMs) throws Exception {
        ensureConnected();
        Destination dest = createDestination(destName, destType);
        MessageConsumer consumer = session.createConsumer(dest);
        
        try {
            Message m = consumer.receive(timeoutMs);
            if (m == null) return null;
            return mapMessage(m);
        } finally {
            consumer.close();
        }
    }
    
    public boolean isConnected() {
        return connection != null && session != null;
    }
    
    public int drainQueue(String destName, String destType) throws Exception {
        ensureConnected();
        Destination dest = createDestination(destName, destType);
        MessageConsumer consumer = session.createConsumer(dest);
        int count = 0;
        int limit = 1000; // Safety cap
        
        try {
            while (count < limit) {
                Message m = consumer.receive(500); // 500ms timeout
                if (m == null) break;
                // Just ack (auto-ack is on) and count
                count++;
            }
        } finally {
            consumer.close();
        }
        return count;
    }

    public void createDestinationAdmin(String name, String type) throws Exception {
        ensureConnected();
        // In most JMS implementations, asserting existence is enough.
        // For ActiveMQ, we send a dummy message with a specific property to "init" it if needed,
        // or just create it via session.
        if ("TOPIC".equalsIgnoreCase(type)) {
            session.createTopic(name);
        } else {
            session.createQueue(name);
        }
        logger.info("Admin: Created/Initialized {} {}", type, name);
    }

    public boolean deleteDestination(String name, String type) throws Exception {
        ensureConnected();
        // Destination deletion is provider-specific. 
        // For ActiveMQ, we can try to destroy it.
        if (connection instanceof org.apache.activemq.ActiveMQConnection) {
            org.apache.activemq.ActiveMQConnection amqConn = (org.apache.activemq.ActiveMQConnection) connection;
            if ("TOPIC".equalsIgnoreCase(type)) {
                amqConn.destroyDestination(new org.apache.activemq.command.ActiveMQTopic(name));
            } else {
                amqConn.destroyDestination(new org.apache.activemq.command.ActiveMQQueue(name));
            }
            logger.info("Admin: Deleted {} {}", type, name);
            return true;
        }
        throw new UnsupportedOperationException("Delete operation not supported for this provider.");
    }

    private Destination createDestination(String name, String type) throws JMSException {
        if ("TOPIC".equalsIgnoreCase(type)) return session.createTopic(name);
        return session.createQueue(name);
    }
    
    private void ensureConnected() {
        if (!isConnected()) {
            throw new IllegalStateException("Not Connected to any JMS Provider.");
        }
    }
    
    public Map<String, Object> getDestinationMetadata(String name, String type) throws Exception {
        ensureConnected();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("name", name);
        meta.put("type", type);
        meta.put("status", "Active");
        
        if (connection instanceof org.apache.activemq.ActiveMQConnection) {
            org.apache.activemq.ActiveMQConnection amqConn = (org.apache.activemq.ActiveMQConnection) connection;
            // Note: Full stats usually require JMX, but we can get some basics if available
            // For now, we'll return placeholders that the frontend can handle
            meta.put("provider", "ActiveMQ");
        }
        return meta;
    }

    public List<Map<String, Object>> receiveMultiple(String destName, String destType, int count, int timeoutMs) throws Exception {
        ensureConnected();
        Destination dest = createDestination(destName, destType);
        MessageConsumer consumer = session.createConsumer(dest);
        List<Map<String, Object>> results = new ArrayList<>();
        
        try {
            for (int i = 0; i < count; i++) {
                Message m = consumer.receive(timeoutMs);
                if (m == null) break;
                results.add(mapMessage(m));
            }
            return results;
        } finally {
            consumer.close();
        }
    }

    private Map<String, Object> mapMessage(Message m) throws JMSException {
        Map<String, Object> data = new LinkedHashMap<>(); // Preserve top-level metadata
        data.put("JMSMessageID", m.getJMSMessageID());
        
        // Granular Timestamp: YYYY-MM-DD HH:mm:ss:SSS
        long ts = m.getJMSTimestamp();
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MM/dd/yyyy, hh:mm:ss:SSS a");
        data.put("JMSTimestamp", ts > 0 ? sdf.format(new Date(ts)) : "N/A");
        data.put("JMSTimestampRaw", ts);
        
        // Categorized Properties
        Map<String, Object> systemProps = new LinkedHashMap<>();
        Map<String, Object> customProps = new LinkedHashMap<>();

        // Standard JMS Headers (System)
        try { systemProps.put("JMSDestination", m.getJMSDestination() != null ? m.getJMSDestination().toString() : "N/A"); } catch(Exception e) {}
        try { systemProps.put("JMSDeliveryMode", m.getJMSDeliveryMode() == 1 ? "NON_PERSISTENT" : "PERSISTENT"); } catch(Exception e) {}
        try { systemProps.put("JMSPriority", m.getJMSPriority()); } catch(Exception e) {}
        try { systemProps.put("JMSRedelivered", m.getJMSRedelivered()); } catch(Exception e) {}
        try { systemProps.put("JMSReplyTo", m.getJMSReplyTo() != null ? m.getJMSReplyTo().toString() : "N/A"); } catch(Exception e) {}
        try { systemProps.put("JMSExpiration", m.getJMSExpiration() > 0 ? sdf.format(new Date(m.getJMSExpiration())) : "Never"); } catch(Exception e) {}
        try { systemProps.put("JMSCorrelationID", m.getJMSCorrelationID()); } catch(Exception e) {}
        try { systemProps.put("JMSType", m.getJMSType()); } catch(Exception e) {}

        // Custom/Application Properties
        Enumeration<?> props = m.getPropertyNames();
        while (props.hasMoreElements()) {
            String key = (String) props.nextElement();
            customProps.put(key, m.getObjectProperty(key));
        }

        data.put("systemProperties", systemProps);
        data.put("customProperties", customProps);
        
        if (m instanceof TextMessage) {
            data.put("body", ((TextMessage) m).getText());
        } else {
             data.put("body", m.toString());
        }
        return data;
    }
}
