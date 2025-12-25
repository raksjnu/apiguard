package com.raks.raksanalyzer.api.rest;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST endpoint for downloading sample project files.
 * Files are served from the classpath resources (embedded in JAR).
 */
@Path("/samples")
public class SampleResource {
    private static final Logger logger = LoggerFactory.getLogger(SampleResource.class);
    
    // Whitelist of allowed files to download
    private static final List<String> ALLOWED_FILES = Arrays.asList(
        "muleapp1.jar",
        "muleapp1.zip",
        "muleapp1.properties",
        "TibcoApp1.zip",
        "TibcoApp1.ear",
        "TIbcoApp1.xml" 
    );

    @GET
    @Path("/{fileName}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response downloadSample(@PathParam("fileName") String fileName) {
        // Security check
        if (!ALLOWED_FILES.contains(fileName)) {
             return Response.status(Response.Status.NOT_FOUND).entity("Sample file not found: " + fileName).build();
        }

        // Load from classpath resources
        String resourcePath = "samples/" + fileName;
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath);
        
        if (inputStream == null) {
            logger.error("Sample file not found in resources: {}", resourcePath);
            return Response.status(Response.Status.NOT_FOUND).entity("Sample resource missing: " + fileName).build();
        }

        StreamingOutput fileStream = output -> {
            try (InputStream is = inputStream) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
                output.flush();
            } catch (IOException e) {
                logger.error("Failed to stream file", e);
                // Don't throw WebApplicationException inside lambda
            }
        };

        return Response.ok(fileStream, MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
                .build();
    }
}
