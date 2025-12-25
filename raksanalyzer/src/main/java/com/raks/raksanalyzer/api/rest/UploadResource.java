package com.raks.raksanalyzer.api.rest;

import com.raks.raksanalyzer.util.FileExtractionUtil;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST API endpoint for file uploads (ZIP, JAR, and EAR files).
 * 
 * Endpoints:
 * - POST /api/upload/zip - Upload ZIP file containing source code
 * - POST /api/upload/jar - Upload JAR/EAR file
 * - POST /api/upload/ear - Upload TIBCO EAR file
 */
@Path("/upload")
public class UploadResource {
    private static final Logger logger = LoggerFactory.getLogger(UploadResource.class);
    private static final long MAX_FILE_SIZE = 500 * 1024 * 1024; // 500MB
    
    /**
     * Upload ZIP file containing Mule source code.
     * 
     * POST /api/upload/zip
     * Content-Type: multipart/form-data
     * 
     * @param fileInputStream Input stream of uploaded file
     * @param fileDetail File metadata
     * @return JSON response with upload status and temp path
     */
    @POST
    @Path("/zip")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadZip(
            @FormDataParam("file") InputStream fileInputStream,
            @FormDataParam("file") FormDataContentDisposition fileDetail) {
        
        return handleUpload(fileInputStream, fileDetail, "zip");
    }
    
    /**
     * Upload JAR file exported from Mule Studio.
     * 
     * POST /api/upload/jar
     * Content-Type: multipart/form-data
     * 
     * @param fileInputStream Input stream of uploaded file
     * @param fileDetail File metadata
     * @return JSON response with upload status and temp path
     */
    @POST
    @Path("/jar")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadJar(
            @FormDataParam("file") InputStream fileInputStream,
            @FormDataParam("file") FormDataContentDisposition fileDetail) {
        
        return handleUpload(fileInputStream, fileDetail, "jar");
    }
    
    /**
     * Upload TIBCO EAR file.
     * 
     * POST /api/upload/ear
     * Content-Type: multipart/form-data
     * 
     * @param fileInputStream Input stream of uploaded file
     * @param fileDetail File metadata
     * @return JSON response with upload status and temp path
     */
    @POST
    @Path("/ear")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadEar(
            @FormDataParam("file") InputStream fileInputStream,
            @FormDataParam("file") FormDataContentDisposition fileDetail) {
        
        return handleUpload(fileInputStream, fileDetail, "ear");
    }
    
    /**
     * Upload configuration file (Validation configuration or Environment properties).
     * 
     * POST /api/upload/config
     * Content-Type: multipart/form-data
     * 
     * @param fileInputStream Input stream of uploaded file
     * @param fileDetail File metadata
     * @return JSON response with upload status and file path
     */
    @POST
    @Path("/config")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadConfig(
            @FormDataParam("file") InputStream fileInputStream,
            @FormDataParam("file") FormDataContentDisposition fileDetail) {
        
        String fileName = null;
        String uploadId = null;
        java.nio.file.Path tempFile = null;
        
        try {
            fileName = fileDetail.getFileName();
            logger.info("Received CONFIG upload: {}", fileName);
            
            // Validate file extension (xml, properties, yaml)
            String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
            if (!extension.equals("xml") && !extension.equals("properties") && !extension.equals("yaml")) {
                 return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Invalid config file type. Expected .xml, .properties or .yaml"))
                    .build();
            }
            
            // Generate unique upload ID
            uploadId = UUID.randomUUID().toString();
            
            // Save uploaded file temporarily
            tempFile = saveTempFile(fileInputStream, uploadId, fileName);
            
            // Validate file size (10MB limit for config)
            long fileSize = java.nio.file.Files.size(tempFile);
            if (fileSize > 10 * 1024 * 1024) { 
                 java.nio.file.Files.delete(tempFile);
                 return Response.status(413)
                    .entity(Map.of("error", "Config file size exceeds 10MB limit"))
                    .build();
            }
            
            // Return success response
            Map<String, Object> response = new HashMap<>();
            response.put("status", "SUCCESS");
            response.put("uploadId", uploadId);
            response.put("tempPath", tempFile.toString());
            response.put("fileName", fileName);
            response.put("fileType", "config");
            
            logger.info("Config file uploaded successfully to: {}", tempFile);
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            logger.error("Upload failed for config file: {}", fileName, e);
            if (uploadId != null) {
                FileExtractionUtil.cleanupTempDirectory(uploadId);
            }
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", "Upload failed: " + e.getMessage()))
                .build();
        }
    }
    
