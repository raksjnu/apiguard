package com.raks.apiforge.jms;

import javax.jms.ConnectionFactory;
import org.apache.activemq.ActiveMQConnectionFactory;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.lang.reflect.Method;

public class JmsConnectorFactory {

    public static ConnectionFactory createFactory(JmsConfig config) throws Exception {
        String provider = config.getProvider();
        
        if ("ActiveMQ".equalsIgnoreCase(provider)) {
            return new ActiveMQConnectionFactory(config.getUsername(), config.getPassword(), config.getUrl());
        } 
        else if ("IBM MQ".equalsIgnoreCase(provider) || "Custom".equalsIgnoreCase(provider)) {
            return createCustomFactory(config);
        }
        
        throw new IllegalArgumentException("Unsupported Provider: " + provider);
    }

    private static ConnectionFactory createCustomFactory(JmsConfig config) throws Exception {
        if (config.getDriverJarPath() == null || config.getFactoryClass() == null) {
            throw new IllegalArgumentException("Driver JAR and Factory Class are required for Custom/IBM MQ providers.");
        }

        File jarFile = new File(config.getDriverJarPath());
        if (!jarFile.exists()) {
            throw new IllegalArgumentException("Driver JAR not found at: " + config.getDriverJarPath());
        }

        URL[] urls = { jarFile.toURI().toURL() };
        URLClassLoader loader = new URLClassLoader(urls, JmsConnectorFactory.class.getClassLoader());
        
        Class<?> clazz = Class.forName(config.getFactoryClass(), true, loader);
        Object factoryObj = clazz.getDeclaredConstructor().newInstance();

        // Reflection to set properties
        tryInvoke(factoryObj, "setBrokerURL", config.getUrl()); 
        tryInvoke(factoryObj, "setProviderUrl", config.getUrl()); // Provider specific
        trySetIntProperty(factoryObj, "XMSC_WMQ_CONNECTION_MODE", 1); // IBM MQ Client mode (1)

        return (ConnectionFactory) factoryObj;
    }

    private static void tryInvoke(Object target, String methodName, String value) {
        try {
            Method m = target.getClass().getMethod(methodName, String.class);
            m.invoke(target, value);
        } catch (Exception e) { /* ignore */ }
    }

    private static void trySetIntProperty(Object target, String key, int value) {
        try {
            Method m = target.getClass().getMethod("setIntProperty", String.class, int.class);
            m.invoke(target, key, value);
        } catch (Exception e) { /* ignore */ }
    }
    
    // Config DTO
    public static class JmsConfig {
        private String provider;
        private String url;
        private String username;
        private String password;
        private String driverJarPath;
        private String factoryClass;

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getDriverJarPath() { return driverJarPath; }
        public void setDriverJarPath(String driverJarPath) { this.driverJarPath = driverJarPath; }
        public String getFactoryClass() { return factoryClass; }
        public void setFactoryClass(String factoryClass) { this.factoryClass = factoryClass; }
    }
}
