package com.example.hotel.booking.booking;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Хранилище бронирований.
 */
public interface BookingRepository extends JpaRepository<Booking, Long> {

    /**
     * Бронирование по запросу клиента, для идемпотентности.
     * @param userId Пользователь
     * @param requestId Идентификатор запроса
     * @return Бронирование, если запрос уже обрабатывался
     */
    Optional<Booking> findByUserIdAndRequestId(Long userId, String requestId);

    /**
     * Бронирование пользователя по идентификатору.
     * @param id Идентификатор
     * @param userId Пользователь
     * @return Бронирование, если принадлежит пользователю
     */
    Optional<Booking> findByIdAndUserId(Long id, Long userId);

    /**
     * История бронирований пользователя, новые первыми.
     * @param userId Пользователь
     * @return Бронирования
     */
    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Бронирования в состоянии, созданные раньше порога.
     * @param status Состояние
     * @param before Порог по времени создания
     * @return Найденные бронирования
     */
    List<Booking> findByStatusAndCreatedAtBefore(BookingStatus status, Instant before);

    /**
     * Бронирование с блокировкой строки на время транзакции.
     * @param id Идентификатор
     * @return Бронирование
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> lockById(@Param("id") Long id);
}