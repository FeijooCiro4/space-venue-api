package com.utn.space.venueaapi;

import com.utn.space.venueaapi.model.Consumer;
import com.utn.space.venueaapi.model.Credential;
import com.utn.space.venueaapi.model.Space;
import com.utn.space.venueaapi.model.SpaceServiceItem;
import com.utn.space.venueaapi.repository.ConsumerRepository;
import com.utn.space.venueaapi.repository.SpaceRepository;
import com.utn.space.venueaapi.repository.NotificationRepository;
import com.utn.space.venueaapi.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiBoundaryTests {
    @Autowired MockMvc mvc;
    @Autowired ConsumerRepository consumers;
    @Autowired SpaceRepository spaces;
    @Autowired NotificationRepository notifications;
    @Autowired JwtUtil jwtUtil;
    @Autowired org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Test
    void corsPreflightAllowsConfiguredOriginAndAuthorizationHeader() throws Exception {
        mvc.perform(options("/api/reservations")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization,Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(options("/api/reservations")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void catalogIsAvailableWithoutAuthentication() throws Exception {
        mvc.perform(get("/api/spaces")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/reservations']").exists());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void consumerResponsesNeverExposePasswordHashes() throws Exception {
        Credential credential = new Credential();
        credential.setUsername("serialization-test");
        credential.setPassword("private-password-hash");
        Consumer consumer = new Consumer();
        consumer.setCredentials(credential);
        Consumer saved = consumers.saveAndFlush(consumer);
        mvc.perform(get("/api/usuarios/" + saved.getIdConsumer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credentials.username").value("serialization-test"))
                .andExpect(jsonPath("$.credentials.password").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void browserCanSubmitUserFiltersWithPost() throws Exception {
        mvc.perform(post("/api/usuarios/byfields")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    @Transactional
    void reservationKeepsPricingAvailabilityAndOwnerNotification() throws Exception {
        Consumer owner = createConsumer("booking-owner");
        Consumer client = createConsumer("booking-client");
        Space space = new Space();
        space.setNameSpace("Sala de pruebas");
        space.setConsumerOwner(owner);
        space.setBasePrice(new BigDecimal("1000.00"));
        space.setBufferTime(30);
        space.setIsActive(true);
        SpaceServiceItem extra = new SpaceServiceItem(null, "Proyector",
                new BigDecimal("250.00"), true, space);
        space.setServices(List.of(extra));
        spaces.saveAndFlush(space);

        LocalDateTime from = LocalDateTime.now().plusDays(2).truncatedTo(ChronoUnit.HOURS);
        String body = """
                {"title":"Reunión", "description":"Equipo", "idSpace":%d,
                 "fromDate":"%s", "untilDate":"%s", "idServicesSelec":[%d]}
                """.formatted(space.getIdSpace(),
                        from.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")),
                        from.plusHours(2).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")),
                        extra.getId());
        String authorization = "Bearer " + jwtUtil.generarToken(
                "booking-client", "ROLE_CLIENT", client.getIdConsumer());

        mvc.perform(post("/api/reservations").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.finalPrice").value(2250.0))
                .andExpect(jsonPath("$.status").value("TENTATIVE"))
                .andExpect(jsonPath("$.consumer.idConsumer").value(client.getIdConsumer()))
                .andExpect(jsonPath("$.googleEventCode").doesNotExist())
                .andExpect(jsonPath("$.saveToMyCalendar").doesNotExist())
                .andExpect(jsonPath("$.space.googleCalendarId").doesNotExist());

        assertEquals(1, notifications.findByConsumer_IdConsumer(owner.getIdConsumer()).size());
        mvc.perform(post("/api/reservations").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertEquals(1, notifications.findByConsumer_IdConsumer(owner.getIdConsumer()).size());
    }

    @Test
    @WithMockUser
    void removedCalendarEndpointsAndSchemasAreAbsent() throws Exception {
        assertTrue(requestMappingHandlerMapping.getHandlerMethods().keySet().stream()
                .noneMatch(mapping -> mapping.toString().contains("/api/google-oauth2")));
        String schema = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(schema.contains("googleCalendarId"));
        assertFalse(schema.contains("googleEventCode"));
        assertFalse(schema.contains("saveToMyCalendar"));
        assertFalse(schema.contains("/api/google-oauth2"));
    }

    private Consumer createConsumer(String username) {
        Credential credential = new Credential();
        credential.setUsername(username);
        credential.setPassword("test-password-hash");
        Consumer consumer = new Consumer();
        consumer.setCredentials(credential);
        consumer.setFirstname(username);
        consumer.setLastname("Prueba");
        return consumers.saveAndFlush(consumer);
    }
}
