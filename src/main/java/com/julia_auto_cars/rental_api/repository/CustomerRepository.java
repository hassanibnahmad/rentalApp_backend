package com.julia_auto_cars.rental_api.repository;


import com.julia_auto_cars.rental_api.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
// Repository for Customer entity that extends JpaRepository to provide CRUD operations and a custom query method to find a customer by their email address.
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByEmail(String email);
}
