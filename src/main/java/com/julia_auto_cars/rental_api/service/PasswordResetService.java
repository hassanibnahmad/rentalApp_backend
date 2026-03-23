package com.julia_auto_cars.rental_api.service;

import com.julia_auto_cars.rental_api.model.PasswordResetToken;
import com.julia_auto_cars.rental_api.model.UserAccount;
import com.julia_auto_cars.rental_api.repository.PasswordResetTokenRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordResetService.class);

    private final PasswordResetTokenRepository tokenRepository;
    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String resetBaseUrl;
    private final Duration tokenTtl;

    public PasswordResetService(PasswordResetTokenRepository tokenRepository,
                                JavaMailSender mailSender,
                                @Value("${app.mail.from}") String fromAddress,
                                @Value("${app.mail.reset-base-url}") String resetBaseUrl,
                                @Value("${app.mail.reset-token-ttl-minutes:30}") long tokenTtlMinutes) {
        this.tokenRepository = tokenRepository;
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.resetBaseUrl = resetBaseUrl;
        this.tokenTtl = Duration.ofMinutes(tokenTtlMinutes);
    }

    @Transactional
    public void sendResetLink(UserAccount account) {
        tokenRepository.deleteByExpiresAtBefore(Instant.now());
        if (account.getId() != null) {
            tokenRepository.deleteByUser_Id(account.getId());
        }
        PasswordResetToken token = new PasswordResetToken();
        token.setToken(UUID.randomUUID().toString());
        token.setUser(account);
        token.setExpiresAt(Instant.now().plus(tokenTtl));
        tokenRepository.save(token);

        SimpleMailMessage message = buildMessage(account, token.getToken());
        try {
            mailSender.send(message);
            LOGGER.info("Password reset email sent to {}", account.getEmail());
        } catch (MailException mailException) {
            LOGGER.error("Failed to send password reset email to {}", account.getEmail(), mailException);
            throw new IllegalStateException("Impossible d'envoyer l'email de réinitialisation pour le moment.", mailException);
        }
    }

    @Transactional(readOnly = true)
    public PasswordResetToken requireValidToken(String rawToken) {
        PasswordResetToken token = tokenRepository.findByToken(rawToken)
                .orElseThrow(() -> new IllegalArgumentException("Lien de réinitialisation invalide."));
        if (token.getUsedAt() != null) {
            throw new IllegalArgumentException("Lien déjà utilisé.");
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Lien expiré. Veuillez refaire une demande.");
        }
        return token;
    }

    @Transactional
    public void markTokenUsed(PasswordResetToken token) {
        token.setUsedAt(Instant.now());
        tokenRepository.save(token);
    }

    private SimpleMailMessage buildMessage(UserAccount account, String token) {
        String resetLink = resetBaseUrl + (resetBaseUrl.contains("?") ? "&" : "?") + "token=" + token;
        String recipientName = (account.getFullName() != null && !account.getFullName().isBlank())
                ? account.getFullName()
                : "membre Julia Auto Cars";
        String body = String.format(
                """
Bonjour %s,

Nous avons reçu une demande de réinitialisation de mot de passe pour votre compte Julia Auto Cars.

Cliquez sur le lien suivant pour définir un nouveau mot de passe (valide %d minutes) :
%s

Si vous n'êtes pas à l'origine de cette demande, ignorez ce message.

— Julia Auto Cars
""",
                recipientName,
                tokenTtl.toMinutes(),
                resetLink);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(account.getEmail());
        message.setSubject("Réinitialisation de votre mot de passe");
        message.setText(body);
        return message;
    }
}
