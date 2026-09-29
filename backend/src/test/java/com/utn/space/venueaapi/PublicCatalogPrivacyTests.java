package com.utn.space.venueaapi;

import com.utn.space.venueaapi.model.*;
import com.utn.space.venueaapi.repository.*;
import com.utn.space.venueaapi.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PublicCatalogPrivacyTests {
    @Autowired MockMvc mvc;
    @Autowired ConsumerRepository consumers;
    @Autowired SpaceRepository spaces;
    @Autowired SpaceImageRepository images;
    @Autowired ReservationRepository reservations;
    @Autowired JwtUtil jwt;
    Consumer owner;
    Space space;
    SpaceImage image;

    @BeforeEach
    void setup() {
        Credential credential = new Credential();
        credential.setUsername("private-catalog-username");
        credential.setPassword("private-catalog-hash");
        credential.setRol(ERoles.ROLE_ADMIN);
        owner = new Consumer();
        owner.setCredentials(credential);
        owner.setFirstname("Ana");
        owner.setLastname("Pérez");
        owner.setEmail("private-catalog@example.com");
        owner.setPhone("private-catalog-phone");
        owner = consumers.saveAndFlush(owner);
        space = new Space();
        space.setConsumerOwner(owner);
        space.setNameSpace("Sala pública");
        space.setDescription("Espacio disponible");
        space.setBasePrice(new BigDecimal("1200"));
        space.setIsActive(true);
        space.setBufferTime(30);
        space.setLocation(new Location(null, new BigDecimal("-58"), new BigDecimal("-34")));
        space.setCancellationPolicies(new CancellationPolicies(null, EPolicyType.STRICT, 7, BigDecimal.TEN));
        space.setServices(List.of(new SpaceServiceItem(null, "Proyector", BigDecimal.TEN, true, space)));
        space = spaces.saveAndFlush(space);
        image = images.saveAndFlush(new SpaceImage(null, space, "sala.jpg", "https://example.com/sala.jpg", LocalDateTime.now()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"spaces", "space", "filter", "images", "image", "spaceImages"})
    void everyPublicCatalogRouteOmitsContactDetailsAndCredentials(String route) throws Exception {
        var request = switch (route) {
            case "spaces" -> get("/api/spaces");
            case "space" -> get("/api/spaces/" + space.getIdSpace());
            case "filter" -> post("/api/spaces/byfields").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"idConsumerOwner\":" + owner.getIdConsumer() + "}");
            case "images" -> get("/api/spaceimages");
            case "image" -> get("/api/spaceimages/" + image.getIdSpaceImages());
            default -> get("/api/spaceimages/byspaceid/" + space.getIdSpace());
        };
        assertPrivateDataAbsent(mvc.perform(request));
    }

    @Test
    void publicSpaceRetainsUsefulFieldsAndExplicitOwnerSummary() throws Exception {
        mvc.perform(get("/api/spaces/" + space.getIdSpace()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idSpace").value(space.getIdSpace()))
                .andExpect(jsonPath("$.consumerOwner", aMapWithSize(3)))
                .andExpect(jsonPath("$.consumerOwner.idConsumer").value(owner.getIdConsumer()))
                .andExpect(jsonPath("$.consumerOwner.firstname").value("Ana"))
                .andExpect(jsonPath("$.consumerOwner.lastname").value("Pérez"))
                .andExpect(jsonPath("$.basePrice").value(1200))
                .andExpect(jsonPath("$.location.latitude").value(-34))
                .andExpect(jsonPath("$.cancellationPolicies.type").value("STRICT"))
                .andExpect(jsonPath("$.services[0].description").value("Proyector"))
                .andExpect(jsonPath("$.services[0].space").doesNotExist());
    }

    @Test
    void authenticatedCatalogStillUsesPublicOwnerSummary() throws Exception {
        for (String route : List.of("/api/spaces/ownedspaces", "/api/spaces/showinactives")) {
            assertPrivateDataAbsent(mvc.perform(get(route).header("Authorization", token())));
        }
    }

    @Test
    void nestedReservationSpaceDoesNotExposeOwnerContactOrCredentials() throws Exception {
        Reservation reservation = new Reservation();
        reservation.setConsumer(owner);
        reservation.setSpace(space);
        reservation.setStatus(ReservationStatus.TENTATIVE);
        reservation.setFromDate(LocalDateTime.now().plusDays(2));
        reservation.setUntilDate(reservation.getFromDate().plusHours(1));
        reservation = reservations.saveAndFlush(reservation);
        mvc.perform(get("/api/reservations/" + reservation.getId()).header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.space.consumerOwner.email").doesNotExist())
                .andExpect(jsonPath("$.space.consumerOwner.phone").doesNotExist())
                .andExpect(jsonPath("$.space.consumerOwner.credentials").doesNotExist());
    }

    @Test
    void authorizedProfileStillIncludesItsContactDetails() throws Exception {
        mvc.perform(get("/api/usuarios/" + owner.getIdConsumer()).header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(owner.getEmail()))
                .andExpect(jsonPath("$.phone").value(owner.getPhone()))
                .andExpect(jsonPath("$.credentials.password").doesNotExist());
    }

    private void assertPrivateDataAbsent(ResultActions response) throws Exception {
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$..email").isEmpty())
                .andExpect(jsonPath("$..phone").isEmpty())
                .andExpect(jsonPath("$..credentials").isEmpty())
                .andExpect(jsonPath("$..password").isEmpty())
                .andExpect(jsonPath("$..username").isEmpty())
                .andExpect(jsonPath("$..rol").isEmpty())
                .andExpect(jsonPath("$..authorities").isEmpty())
                .andExpect(content().string(not(containsString("private-catalog"))));
    }

    private String token() {
        return "Bearer " + jwt.generarToken(owner.getCredentials().getUsername(), "ROLE_ADMIN", owner.getIdConsumer());
    }
}
