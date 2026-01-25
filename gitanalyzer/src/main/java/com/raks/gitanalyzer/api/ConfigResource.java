package com.raks.gitanalyzer.api;

import com.raks.gitanalyzer.core.ConfigManager;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;

@Path("/config")
public class ConfigResource {

    @GET
    @Path("/ui")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUIConfig() {
        try {
            // Fetch all properties starting with "ui."
            Map<String, Object> responseData = new java.util.HashMap<>(ConfigManager.getPropertiesByPrefix("ui."));
            responseData.put("env.isCloudHub", ConfigManager.isCloudHub());
            return Response.ok(responseData).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity("{\"error\":\"" + e.getMessage() + "\"}").build();
        }
    }
}
