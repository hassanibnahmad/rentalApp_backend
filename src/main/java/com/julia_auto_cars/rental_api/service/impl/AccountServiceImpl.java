package com.julia_auto_cars.rental_api.service.impl;

import com.julia_auto_cars.rental_api.dto.ChangeEmailRequest;
import com.julia_auto_cars.rental_api.dto.ChangePasswordRequest;
import com.julia_auto_cars.rental_api.model.UserAccount;
import com.julia_auto_cars.rental_api.repository.UserAccountRepository;
import com.julia_auto_cars.rental_api.service.AccountService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AccountServiceImpl implements AccountService {

    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;

    public AccountServiceImpl(UserAccountRepository repository, PasswordEncoder encoder) {
        this.repository = repository;
        this.passwordEncoder = encoder;
    }

    @Override
    public void changeEmail(ChangeEmailRequest request) {
        UserAccount account = repository.findByEmailIgnoreCase(normalizeEmail(request.currentEmail()))
                .orElseThrow(() -> new EntityNotFoundException("Utilisateur introuvable"));
        if (!passwordEncoder.matches(request.currentPassword(), account.getPassword())) {
            throw new IllegalArgumentException("Mot de passe actuel invalide");
        }
        String nextEmail = normalizeEmail(request.newEmail());
        if (!nextEmail.equals(account.getEmail().toLowerCase()) && repository.existsByEmailIgnoreCase(nextEmail)) {
            throw new IllegalArgumentException("Email déjà utilisé");
        }
        account.setEmail(nextEmail);
        repository.save(account);
    }

    @Override
    public void changePassword(ChangePasswordRequest request) {
        UserAccount account = repository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .orElseThrow(() -> new EntityNotFoundException("Utilisateur introuvable"));
        if (!passwordEncoder.matches(request.currentPassword(), account.getPassword())) {
            throw new IllegalArgumentException("Mot de passe actuel invalide");
        }
        String hashed = passwordEncoder.encode(request.newPassword());
        account.setPassword(hashed);
        account.setPasswordHash(hashed);
        repository.save(account);
    }

    private String normalizeEmail(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase();
    }
}