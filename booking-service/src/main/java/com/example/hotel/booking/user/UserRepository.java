package com.example.hotel.booking.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Хранилище пользователей.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Поиск по логину для входа.
     * @param login Логин
     * @return Пользователь, если есть
     */
    Optional<User> findByUsername(String login);
}