package com.restaurantplanner.notification.service;

import com.restaurantplanner.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final AuditService auditService;

    public EmailService(JavaMailSender mailSender, AuditService auditService) {
        this.mailSender = mailSender;
        this.auditService = auditService;
    }

    @Async
    public void send(String to, String subject, String text, Long restaurantId, Long reservationId) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            auditService.record(restaurantId, "Email", reservationId, "email.sent", null,
                "{\"to\":\"" + to + "\",\"subject\":\"" + subject + "\"}");
            log.info("Email sent to {}: {}", to, subject);
        } catch (MailException e) {
            log.warn("Failed to send email to {}: {}", to, e.getMessage());
            auditService.record(restaurantId, "Email", reservationId, "email.failed", null,
                "{\"to\":\"" + to + "\",\"error\":\"" + e.getMessage() + "\"}");
        }
    }
}
