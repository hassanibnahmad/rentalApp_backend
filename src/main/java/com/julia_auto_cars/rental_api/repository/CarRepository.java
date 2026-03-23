package com.julia_auto_cars.rental_api.repository;

import com.julia_auto_cars.rental_api.model.Car;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CarRepository extends JpaRepository<Car, Long> {
    Optional<Car> findBySlug(String slug);
    boolean existsBySlug(String slug);
}

