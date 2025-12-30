package com.raks.raksanalyzer.api.rest;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import java.util.HashSet;
import java.util.Set;
@ApplicationPath("/api")
public class RestApplication extends Application {
    @Override
    public Set<Class<?>> getClasses() {
        Set<Class<?>> classes = new HashSet<>();
        classes.add(AnalysisResource.class);
        classes.add(ConfigurationResource.class);
        classes.add(UploadResource.class);
        classes.add(SampleResource.class);
        classes.add(org.glassfish.jersey.jackson.JacksonFeature.class);
        classes.add(org.glassfish.jersey.media.multipart.MultiPartFeature.class);
        return classes;
    }
}
