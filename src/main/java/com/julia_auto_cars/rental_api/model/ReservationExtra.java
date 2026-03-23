package com.julia_auto_cars.rental_api.model;


import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

// this entity represents additional services or products added to a reservation, such as GPS, child seats, or insurance packages. Each ReservationExtra is linked to a specific Reservation and includes details about the extra service, such as its label, quantity, and unit price.
@Entity
@Table(name = "reservation_extras")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationExtra {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    // Label for the extra service (e.g., "GPS Navigation", "Child Seat", "Additional Insurance")
    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private BigDecimal unitPrice;
}
