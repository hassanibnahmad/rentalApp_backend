package com.julia_auto_cars.rental_api.service.impl;


import com.julia_auto_cars.rental_api.model.Car;
import com.julia_auto_cars.rental_api.repository.CarRepository;
import com.julia_auto_cars.rental_api.service.CarService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional
public class CarServiceImpl implements CarService {

    private final CarRepository carRepository;

    public CarServiceImpl(CarRepository carRepository) {
        this.carRepository = carRepository;
    }

    // This method is used to find all cars. It returns a list of cars from the repository.
    @Override
    @Transactional(readOnly = true)
    public List<Car> findAll() {
        return carRepository.findAll();
    }

    // This method is used to find a car by its slug. It throws an EntityNotFoundException if the car is not found.
    @Override
    @Transactional(readOnly = true) //
    public Car findBySlug(String slug) {
        return carRepository.findBySlug(slug)
                .orElseThrow(() -> new EntityNotFoundException("Car not found: " + slug));
    }

    // This method is used to create a new car. It checks if the slug is already used and throws an IllegalArgumentException if it is. Otherwise, it saves the car to the repository.
    @Override
    public Car create(Car car) {
        if (carRepository.existsBySlug(car.getSlug())) {
            throw new IllegalArgumentException("Slug already used: " + car.getSlug());
        }
        return carRepository.save(car);
    }

    // This method is used to update an existing car. It first checks if the car with the given id exists. If it does, it updates the car's id and saves the updated car to the repository. If the car does not exist, it throws an EntityNotFoundException.
    @Override
    public Car update(Long id, Car car) {
        Car existing = carRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Car not found: " + id));
        car.setId(existing.getId());
        return carRepository.save(car);
    }

    // This method is used to delete a car by its id. It first checks if the car with the given id exists. If it does, it deletes the car from the repository. If the car does not exist, it throws an EntityNotFoundException.
    @Override
    public void delete(Long id) {
        if (!carRepository.existsById(id)) {
            throw new EntityNotFoundException("Car not found: " + id);
        }
        carRepository.deleteById(id);
    }
}
