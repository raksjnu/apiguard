package com.raks.raksanalyzer.service;
import com.raks.raksanalyzer.core.config.ConfigurationManager;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
public class EmailService {
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    private final ConfigurationManager config;
    public EmailService() {
        this.config = ConfigurationManager.getInstance();
    }
    public boolean sendDocuments(String toEmail, String projectName, List<String> documentPaths) {
        logger.info("=== EMAIL SERVICE CALLED ===");
        logger.info("Recipient: {}", toEmail);
        logger.info("Project: {}", projectName);
        logger.info("Document paths: {}", documentPaths);
        boolean emailEnabled = Boolean.parseBoolean(config.getProperty("email.enabled", "true"));
        if (!emailEnabled) {
            logger.info("Email is disabled in configuration");
            return false;
        }
        try {
            logger.info("Creating ZIP file with documents...");
            Path zipPath = createDocumentsZip(projectName, documentPaths);
            logger.info("Sending email with ZIP attachment...");
            sendEmailWithAttachment(toEmail, projectName, zipPath);
            Files.deleteIfExists(zipPath);
            logger.info("Temporary ZIP file deleted: {}", zipPath);
            logger.info("✅ Email sent successfully to: {}", toEmail);
            return true;
        } catch (Exception e) {
            logger.error("❌ Failed to send email to: {}", toEmail, e);
            return false;
        }
    }
    private Path createDocumentsZip(String projectName, List<String> documentPaths) throws IOException {
        String timestamp = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String zipFileName = projectName + "_Analysis_Document_" + timestamp + ".zip";
        Path outputDir = Paths.get(config.getProperty("framework.output.directory", "./output"));
        Path zipPath = outputDir.resolve(zipFileName);
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            for (String docPath : documentPaths) {
                if (docPath == null || docPath.isEmpty()) continue;
                Path filePath = Paths.get(docPath);
                if (!Files.exists(filePath)) {
                    logger.warn("Document not found: {}", docPath);
                    continue;
                }
                ZipEntry zipEntry = new ZipEntry(filePath.getFileName().toString());
                zos.putNextEntry(zipEntry);
                Files.copy(filePath, zos);
                zos.closeEntry();
                logger.debug("Added to ZIP: {}", filePath.getFileName());
            }
        }
        logger.info("Created ZIP file: {}", zipPath);
        return zipPath;
    }
    private void sendEmailWithAttachment(String toEmail, String projectName, Path zipPath) throws MessagingException, IOException {
        Properties props = new Properties();
        props.put("mail.smtp.host", config.getProperty("email.smtp.host", "smtp.gmail.com"));
        props.put("mail.smtp.port", config.getProperty("email.smtp.port", "587"));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", config.getProperty("email.smtp.tls.enabled", "true"));
        props.put("mail.smtp.connectiontimeout", config.getProperty("email.smtp.connection.timeout", "30000"));
        props.put("mail.smtp.timeout", config.getProperty("email.smtp.timeout", "60000"));
        String username = config.getProperty("email.smtp.user");
        String password = config.getProperty("email.smtp.password");
        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(
            config.getProperty("email.from.address", username),
            config.getProperty("email.from.name", "RaksAnalyzer")
        ));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
        String subject = config.getProperty("email.subject", "RaksAnalyzer - Analysis Documents");
        message.setSubject(subject);
        Multipart multipart = new MimeMultipart();
        MimeBodyPart textPart = new MimeBodyPart();
        String timestamp = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String htmlBody = "<!DOCTYPE html>" +
            "<html><head><style>" +
            "body { font-family: Arial, sans-serif; margin: 0; padding: 0; background: #f5f5f5; }" +
            ".container { max-width: 700px; margin: 20px auto; background: white; }" +
            ".header { background: #663399; color: white; padding: 25px; text-align: center; }" +
            ".section { margin: 0; padding: 20px; border-bottom: 1px solid #e0e0e0; }" +
            ".footer { background: #f9f9f9; padding: 15px; text-align: center; font-size: 12px; color: #666; }" +
            "</style></head><body>" +
            "<div class='container'>" +
            "<div class='header'>" +
            "<h2 style='margin: 0;'>📊 RaksAnalyzer</h2>" +
            "<p style='margin: 5px 0 0 0; font-size: 14px;'>" + config.getProperty("email.header.subtitle", "Analysis Documents") + "</p>" +
            "</div>" +
            "<div class='section'>" +
            "<h3 style='color: #663399; margin-top: 0;'>📦 Project Analysis Complete</h3>" +
            "<p>Your analysis for project <strong>" + projectName + "</strong> has been completed successfully.</p>" +
            "<p>The generated documents are attached to this email as a ZIP file.</p>" +
            "</div>" +
            "<div class='section'>" +
            "<h3 style='color: #663399; margin-top: 0;'>📎 Attachment Details</h3>" +
            "<p><strong>File:</strong> " + zipPath.getFileName() + "</p>" +
            "<p><strong>Contains:</strong> Analysis documents (Excel, Word, PDF)</p>" +
            "</div>" +
            "<div class='section'>" +
            "<h3 style='color: #663399; margin-top: 0;'>ℹ️ Analysis Information</h3>" +
            "<p><strong>Project:</strong> " + projectName + "</p>" +
            "<p><strong>Generated:</strong> " + timestamp + "</p>" +
            "<p><strong>Tool:</strong> RaksAnalyzer v1.0.0</p>" +
            "</div>" +
            "<div class='footer'>" +
            "<p style='margin: 0;'>" + config.getProperty("email.footer.text", "This is an automated notification from RaksAnalyzer") + "</p>" +
            "<p style='margin: 5px 0 0 0;'>" + config.getProperty("email.footer.copyright", "© 2025 RAKS - Universal Document Generation Framework") + "</p>" +
            "</div></div></body></html>";
        textPart.setContent(htmlBody, "text/html; charset=utf-8");
        multipart.addBodyPart(textPart);
        logger.info("Email body created for project: {}", projectName);
        MimeBodyPart attachmentPart = new MimeBodyPart();
        attachmentPart.attachFile(zipPath.toFile());
        multipart.addBodyPart(attachmentPart);
        logger.info("ZIP attachment added: {} ({} bytes)", zipPath.getFileName(), Files.size(zipPath));
        message.setContent(multipart);
        logger.info("Sending email to: {} with attachment: {}", toEmail, zipPath.getFileName());
        Transport.send(message);
        logger.info("✅ EMAIL SENT SUCCESSFULLY to: {}, Attachment: {}, Size: {} bytes", 
            toEmail, zipPath.getFileName(), Files.size(zipPath));
    }
}
