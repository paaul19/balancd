package com.balancdapp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class EmailService {

    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    @Value("${resend.api.key}")
    private String resendApiKey;

    @Value("${resend.from.email}")
    private String fromEmail;

    private final RestTemplate restTemplate = new RestTemplate();

    public void sendVerificationEmail(String toEmail, String verificationLink) throws IOException {
        String subject = "Verifica tu cuenta en balanc*d";
        String htmlContent = loadTemplate("templates/email/verification.html").replace("${verificationLink}", verificationLink);
        send(toEmail, subject, htmlContent);
    }

    public void sendPasswordResetEmail(String toEmail, String resetLink) throws IOException {
        String subject = "Restablece tu contraseña en balanc*d";
        String htmlContent = loadTemplate("templates/email/password-reset.html").replace("${resetLink}", resetLink);
        send(toEmail, subject, htmlContent);
    }

    private void send(String toEmail, String subject, String htmlContent) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(resendApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "from", fromEmail,
                "to", toEmail,
                "subject", subject,
                "html", htmlContent
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        var response = restTemplate.postForEntity(RESEND_API_URL, request, String.class);
        System.out.println("STATUS: " + response.getStatusCode());
        System.out.println("BODY: " + response.getBody());
    }

    private String loadTemplate(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        byte[] bytes = resource.getInputStream().readAllBytes();
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
