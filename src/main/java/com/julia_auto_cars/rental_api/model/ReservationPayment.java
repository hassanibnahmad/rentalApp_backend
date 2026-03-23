package com.julia_auto_cars.rental_api.model;


import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

// Represents a payment made for a reservation, including details about the payment provider, amount, currency, and status.
@Entity
@Table(name = "reservation_payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationPayment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    // The payment provider used for the transaction (e.g., "Stripe", "PayPal"). This field is required and cannot be null.
    @Column(nullable = false)
    private String provider; // e.g., "Stripe", "PayPal"

    // A reference or transaction ID provided by the payment provider for this specific payment. This field is required and cannot be null.
    @Column(nullable = false)
    private String providerReference;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    // The status of the payment (e.g., "PENDING", "COMPLETED", "FAILED"). This field is required and cannot be null.
    @Column(nullable = false)
    private String status;

    // The date and time when the payment was processed. This field is required and cannot be null.
    @Column(nullable = false)
    private OffsetDateTime processedAt;
}

