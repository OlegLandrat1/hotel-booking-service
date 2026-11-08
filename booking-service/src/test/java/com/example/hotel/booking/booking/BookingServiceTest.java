package com.example.hotel.booking.booking;

import com.example.hotel.booking.hotel.HotelClient;
import com.example.hotel.booking.user.Role;
import com.example.hotel.booking.user.User;
import com.example.hotel.booking.user.UserRepository;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Полный цикл бронирования с подменённым Hotel Service: успех, сбой, перебор номеров,
 * отмена и повторная доставка запроса.
 */
@SpringBootTest(properties = "eureka.client.enabled=false")
class BookingServiceTest {

    private static final LocalDate ARRIVAL = LocalDate.of(2026, 12, 1);
    private static final LocalDate DEPARTURE = LocalDate.of(2026, 12, 5);

    @Autowired
    private BookingService service;

    @Autowired
    private UserRepository users;

    @MockitoBean
    private HotelClient hotel;

    private Long owner;

    @BeforeEach
    void setUp() {
        this.owner = this.users.save(
                new User("user-" + UUID.randomUUID(), "hash", Role.USER)
        ).id();
    }

    @Test
    void successfulConfirmationEndsConfirmed() {
        final Booking booking = this.service.create(
                this.owner, new NewBooking(UUID.randomUUID().toString(), 7L, false, ARRIVAL, DEPARTURE)
        );
        MatcherAssert.assertThat(
                "successful hotel answer did not confirm the booking",
                booking.status(),
                Matchers.equalTo(BookingStatus.CONFIRMED)
        );
    }

    @Test
    void hotelFailureCancelsAndReleasesLock() {
        doThrow(new HotelClient.HotelFailure("confirm", null))
                .when(this.hotel).confirm(anyLong(), any(), any(), anyString());
        final Booking booking = this.service.create(
                this.owner, new NewBooking(UUID.randomUUID().toString(), 7L, false, ARRIVAL, DEPARTURE)
        );
        MatcherAssert.assertThat(
                "hotel failure did not cancel the booking",
                booking.status(),
                Matchers.equalTo(BookingStatus.CANCELLED)
        );
        verify(this.hotel).release(eq(7L), anyString());
    }

    @Test
    void autoSelectSkipsBusyRoom() {
        when(this.hotel.recommend(ARRIVAL, DEPARTURE)).thenReturn(
                List.of(new HotelClient.RoomView(7L, "101", 0), new HotelClient.RoomView(8L, "102", 1))
        );
        doThrow(new HotelClient.RoomUnavailable("busy"))
                .when(this.hotel).confirm(eq(7L), any(), any(), anyString());
        final Booking booking = this.service.create(
                this.owner, new NewBooking(UUID.randomUUID().toString(), null, true, ARRIVAL, DEPARTURE)
        );
        MatcherAssert.assertThat(
                "booking did not move to the next free room",
                booking.roomId(),
                Matchers.equalTo(8L)
        );
        MatcherAssert.assertThat(
                "booking is not CONFIRMED after fallback",
                booking.status(),
                Matchers.equalTo(BookingStatus.CONFIRMED)
        );
    }

    @Test
    void repeatedRequestReturnsSameBookingWithoutSecondHotelCall() {
        final String requestId = UUID.randomUUID().toString();
        final Booking first = this.service.create(
                this.owner, new NewBooking(requestId, 7L, false, ARRIVAL, DEPARTURE)
        );
        final Booking second = this.service.create(
                this.owner, new NewBooking(requestId, 7L, false, ARRIVAL, DEPARTURE)
        );
        MatcherAssert.assertThat(
                "repeated requestId created a second booking",
                second.id(),
                Matchers.equalTo(first.id())
        );
        verify(this.hotel, times(1)).confirm(anyLong(), any(), any(), anyString());
    }

    @Test
    void userCancelOfConfirmedBookingReleasesRoom() {
        final Booking booking = this.service.create(
                this.owner, new NewBooking(UUID.randomUUID().toString(), 7L, false, ARRIVAL, DEPARTURE)
        );
        final Booking cancelled = this.service.cancel(this.owner, booking.id());
        MatcherAssert.assertThat(
                "user cancel did not set CANCELLED",
                cancelled.status(),
                Matchers.equalTo(BookingStatus.CANCELLED)
        );
        verify(this.hotel).release(eq(7L), anyString());
    }
}