package com.example.hotel.hotel.availability;

import com.example.hotel.hotel.hotel.Hotel;
import com.example.hotel.hotel.hotel.HotelRepository;
import com.example.hotel.hotel.room.Room;
import com.example.hotel.hotel.room.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Проверка ролей, подтверждения занятости и освобождения через HTTP.
 * Каждый тест выполняется в транзакции и откатывается, поэтому данные не пересекаются.
 */
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
@Transactional
class AvailabilityControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private HotelRepository hotels;

    @Autowired
    private RoomRepository rooms;

    private Long room;

    @BeforeEach
    void setUp() {
        final Hotel hotel = this.hotels.save(new Hotel("Dune", "Coast 1"));
        this.room = this.rooms.save(new Room(hotel, "101")).id();
    }

    @Test
    void anonymousRequestGets401() throws Exception {
        this.mvc.perform(get("/api/hotels"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userCanListHotels() throws Exception {
        this.mvc.perform(get("/api/hotels").with(as("USER")))
                .andExpect(status().isOk());
    }

    @Test
    void userCannotCreateHotel() throws Exception {
        this.mvc.perform(post("/api/hotels")
                        .with(as("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sea\",\"address\":\"Pier 2\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanCreateHotel() throws Exception {
        this.mvc.perform(post("/api/hotels")
                        .with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sea\",\"address\":\"Pier 2\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Sea"));
    }

    @Test
    void userGetsRecommendedRooms() throws Exception {
        this.mvc.perform(get("/api/rooms/recommend")
                        .param("arrival", "2026-12-01")
                        .param("departure", "2026-12-05")
                        .with(as("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].number").value("101"));
    }

    @Test
    void userCannotConfirmAvailability() throws Exception {
        confirm("USER", "req-u", "2026-12-01", "2026-12-05")
                .andExpect(status().isForbidden());
    }

    @Test
    void serviceConfirmsFreeRoom() throws Exception {
        confirm("SERVICE", "req-1", "2026-12-01", "2026-12-05")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lockId").isNumber());
    }

    @Test
    void overlappingDatesGet409() throws Exception {
        confirm("SERVICE", "req-1", "2026-12-01", "2026-12-05")
                .andExpect(status().isOk());
        confirm("SERVICE", "req-2", "2026-12-03", "2026-12-07")
                .andExpect(status().isConflict());
    }

    @Test
    void departureDayDoesNotBlockArrival() throws Exception {
        confirm("SERVICE", "req-1", "2026-12-01", "2026-12-05")
                .andExpect(status().isOk());
        confirm("SERVICE", "req-2", "2026-12-05", "2026-12-08")
                .andExpect(status().isOk());
    }

    @Test
    void repeatWithSameRequestReturnsSameLock() throws Exception {
        final String first = confirm("SERVICE", "req-1", "2026-12-01", "2026-12-05")
                .andReturn().getResponse().getContentAsString();
        confirm("SERVICE", "req-1", "2026-12-01", "2026-12-05")
                .andExpect(status().isOk())
                .andExpect(content().json(first));
    }

    @Test
    void releaseFreesDates() throws Exception {
        confirm("SERVICE", "req-1", "2026-12-01", "2026-12-05")
                .andExpect(status().isOk());
        this.mvc.perform(post("/api/rooms/{id}/release", this.room)
                        .with(as("SERVICE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"req-1\"}"))
                .andExpect(status().isNoContent());
        confirm("SERVICE", "req-2", "2026-12-01", "2026-12-05")
                .andExpect(status().isOk());
    }

    @Test
    void releaseOfUnknownRequestChangesNothing() throws Exception {
        this.mvc.perform(post("/api/rooms/{id}/release", this.room)
                        .with(as("SERVICE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"missing\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void unknownRoomGets404() throws Exception {
        this.mvc.perform(post("/api/rooms/{id}/confirm-availability", 999_999L)
                        .with(as("SERVICE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hold("2026-12-01", "2026-12-05", "req-1")))
                .andExpect(status().isNotFound());
    }

    private ResultActions confirm(
            final String role,
            final String request,
            final String arrival,
            final String departure
    ) throws Exception {
        return this.mvc.perform(post("/api/rooms/{id}/confirm-availability", this.room)
                .with(as(role))
                .contentType(MediaType.APPLICATION_JSON)
                .content(hold(arrival, departure, request)));
    }

    private static String hold(final String arrival, final String departure, final String request) {
        return String.format(
                "{\"arrival\":\"%s\",\"departure\":\"%s\",\"requestId\":\"%s\"}",
                arrival, departure, request
        );
    }

    private static RequestPostProcessor as(final String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}