    /**
     * Common upload handler for both ZIP and JAR files.
     * 
     * @param fileInputStream Input stream of uploaded file
     * @param fileDetail File metadata
     * @param fileType Type of file ("zip" or "jar")
     * @return JSON response with upload status
     */
    private Response handleUpload(InputStream fileInputStream, 
                                  FormDataContentDisposition fileDetail,
                                  String fileType) {
        String fileName = null;
        String uploadId = null;
        java.nio.file.Path tempFile = null;
        
        try {
            fileName = fileDetail.getFileName();
            logger.info("Received {} upload: {}", fileType.toUpperCase(), fileName);
            
            // Validate file extension
            String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
            
            // For JAR endpoint, accept both .jar and .ear files
            if ("jar".equals(fileType)) {
                if (!extension.equals("jar") && !extension.equals("ear")) {
                    return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Invalid file type. Expected .jar or .ear file"))
                        .build();
                }
            } else if (!extension.equals(fileType)) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Invalid file type. Expected ." + fileType + " file"))
                    .build();
            }
            
            // Generate unique upload ID
            uploadId = UUID.randomUUID().toString();
            
            // Save uploaded file temporarily
            tempFile = saveTempFile(fileInputStream, uploadId, fileName);
            
            // Validate file size
            long fileSize = java.nio.file.Files.size(tempFile);
            logger.info("File size: {} bytes ({} MB)", fileSize, fileSize / (1024 * 1024));
            
            if (fileSize > MAX_FILE_SIZE) {
                java.nio.file.Files.delete(tempFile);
                return Response.status(413) // PAYLOAD_TOO_LARGE
                    .entity(Map.of("error", "File size exceeds 500MB limit"))
                    .build();
            }
            
            // Validate that file is a valid archive
            if (!FileExtractionUtil.isValidArchive(tempFile)) {
                java.nio.file.Files.delete(tempFile);
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Invalid or corrupted " + fileType.toUpperCase() + " file"))
                    .build();
            }
            
            // Extract based on type
            java.nio.file.Path projectPath;
            if ("jar".equals(fileType)) {
                // Check if it's actually an EAR file
                if (extension.equals("ear")) {
                    projectPath = FileExtractionUtil.extractEar(tempFile, uploadId);
                } else {
                    projectPath = FileExtractionUtil.extractJar(tempFile, uploadId);
                }
            } else if ("ear".equals(fileType)) {
                projectPath = FileExtractionUtil.extractEar(tempFile, uploadId);
            } else {
                projectPath = FileExtractionUtil.extractZip(tempFile, uploadId);
            }
            
            // Delete the temp archive file (keep extracted content)
            java.nio.file.Files.delete(tempFile);
            tempFile = null; // Mark as deleted
            
            // Return success response
            Map<String, Object> response = new HashMap<>();
            response.put("status", "SUCCESS");
            response.put("uploadId", uploadId);
            response.put("tempPath", projectPath.toString());
            response.put("fileName", fileName);
            response.put("fileType", fileType);
            
            logger.info("File extracted successfully to: {}", projectPath);
            
            return Response.ok(response).build();
            
        } catch (java.io.IOException e) {
            logger.error("Upload failed for file: {}", fileName, e);
            
            // Cleanup on error
            if (tempFile != null && java.nio.file.Files.exists(tempFile)) {
                try {
                    java.nio.file.Files.delete(tempFile);
                } catch (java.io.IOException cleanupError) {
                    logger.warn("Failed to cleanup temp file: {}", tempFile, cleanupError);
                }
            }
            if (uploadId != null) {
                FileExtractionUtil.cleanupTempDirectory(uploadId);
            }
            
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", "Upload failed: " + e.getMessage()))
                .build();
                
        } catch (Exception e) {
            logger.error("Unexpected error during upload", e);
            
            // Cleanup on error
            if (uploadId != null) {
                FileExtractionUtil.cleanupTempDirectory(uploadId);
            }
            
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", "Unexpected error: " + e.getMessage()))
                .build();
        }
    }
    
    /**
     * Save uploaded file to temporary location.
     * 
     * @param inputStream Input stream of uploaded file
     * @param uploadId Unique upload identifier
     * @param fileName Original filename
     * @return Path to saved temp file
     * @throws java.io.IOException if save fails
     */
    private java.nio.file.Path saveTempFile(InputStream inputStream, String uploadId, String fileName) 
            throws java.io.IOException {
        // Use temp folder relative to working directory (wrapper-compatible)
        java.nio.file.Path uploadDir = java.nio.file.Paths.get(System.getProperty("user.dir"), "temp", "uploads", uploadId);
        java.nio.file.Files.createDirectories(uploadDir);
        
        java.nio.file.Path tempFile = uploadDir.resolve(fileName);
        java.nio.file.Files.copy(inputStream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        
        logger.debug("Saved temp file: {}", tempFile);
        return tempFile;
    }
}
