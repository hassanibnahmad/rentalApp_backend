package com.julia_auto_cars.rental_api.automation.repository;

import com.julia_auto_cars.rental_api.automation.model.AutomationEvent;
import com.julia_auto_cars.rental_api.automation.model.AutomationEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AutomationEventRepository extends JpaRepository<AutomationEvent, UUID> {

    Optional<AutomationEvent> findBySourceAndExternalIdAndType(String source, String externalId, AutomationEventType type);

    Optional<AutomationEvent> findByExternalIdAndType(String externalId, AutomationEventType type);
}
