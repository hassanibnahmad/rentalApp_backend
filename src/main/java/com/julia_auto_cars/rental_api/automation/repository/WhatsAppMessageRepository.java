package com.julia_auto_cars.rental_api.automation.repository;

import com.julia_auto_cars.rental_api.automation.model.WhatsAppMessage;
import com.julia_auto_cars.rental_api.automation.model.MessageStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WhatsAppMessageRepository extends JpaRepository<WhatsAppMessage, UUID> {

    Page<WhatsAppMessage> findByTemplateId(String templateId, Pageable pageable);

    Page<WhatsAppMessage> findByStatus(MessageStatus status, Pageable pageable);

    Page<WhatsAppMessage> findByToPhone(String toPhone, Pageable pageable);

    Page<WhatsAppMessage> findByTemplateIdAndStatus(String templateId, MessageStatus status, Pageable pageable);

    Optional<WhatsAppMessage> findFirstByProviderMessageId(String providerMessageId);
}
