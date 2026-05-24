package com.julia_auto_cars.rental_api.model;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cars")
public class Car {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String brand;
    private String model;
    private String category;

        @Column(name = "slug", unique = true, nullable = false)
    private String slug;

    @Column(name = "daily_price")
    private Integer pricePerDay;

    private Integer year;
    private Integer mileage;
    private String color;
    private Integer doors;
    private String engine;

    @Column(name = "license_plate")
    private String licensePlate;

    @Column(length = 120)
    private String location;

    private String transmission;
    private String fuel;
    private Integer seats;
    private String heroImage;
    private String whatsappMessage;

    @Column(length = 1000)
    private String description;

    @ElementCollection
    @CollectionTable(name = "car_equipments", joinColumns = @JoinColumn(name = "car_id"))
    @Column(name = "equipment")
    private List<String> equipments = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "car_gallery", joinColumns = @JoinColumn(name = "car_id"))
    private List<GalleryItem> gallery = new ArrayList<>();

    /** Record-like embeddable for gallery entries */
    @Embeddable
    public static class GalleryItem {
        private String label;
        private String imageUrl;

        // getters & setters

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getImageUrl() {
            return imageUrl;
        }

        public void setImageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
        }
    }

    // Getters and setters for all fields
    // Getters and setters


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public Integer getPricePerDay() {
        return pricePerDay;
    }

    public void setPricePerDay(Integer pricePerDay) {
        this.pricePerDay = pricePerDay;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public Integer getMileage() {
        return mileage;
    }

    public void setMileage(Integer mileage) {
        this.mileage = mileage;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public Integer getDoors() {
        return doors;
    }

    public void setDoors(Integer doors) {
        this.doors = doors;
    }

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public void setLicensePlate(String licensePlate) {
        this.licensePlate = licensePlate;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getTransmission() {
        return transmission;
    }

    public void setTransmission(String transmission) {
        this.transmission = transmission;
    }

    public String getFuel() {
        return fuel;
    }

    public void setFuel(String fuel) {
        this.fuel = fuel;
    }

    public Integer getSeats() {
        return seats;
    }

    public void setSeats(Integer seats) {
        this.seats = seats;
    }

    public String getHeroImage() {
        return heroImage;
    }

    public void setHeroImage(String heroImage) {
        this.heroImage = heroImage;
    }

    public String getWhatsappMessage() {
        return whatsappMessage;
    }

    public void setWhatsappMessage(String whatsappMessage) {
        this.whatsappMessage = whatsappMessage;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getEquipments() {
        return equipments;
    }

    public void setEquipments(List<String> equipments) {
        this.equipments = equipments;
    }

    public List<GalleryItem> getGallery() {
        return gallery;
    }

    public void setGallery(List<GalleryItem> gallery) {
        this.gallery = gallery;
    }

}

