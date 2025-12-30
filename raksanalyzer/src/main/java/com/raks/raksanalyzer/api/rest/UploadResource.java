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
@Path("/upload")
public class UploadResource {
    private static final Logger logger = LoggerFactory.getLogger(UploadResource.class);
    private static final long MAX_FILE_SIZE = 500 * 1024 * 1024; 
    @POST
    @Path("/zip")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadZip(
            @FormDataParam("file") InputStream fileInputStream,
            @FormDataParam("file") FormDataContentDisposition fileDetail) {
        return handleUpload(fileInputStream, fileDetail, "zip");
    }
    @POST
    @Path("/jar")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadJar(
            @FormDataParam("file") InputStream fileInputStream,
            @FormDataParam("file") FormDataContentDisposition fileDetail) {
        return handleUpload(fileInputStream, fileDetail, "jar");
    }
    @POST
    @Path("/ear")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadEar(
            @FormDataParam("file") InputStream fileInputStream,
            @FormDataParam("file") FormDataContentDisposition fileDetail) {
        return handleUpload(fileInputStream, fileDetail, "ear");
    }
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
            String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
            if (!extension.equals("xml") && !extension.equals("properties") && !extension.equals("yaml")) {
                 return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Invalid config file type. Expected .xml, .properties or .yaml"))
                    .build();
            }
            uploadId = UUID.randomUUID().toString();
            tempFile = saveTempFile(fileInputStream, uploadId, fileName);
            long fileSize = java.nio.file.Files.size(tempFile);
            if (fileSize > 10 * 1024 * 1024) { 
                 java.nio.file.Files.delete(tempFile);
                 return Response.status(413)
                    .entity(Map.of("error", "Config file size exceeds 10MB limit"))
                    .build();
            }
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
    private Response handleUpload(InputStream fileInputStream, 
                                  FormDataContentDisposition fileDetail,
                                  String fileType) {
        String fileName = null;
        String uploadId = null;
        java.nio.file.Path tempFile = null;
        try {
            fileName = fileDetail.getFileName();
            logger.info("Received {} upload: {}", fileType.toUpperCase(), fileName);
            String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
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
            uploadId = UUID.randomUUID().toString();
            tempFile = saveTempFile(fileInputStream, uploadId, fileName);
            long fileSize = java.nio.file.Files.size(tempFile);
            logger.info("File size: {} bytes ({} MB)", fileSize, fileSize / (1024 * 1024));
            if (fileSize > MAX_FILE_SIZE) {
                java.nio.file.Files.delete(tempFile);
                return Response.status(413) 
                    .entity(Map.of("error", "File size exceeds 500MB limit"))
                    .build();
            }
            if (!FileExtractionUtil.isValidArchive(tempFile)) {
                java.nio.file.Files.delete(tempFile);
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Invalid or corrupted " + fileType.toUpperCase() + " file"))
                    .build();
            }
            java.nio.file.Path projectPath;
            if ("jar".equals(fileType)) {
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
            java.nio.file.Files.delete(tempFile);
            tempFile = null; 
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
            if (uploadId != null) {
                FileExtractionUtil.cleanupTempDirectory(uploadId);
            }
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", "Unexpected error: " + e.getMessage()))
                .build();
        }
    }
    private java.nio.file.Path saveTempFile(InputStream inputStream, String uploadId, String fileName) 
            throws java.io.IOException {
        java.nio.file.Path uploadDir = java.nio.file.Paths.get(System.getProperty("user.dir"), "temp", "uploads", uploadId);
        java.nio.file.Files.createDirectories(uploadDir);
        java.nio.file.Path tempFile = uploadDir.resolve(fileName);
        java.nio.file.Files.copy(inputStream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        logger.debug("Saved temp file: {}", tempFile);
        return tempFile;
    }
}
