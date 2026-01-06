package com.raks.gitanalyzer.api;

import org.glassfish.jersey.server.ResourceConfig;

public class RestApplication extends ResourceConfig {
    public RestApplication() {
        packages("com.raks.gitanalyzer.api");
        register(org.glassfish.jersey.jackson.JacksonFeature.class);
    }
}
