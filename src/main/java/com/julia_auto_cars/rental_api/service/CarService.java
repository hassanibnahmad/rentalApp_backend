package com.julia_auto_cars.rental_api.service;


import com.julia_auto_cars.rental_api.model.Car;
import java.util.List;

public interface CarService {
    List<Car> findAll();
    Car findBySlug(String slug);
    Car create(Car car);
    Car update(Long id, Car car);
    void delete(Long id);
}

