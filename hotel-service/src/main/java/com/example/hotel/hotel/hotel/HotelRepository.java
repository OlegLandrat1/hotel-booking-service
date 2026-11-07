package com.example.hotel.hotel.hotel;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Хранилище гостиниц.
 */
public interface HotelRepository extends JpaRepository<Hotel, Long> {
}