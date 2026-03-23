package com.julia_auto_cars.rental_api.controller;

import com.julia_auto_cars.rental_api.model.Car;
import com.julia_auto_cars.rental_api.service.CarService;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cars")
@CrossOrigin(origins = "*") // Allow CORS for all origins (you can restrict this in production)
public class CarController {

    private final CarService carService;

    public CarController(CarService carService) {
        this.carService = carService;
    }

    @GetMapping
    public List<Car> listCars() {
        return carService.findAll();
    }

    @GetMapping("/{slug}")
    public Car getBySlug(@PathVariable String slug) {
        return carService.findBySlug(slug);
    }

    @PostMapping
    public ResponseEntity<Car> create(@RequestBody Car car) {
        Car created = carService.create(car);
        return ResponseEntity.created(URI.create("/api/cars/" + created.getSlug())).body(created);
    }

    @PutMapping("/{id}")
    public Car update(@PathVariable Long id, @RequestBody Car car) {
        return carService.update(id, car);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        carService.delete(id);
        return ResponseEntity.noContent().build();
    }
}