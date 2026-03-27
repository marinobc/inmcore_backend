package com.inmobiliaria.notification_service.service;

import com.inmobiliaria.notification_service.config.MailPropertiesConfig;
import com.inmobiliaria.notification_service.domain.EmailLogDocument;
import com.inmobiliaria.notification_service.domain.NotificationStatus;
import com.inmobiliaria.notification_service.dto.request.SendCredentialsEmailRequest;
import com.inmobiliaria.notification_service.dto.response.NotificationResponse;
import com.inmobiliaria.notification_service.exception.EmailSendException;
import com.inmobiliaria.notification_service.repository.EmailLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final JavaMailSender mailSender;
    private final MailPropertiesConfig mailPropertiesConfig;
    private final EmailLogRepository emailLogRepository;

    public NotificationResponse sendCredentialsEmail(SendCredentialsEmailRequest request) {
        String subject = "Credenciales temporales de acceso";
        String body = buildCredentialsBody(request.fullName(), request.to(), request.temporaryPassword());

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailPropertiesConfig.getFrom());
            message.setTo(request.to());
            message.setSubject(subject);
            message.setText(body);

            mailSender.send(message);

            emailLogRepository.save(
                    EmailLogDocument.builder()
                            .to(request.to())
                            .subject(subject)
                            .body(body)
                            .status(NotificationStatus.SENT)
                            .errorMessage(null)
                            .createdAt(Instant.now())
                            .build()
            );

            return new NotificationResponse("Email sent successfully", NotificationStatus.SENT);

        } catch (Exception ex) {
            emailLogRepository.save(
                    EmailLogDocument.builder()
                            .to(request.to())
                            .subject(subject)
                            .body(body)
                            .status(NotificationStatus.FAILED)
                            .errorMessage(ex.getMessage())
                            .createdAt(Instant.now())
                            .build()
            );

            throw new EmailSendException("Failed to send email to " + request.to(), ex);
        }
    }

    private String buildCredentialsBody(String fullName, String email, String temporaryPassword) {
        return """
                Hola %s,

                Se ha creado una cuenta para ti en el sistema inmobiliario.

                Credenciales temporales:
                Usuario: %s
                Contraseña temporal: %s

                Esta contraseña vence en 5 minutos y deberás cambiarla en tu primer ingreso.

                Saludos,
                Equipo de soporte
                """.formatted(fullName, email, temporaryPassword);
    }
}