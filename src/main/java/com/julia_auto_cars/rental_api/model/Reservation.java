package com.julia_auto_cars.rental_api.model;


import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "reservations",
        indexes = {
                @Index(name = "idx_res_car_dates", columnList = "car_id, pickup_date, return_date"),
                @Index(name = "idx_res_customer", columnList = "customer_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reservation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long carId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(nullable = false)
    private String pickupCity;

    @Column(nullable = false)
    private LocalDate pickupDate;

    @Column(nullable = false)
    private String returnCity;

    @Column(nullable = false)
    private LocalDate returnDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @Column(nullable = false)
    private BigDecimal dailyRate;

    @Column(nullable = false)
    private Integer daysCount;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    @Column(length = 500)
    private String notes;

    // ── automation flags (managed exclusively by the automation module) ──────
    @Column(name = "abandoned_sent",    nullable = false, columnDefinition = "boolean default false") @Builder.Default private Boolean abandonedSent    = Boolean.FALSE;
    @Column(name = "confirmation_sent", nullable = false, columnDefinition = "boolean default false") @Builder.Default private Boolean confirmationSent = Boolean.FALSE;
    @Column(name = "reminder_sent",     nullable = false, columnDefinition = "boolean default false") @Builder.Default private Boolean reminderSent     = Boolean.FALSE;
    @Column(name = "review_sent",       nullable = false, columnDefinition = "boolean default false") @Builder.Default private Boolean reviewSent       = Boolean.FALSE;
    @Column(name = "upsell_sent",       nullable = false, columnDefinition = "boolean default false") @Builder.Default private Boolean upsellSent       = Boolean.FALSE;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true) // Cascade all operations and remove orphans
    @Builder.Default
    private List<ReservationExtra> extras = new ArrayList<>();

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ReservationPayment> payments = new ArrayList<>();

    @PrePersist // Set timestamps before inserting a new reservation, ensuring createdAt is set and updatedAt is initialized to the same value
    void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
