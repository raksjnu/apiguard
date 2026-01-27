package com.raks.apiforge.jms;

import org.apache.activemq.broker.BrokerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmbeddedBrokerService {
    private static final Logger logger = LoggerFactory.getLogger(EmbeddedBrokerService.class);
    private static BrokerService broker;
    private static boolean isRunning = false;

    public static synchronized boolean startBroker() {
        if (isRunning) return true;
        try {
            broker = new BrokerService();
            broker.setPersistent(false); // In-memory for testing
            broker.setUseJmx(false);
            broker.addConnector("tcp://localhost:61616");
            broker.start();
            isRunning = true;
            logger.info("Embedded ActiveMQ Broker started at tcp://localhost:61616");
            return true;
        } catch (Exception e) {
            logger.error("Failed to start Embedded Broker", e);
            return false;
        }
    }

    public static synchronized boolean stopBroker() {
        if (!isRunning) return true;
        try {
            if (broker != null) {
                broker.stop();
                broker = null;
            }
            isRunning = false;
            logger.info("Embedded ActiveMQ Broker stopped");
            return true;
        } catch (Exception e) {
            logger.error("Failed to stop Embedded Broker", e);
            return false;
        }
    }

    public static boolean isRunning() {
        return isRunning;
    }
}
