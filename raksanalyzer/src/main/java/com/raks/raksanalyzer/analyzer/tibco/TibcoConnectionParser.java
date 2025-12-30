package com.raks.raksanalyzer.analyzer.tibco;
import com.raks.raksanalyzer.core.utils.XmlUtils;
import com.raks.raksanalyzer.domain.model.tibco.TibcoConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
public class TibcoConnectionParser {
    private static final Logger logger = LoggerFactory.getLogger(TibcoConnectionParser.class);
    public static TibcoConnection parseJdbcConnection(Path connectionFile) {
        try {
            Document doc = XmlUtils.parseXmlFile(connectionFile);
            TibcoConnection connection = new TibcoConnection();
            connection.setType("JDBC");
            connection.setFilePath(connectionFile.toString());
            connection.setName(connectionFile.getFileName().toString().replace(".sharedjdbc", ""));
            Element root = doc.getDocumentElement();
            Element urlElement = XmlUtils.getFirstChildElement(root, "jdbcUrl");
            if (urlElement != null) {
                connection.setConnectionUrl(XmlUtils.getElementText(urlElement));
            }
            Element userElement = XmlUtils.getFirstChildElement(root, "username");
            if (userElement != null) {
                connection.setUsername(XmlUtils.getElementText(userElement));
            }
            Element minPoolElement = XmlUtils.getFirstChildElement(root, "minPoolSize");
            if (minPoolElement != null) {
                try {
                    connection.setMinPoolSize(Integer.parseInt(XmlUtils.getElementText(minPoolElement)));
                } catch (NumberFormatException e) {
                    logger.warn("Invalid minPoolSize value");
                }
            }
            Element maxPoolElement = XmlUtils.getFirstChildElement(root, "maxPoolSize");
            if (maxPoolElement != null) {
                try {
                    connection.setMaxPoolSize(Integer.parseInt(XmlUtils.getElementText(maxPoolElement)));
                } catch (NumberFormatException e) {
                    logger.warn("Invalid maxPoolSize value");
                }
            }
            logger.debug("Parsed JDBC connection: {}", connection.getName());
            return connection;
        } catch (IOException e) {
            logger.error("Failed to parse JDBC connection: {}", connectionFile, e);
            return null;
        }
    }
    public static TibcoConnection parseJmsConnection(Path connectionFile) {
        try {
            Document doc = XmlUtils.parseXmlFile(connectionFile);
            TibcoConnection connection = new TibcoConnection();
            connection.setType("JMS");
            connection.setFilePath(connectionFile.toString());
            connection.setName(connectionFile.getFileName().toString().replace(".sharedjmscon", ""));
            Element root = doc.getDocumentElement();
            Element urlElement = XmlUtils.getFirstChildElement(root, "serverUrl");
            if (urlElement != null) {
                connection.setConnectionUrl(XmlUtils.getElementText(urlElement));
            }
            Element providerElement = XmlUtils.getFirstChildElement(root, "provider");
            if (providerElement != null) {
                connection.setProvider(XmlUtils.getElementText(providerElement));
            }
            Element userElement = XmlUtils.getFirstChildElement(root, "username");
            if (userElement != null) {
                connection.setUsername(XmlUtils.getElementText(userElement));
            }
            Element sslElement = XmlUtils.getFirstChildElement(root, "sslEnabled");
            if (sslElement != null) {
                connection.setSslEnabled("true".equalsIgnoreCase(XmlUtils.getElementText(sslElement)));
            }
            logger.debug("Parsed JMS connection: {}", connection.getName());
            return connection;
        } catch (IOException e) {
            logger.error("Failed to parse JMS connection: {}", connectionFile, e);
            return null;
        }
    }
    public static TibcoConnection parseHttpConnection(Path connectionFile) {
        try {
            Document doc = XmlUtils.parseXmlFile(connectionFile);
            TibcoConnection connection = new TibcoConnection();
            connection.setType("HTTP");
            connection.setFilePath(connectionFile.toString());
            connection.setName(connectionFile.getFileName().toString().replace(".sharedhttp", ""));
            Element root = doc.getDocumentElement();
            Element hostElement = XmlUtils.getFirstChildElement(root, "host");
            if (hostElement != null) {
                connection.setHost(XmlUtils.getElementText(hostElement));
            }
            Element portElement = XmlUtils.getFirstChildElement(root, "port");
            if (portElement != null) {
                connection.setPort(XmlUtils.getElementText(portElement));
            }
            Element sslElement = XmlUtils.getFirstChildElement(root, "useSSL");
            if (sslElement != null) {
                connection.setSslEnabled("true".equalsIgnoreCase(XmlUtils.getElementText(sslElement)));
            }
            Element authElement = XmlUtils.getFirstChildElement(root, "authenticationType");
            if (authElement != null) {
                connection.setAuthenticationType(XmlUtils.getElementText(authElement));
            }
            logger.debug("Parsed HTTP connection: {}", connection.getName());
            return connection;
        } catch (IOException e) {
            logger.error("Failed to parse HTTP connection: {}", connectionFile, e);
            return null;
        }
    }
    public static TibcoConnection parseMqConnection(Path connectionFile) {
        try {
            Document doc = XmlUtils.parseXmlFile(connectionFile);
            TibcoConnection connection = new TibcoConnection();
            connection.setType("MQ");
            connection.setFilePath(connectionFile.toString());
            connection.setName(connectionFile.getFileName().toString().replace(".mqcon", ""));
            Element root = doc.getDocumentElement();
            Element qmElement = XmlUtils.getFirstChildElement(root, "queueManager");
            if (qmElement != null) {
                connection.setQueueManager(XmlUtils.getElementText(qmElement));
            }
            Element hostElement = XmlUtils.getFirstChildElement(root, "host");
            if (hostElement != null) {
                connection.setHost(XmlUtils.getElementText(hostElement));
            }
            Element portElement = XmlUtils.getFirstChildElement(root, "port");
            if (portElement != null) {
                connection.setPort(XmlUtils.getElementText(portElement));
            }
            Element channelElement = XmlUtils.getFirstChildElement(root, "channel");
            if (channelElement != null) {
                connection.setChannel(XmlUtils.getElementText(channelElement));
            }
            logger.debug("Parsed MQ connection: {}", connection.getName());
            return connection;
        } catch (IOException e) {
            logger.error("Failed to parse MQ connection: {}", connectionFile, e);
            return null;
        }
    }
    public static TibcoConnection parseConnection(Path connectionFile) {
        String fileName = connectionFile.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".sharedjdbc")) {
            return parseJdbcConnection(connectionFile);
        } else if (fileName.endsWith(".sharedjmscon")) {
            return parseJmsConnection(connectionFile);
        } else if (fileName.endsWith(".sharedhttp")) {
            return parseHttpConnection(connectionFile);
        } else if (fileName.endsWith(".mqcon")) {
            return parseMqConnection(connectionFile);
        } else {
            logger.warn("Unknown connection type: {}", fileName);
            return null;
        }
    }
    public static List<TibcoConnection> parseAllConnections(Path projectPath) {
        List<TibcoConnection> connections = new ArrayList<>();
        try {
            List<String> extensions = List.of("sharedjdbc", "sharedjmscon", "sharedhttp", "mqcon");
            for (String ext : extensions) {
                List<Path> files = com.raks.raksanalyzer.core.utils.FileUtils.findFilesByExtension(projectPath, ext);
                for (Path file : files) {
                    TibcoConnection conn = parseConnection(file);
                    if (conn != null) {
                        connections.add(conn);
                    }
                }
            }
            logger.info("Parsed {} Tibco connections", connections.size());
        } catch (IOException e) {
            logger.error("Failed to parse connections", e);
        }
        return connections;
    }
}
