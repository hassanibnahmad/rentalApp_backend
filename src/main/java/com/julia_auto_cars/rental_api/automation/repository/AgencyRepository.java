package com.julia_auto_cars.rental_api.automation.repository;

import com.julia_auto_cars.rental_api.automation.model.Agency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgencyRepository extends JpaRepository<Agency, UUID> {
    Optional<Agency> findFirstByOrderByCreatedAtAsc();
}
