package com.example.hotel.hotel.availability;

import com.example.hotel.hotel.room.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Хранилище блокировок слотов.
 */
public interface SlotLockRepository extends JpaRepository<SlotLock, Long> {

    /**
     * Блокировка по идентификатору запроса, для идемпотентности.
     * @param request Идентификатор запроса
     * @return Блокировка, если она уже есть
     */
    Optional<SlotLock> findByRequest(String request);

    /**
     * Блокировки номера, пересекающиеся с периодом.
     * @param room Номер
     * @param arrival Дата заезда
     * @param departure Дата выезда
     * @return Пересекающиеся блокировки
     */
    @Query("select l from SlotLock l where l.room = :room and l.arrival < :departure and l.departure > :arrival")
    List<SlotLock> findOverlapping(
            @Param("room") Room room,
            @Param("arrival") LocalDate arrival,
            @Param("departure") LocalDate departure
    );
}