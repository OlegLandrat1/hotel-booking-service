package com.example.hotel.hotel.room;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Хранилище номеров с выборкой номеров, свободных на даты, и блокировкой номера на время транзакции.
 */
public interface RoomRepository extends JpaRepository<Room, Long> {

    /**
     * Номер с пессимистичной блокировкой на запись. Блокировка держится до конца транзакции.
     * @param id Идентификатор номера
     * @return Номер, если он есть
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> lockById(@Param("id") Long id);

    /**
     * Номера, доступные оператору и без пересечения блокировок с периодом.
     * @param arrival Дата заезда
     * @param departure Дата выезда
     * @param order Порядок выдачи
     * @return Свободные номера
     */
    @Query("select r from Room r where r.available = true and not exists (select l from SlotLock l where l.room = r and l.arrival < :departure and l.departure > :arrival)")
    List<Room> findFree(
            @Param("arrival") LocalDate arrival,
            @Param("departure") LocalDate departure,
            Sort order
    );
}