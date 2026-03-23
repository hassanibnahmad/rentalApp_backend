package com.julia_auto_cars.rental_api.repository;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.julia_auto_cars.rental_api.model.PasswordResetToken;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByToken(String token);
    void deleteByUser_Id(Long userId);
    void deleteByExpiresAtBefore(Instant threshold);
}
