package com.raks.raksanalyzer.api.rest;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import java.util.HashSet;
import java.util.Set;

/**
 * JAX-RS Application configuration.
 * Registers REST resources and providers.
 */
@ApplicationPath("/api")
public class RestApplication extends Application {
    
    @Override
    public Set<Class<?>> getClasses() {
        Set<Class<?>> classes = new HashSet<>();
        
        // Register REST resources
        classes.add(AnalysisResource.class);
        classes.add(ConfigurationResource.class);
        classes.add(UploadResource.class);
        
        // Register Jackson JSON provider
        classes.add(org.glassfish.jersey.jackson.JacksonFeature.class);
        
        // Register MultiPart feature for file uploads
        classes.add(org.glassfish.jersey.media.multipart.MultiPartFeature.class);
        
        return classes;
    }
}
