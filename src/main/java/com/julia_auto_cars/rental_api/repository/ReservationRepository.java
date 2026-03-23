package com.julia_auto_cars.rental_api.repository;


import com.julia_auto_cars.rental_api.model.Reservation;
import com.julia_auto_cars.rental_api.model.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
// Repository for Reservation entity that extends JpaRepository to provide CRUD operations and custom query to find overlapping reservations based on car ID, date range, and reservation statuses.
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @Query("SELECT r FROM Reservation r WHERE r.carId = :carId " +
            "AND r.status IN :statuses " +
            "AND r.pickupDate < :endDate " +
            "AND r.returnDate > :startDate")
    List<Reservation> findOverlapping(@Param("carId") Long carId,
                                      @Param("startDate") LocalDate startDate,
                                      @Param("endDate") LocalDate endDate,
                                      @Param("statuses") List<ReservationStatus> statuses);
}

