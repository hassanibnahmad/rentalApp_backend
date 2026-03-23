package com.julia_auto_cars.rental_api.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.julia_auto_cars.rental_api.model.UserAccount;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
}
