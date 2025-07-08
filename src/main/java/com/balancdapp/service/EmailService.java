package com.balancdapp.service;

import com.sendgrid.*;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@Service
public class EmailService {

    @Value("${sendgrid.api.key}")
    private String sendgridApiKey;

    @Value("${sendgrid.from.email}")
    private String fromEmail;

    public void sendVerificationEmail(String toEmail, String verificationLink) throws IOException {
        Email from = new Email(fromEmail);
        String subject = "Verifica tu cuenta en TuApp";
        Email to = new Email(toEmail);
        String htmlContent = loadTemplate("templates/email/verification.html").replace("${verificationLink}", verificationLink);
        Content content = new Content("text/html", htmlContent);
        Mail mail = new Mail(from, subject, to, content);

        SendGrid sg = new SendGrid(sendgridApiKey);
        Request request = new Request();

        request.setMethod(Method.POST);
        request.setEndpoint("mail/send");
        request.setBody(mail.build());

        Response response = sg.api(request);
        System.out.println("STATUS: " + response.getStatusCode());
        System.out.println("BODY: " + response.getBody());
        System.out.println("HEADERS: " + response.getHeaders());
    }

    public void sendPasswordResetEmail(String toEmail, String resetLink) throws IOException {
        Email from = new Email(fromEmail);
        String subject = "Restablece tu contraseña en Balancd";
        Email to = new Email(toEmail);
        String htmlContent = loadTemplate("templates/email/password-reset.html").replace("${resetLink}", resetLink);
        Content content = new Content("text/html", htmlContent);
        Mail mail = new Mail(from, subject, to, content);
        SendGrid sg = new SendGrid(sendgridApiKey);
        Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint("mail/send");
        request.setBody(mail.build());
        Response response = sg.api(request);
        System.out.println("STATUS: " + response.getStatusCode());
        System.out.println("BODY: " + response.getBody());
        System.out.println("HEADERS: " + response.getHeaders());
    }

    private String loadTemplate(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        byte[] bytes = Files.readAllBytes(resource.getFile().toPath());
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
