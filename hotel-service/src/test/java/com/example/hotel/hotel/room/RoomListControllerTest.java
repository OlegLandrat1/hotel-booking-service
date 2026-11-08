package com.example.hotel.hotel.room;

import com.example.hotel.hotel.hotel.Hotel;
import com.example.hotel.hotel.hotel.HotelRepository;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.util.UUID;

/**
 * Список свободных номеров через HTTP. Каждый тест создаёт свой номер.
 */
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
class RoomListControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private HotelRepository hotels;

    @Autowired
    private RoomRepository rooms;

    @Test
    void anonymousListGets401() throws Exception {
        this.mvc.perform(
                        MockMvcRequestBuilders.get("/api/rooms")
                                .param("arrival", "2026-12-01")
                                .param("departure", "2026-12-05")
                )
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    void userListContainsFreeRoom() throws Exception {
        final String number = "L-" + UUID.randomUUID();
        final Hotel hotel = this.hotels.save(new Hotel("Dune", "Coast 1"));
        this.rooms.save(new Room(hotel, number));
        this.mvc.perform(
                        MockMvcRequestBuilders.get("/api/rooms")
                                .param("arrival", "2026-12-01")
                                .param("departure", "2026-12-05")
                                .with(
                                        SecurityMockMvcRequestPostProcessors.jwt()
                                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))
                                )
                )
                .andExpect(MockMvcResultMatchers.jsonPath("$[*].number", Matchers.hasItem(number)));
    }
}