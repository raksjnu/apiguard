package com.raks.apiforge.jms;

import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.region.RegionBroker;
import org.apache.activemq.command.ActiveMQDestination;
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

    public static java.util.List<String> getDestinations() {
        java.util.List<String> dests = new java.util.ArrayList<>();
        if (broker != null && isRunning) {
             try {
                 // ActiveMQ 5.x: broker.getRegionBroker() returns RegionBroker interface/class
                 RegionBroker regionBroker = (RegionBroker) broker.getRegionBroker();
                 java.util.Set<ActiveMQDestination> keys = regionBroker.getDestinationMap().keySet();
                 logger.info("EmbeddedBroker: Destination Map Size: " + keys.size());
                 
                 for (ActiveMQDestination d : keys) {
                     logger.info("EmbeddedBroker: Found destination: " + d.getPhysicalName() + ", isTemp: " + d.isTemporary() + ", isAdvisory: " + d.getPhysicalName().startsWith("ActiveMQ.Advisory"));
                     // Filter out Advisory topics and temporary destinations
                     if (!d.getPhysicalName().startsWith("ActiveMQ.Advisory") && !d.isTemporary()) {
                         dests.add(d.getPhysicalName());
                     }
                 }
             } catch (Exception e) {
                 logger.error("Failed to fetch embedded destinations", e);
             }
        }
        return dests;
    }
}
