package com.utn.space.venueaapi;

import com.utn.space.venueaapi.model.*;
import com.utn.space.venueaapi.repository.*;
import com.utn.space.venueaapi.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ResourceOwnershipTests {
    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;
    @Autowired ConsumerRepository consumers;
    @Autowired SpaceRepository spaces;
    @Autowired ReservationRepository reservations;
    @Autowired SpaceImageRepository images;
    @Autowired ServiceSelectedRepository selectedServices;
    @Autowired NotificationRepository notifications;
    @Autowired CommentRepository comments;
    Consumer owner, client, outsider, admin;
    Space space, otherSpace;
    Reservation reservation;
    SpaceImage image;
    ServiceSelected selectedService;
    Notification notification;
    Comment comment;

    @BeforeEach
    void setup() {
        owner = consumer("ownership-owner", ERoles.ROLE_CLIENT);
        client = consumer("ownership-client", ERoles.ROLE_CLIENT);
        outsider = consumer("ownership-outsider", ERoles.ROLE_CLIENT);
        admin = consumer("ownership-admin", ERoles.ROLE_ADMIN);
        space = space(owner);
        otherSpace = space(outsider);
        reservation = new Reservation();
        reservation.setTitle("Original");
        reservation.setDescription("Reserva original");
        reservation.setConsumer(client);
        reservation.setSpace(space);
        reservation.setFromDate(LocalDateTime.now().plusDays(2).withNano(0));
        reservation.setUntilDate(reservation.getFromDate().plusHours(2));
        reservation.setCreatedAt(LocalDateTime.now().withNano(0));
        reservation.setStatus(ReservationStatus.TENTATIVE);
        reservation.setFinalPrice(new BigDecimal("2000"));
        reservation.setServices(new ArrayList<>());
        reservations.saveAndFlush(reservation);
        selectedService = selectedServices.saveAndFlush(new ServiceSelected(
                null, new BigDecimal("250"), "Proyector", reservation));
        image = images.saveAndFlush(new SpaceImage(null, space, "original.jpg",
                "https://example.com/original.jpg", LocalDateTime.now()));
        notification = notifications.saveAndFlush(new Notification(null, LocalDateTime.now(),
                "Notificación privada", false, client));
        comment = comments.saveAndFlush(new Comment(null, client, space, "Original", (byte) 4, LocalDateTime.now()));
    }

    @Test
    void outsiderCannotReadOrMutateAnotherReservation() throws Exception {
        denied(get("/api/reservations"), outsider);
        denied(get("/api/reservations/" + reservation.getId()), outsider);
        denied(put("/api/reservations").contentType(MediaType.APPLICATION_JSON)
                .content(reservationBody(outsider)), outsider);
        long originalNotificationCount = notifications.count();
        for (String action : List.of("confirm", "reject", "complete", "cancel")) {
            denied(put("/api/reservations/" + action + "/" + reservation.getId()), outsider);
        }
        denied(delete("/api/reservations/" + reservation.getId()), outsider);
        denied(post("/api/reservations/" + reservation.getId() + "/checkout"), outsider);
        assertEquals(ReservationStatus.TENTATIVE, reservations.findById(reservation.getId()).orElseThrow().getStatus());
        assertEquals(client.getIdConsumer(), reservation.getConsumer().getIdConsumer());
        assertEquals("Original", reservation.getTitle());
        assertEquals(originalNotificationCount, notifications.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"client", "owner", "admin"})
    void reservationParticipantsAndAdminCanRead(String actor) throws Exception {
        mvc.perform(get("/api/reservations/" + reservation.getId()).header("Authorization", token(actor(actor))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(reservation.getId()));
        mvc.perform(get("/api/servicesselected/reservation/" + reservation.getId())
                        .header("Authorization", token(actor(actor))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].descriptionFrozen").value("Proyector"));
    }

    @Test
    void globalReservationListIsAdminOnlyAndMeStillWorks() throws Exception {
        denied(get("/api/reservations"), client);
        denied(get("/api/reservations"), owner);
        mvc.perform(get("/api/reservations").header("Authorization", token(admin))).andExpect(status().isOk());
        mvc.perform(get("/api/reservations/me").header("Authorization", token(client)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(reservation.getId()));
        mvc.perform(get("/api/reservations/me").header("Authorization", token(outsider)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"client,confirm,403", "client,reject,403", "client,complete,403", "client,cancel,200",
            "owner,confirm,200", "owner,reject,200", "owner,complete,200", "owner,cancel,200",
            "admin,confirm,200", "admin,reject,200", "admin,complete,200", "admin,cancel,200"})
    void reservationActionsEnforceTheActorRole(String actor, String action, int expectedStatus) throws Exception {
        if ("complete".equals(action)) {
            reservation.setStatus(ReservationStatus.CONFIRMED);
            reservations.saveAndFlush(reservation);
        }
        mvc.perform(put("/api/reservations/" + action + "/" + reservation.getId())
                        .header("Authorization", token(actor(actor))))
                .andExpect(status().is(expectedStatus));
        ReservationStatus expected = expectedStatus == 403
                ? ("complete".equals(action) ? ReservationStatus.CONFIRMED : ReservationStatus.TENTATIVE) : switch (action) {
            case "confirm" -> ReservationStatus.CONFIRMED;
            case "reject" -> ReservationStatus.REJECTED;
            case "complete" -> ReservationStatus.COMPLETED;
            default -> ReservationStatus.CANCELLED;
        };
        assertEquals(expected, reservations.findById(reservation.getId()).orElseThrow().getStatus());
    }

    @Test
    void reservationEditCannotTransferOwnershipOrBypassStatePermissions() throws Exception {
        denied(put("/api/reservations").contentType(MediaType.APPLICATION_JSON)
                .content(reservationBody(outsider)), client);
        denied(put("/api/reservations").contentType(MediaType.APPLICATION_JSON)
                .content(reservationBody(client)), owner);
        LocalDateTime originalCreatedAt = reservation.getCreatedAt();
        mvc.perform(put("/api/reservations").header("Authorization", token(client))
                        .contentType(MediaType.APPLICATION_JSON).content(reservationBody(client)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Editada"))
                .andExpect(jsonPath("$.consumer.idConsumer").value(client.getIdConsumer()))
                .andExpect(jsonPath("$.status").value("TENTATIVE"));
        Reservation saved = reservations.findById(reservation.getId()).orElseThrow();
        assertTrue(saved.getIsActive());
        assertEquals(originalCreatedAt, saved.getCreatedAt());
    }

    @Test
    void adminCanEditAndDeleteAnotherReservation() throws Exception {
        mvc.perform(put("/api/reservations").header("Authorization", token(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(reservationBody(client)))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/reservations/" + reservation.getId()).header("Authorization", token(admin)))
                .andExpect(status().isOk());
        assertFalse(reservations.findById(reservation.getId()).orElseThrow().getIsActive());
    }

    @Test
    void imageWritesCheckBothStoredOwnerAndDestinationSpace() throws Exception {
        long originalCount = images.count();
        denied(post("/api/spaceimages").contentType(MediaType.APPLICATION_JSON).content(imageBody(space)), outsider);
        denied(put("/api/spaceimages/" + image.getIdSpaceImages()).contentType(MediaType.APPLICATION_JSON)
                .content(imageBody(otherSpace)), outsider);
        denied(delete("/api/spaceimages/" + image.getIdSpaceImages()), outsider);
        denied(put("/api/spaceimages/" + image.getIdSpaceImages()).contentType(MediaType.APPLICATION_JSON)
                .content(imageBody(otherSpace)), owner);
        SpaceImage saved = images.findById(image.getIdSpaceImages()).orElseThrow();
        assertEquals(space.getIdSpace(), saved.getSpace().getIdSpace());
        assertEquals("original.jpg", saved.getFileName());
        assertEquals(originalCount, images.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"owner", "admin"})
    void imageOwnerAndAdminCanWrite(String actor) throws Exception {
        String auth = token(actor(actor));
        mvc.perform(post("/api/spaceimages").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(imageBody(space)))
                .andExpect(status().isOk());
        mvc.perform(put("/api/spaceimages/" + image.getIdSpaceImages()).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(imageBody(space)))
                .andExpect(status().isOk());
        assertEquals("editada.jpg", images.findById(image.getIdSpaceImages()).orElseThrow().getFileName());
        mvc.perform(delete("/api/spaceimages/" + image.getIdSpaceImages()).header("Authorization", auth))
                .andExpect(status().isOk());
        assertFalse(images.existsById(image.getIdSpaceImages()));
    }

    @Test
    void publicImageReadsRemainPublic() throws Exception {
        mvc.perform(get("/api/spaceimages/" + image.getIdSpaceImages())).andExpect(status().isOk());
        mvc.perform(get("/api/spaceimages/byspaceid/" + space.getIdSpace())).andExpect(status().isOk());
        mvc.perform(delete("/api/spaceimages/" + image.getIdSpaceImages())).andExpect(status().isForbidden());
        assertTrue(images.existsById(image.getIdSpaceImages()));
    }

    @Test
    void onlyReservationConsumerOrAdminCanChangeSelectedServices() throws Exception {
        long originalCount = selectedServices.count();
        denied(get("/api/servicesselected/reservation/" + reservation.getId()), outsider);
        for (Consumer actor : List.of(outsider, owner)) {
            denied(post("/api/servicesselected/insert/list/" + reservation.getId())
                    .contentType(MediaType.APPLICATION_JSON).content(selectedServiceBody()), actor);
            denied(delete("/api/servicesselected/delete/" + selectedService.getId()), actor);
        }
        assertEquals(originalCount, selectedServices.count());
        assertTrue(selectedServices.existsById(selectedService.getId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"client", "admin"})
    void selectedServiceConsumerAndAdminCanWrite(String actor) throws Exception {
        String auth = token(actor(actor));
        long originalCount = selectedServices.count();
        mvc.perform(post("/api/servicesselected/insert/list/" + reservation.getId()).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(selectedServiceBody()))
                .andExpect(status().isOk());
        assertEquals(originalCount + 1, selectedServices.count());
        mvc.perform(delete("/api/servicesselected/delete/" + selectedService.getId()).header("Authorization", auth))
                .andExpect(status().isOk());
        assertFalse(selectedServices.existsById(selectedService.getId()));
    }

    @Test
    void notificationsArePrivateToRecipient() throws Exception {
        for (Consumer actor : List.of(outsider, owner)) {
            denied(get("/api/notifications/" + notification.getIdNotification()), actor);
            denied(post("/api/notifications/" + notification.getIdNotification()), actor);
        }
        mvc.perform(get("/api/notifications/consumer/" + client.getIdConsumer())
                        .header("Authorization", token(outsider)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        assertFalse(notifications.findById(notification.getIdNotification()).orElseThrow().getIsSeen());
    }

    @ParameterizedTest
    @ValueSource(strings = {"client", "admin"})
    void recipientAndAdminCanReadAndMarkNotification(String actor) throws Exception {
        String auth = token(actor(actor));
        mvc.perform(get("/api/notifications/" + notification.getIdNotification()).header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Notificación privada"));
        mvc.perform(post("/api/notifications/" + notification.getIdNotification()).header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isSeen").value(true));
    }

    @Test
    void forgedCommentAuthorCannotGrantAccess() throws Exception {
        denied(put("/api/comments/" + comment.getIdComment()).contentType(MediaType.APPLICATION_JSON)
                .content(commentBody(outsider)), outsider);
        denied(delete("/api/comments/" + comment.getIdComment()), outsider);
        assertEquals("Original", comments.findById(comment.getIdComment()).orElseThrow().getDescription());
        assertEquals(client.getIdConsumer(), comment.getConsumer().getIdConsumer());
    }

    @ParameterizedTest
    @ValueSource(strings = {"client", "admin"})
    void storedCommentAuthorAndAdminCanEditAndDelete(String actor) throws Exception {
        String auth = token(actor(actor));
        mvc.perform(put("/api/comments/" + comment.getIdComment()).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(commentBody(outsider)))
                .andExpect(status().isOk());
        assertEquals("Editado", comments.findById(comment.getIdComment()).orElseThrow().getDescription());
        mvc.perform(delete("/api/comments/" + comment.getIdComment()).header("Authorization", auth))
                .andExpect(status().isOk());
        assertFalse(comments.existsById(comment.getIdComment()));
    }

    @Test
    void consumerProfileRequiresSelfOrAdmin() throws Exception {
        denied(get("/api/usuarios/" + client.getIdConsumer()), outsider);
        for (Consumer actor : List.of(client, admin)) {
            mvc.perform(get("/api/usuarios/" + client.getIdConsumer()).header("Authorization", token(actor)))
                    .andExpect(status().isOk());
        }
    }

    private void denied(MockHttpServletRequestBuilder request, Consumer actor) throws Exception {
        mvc.perform(request.header("Authorization", token(actor))).andExpect(status().isForbidden());
    }

    private Consumer actor(String name) {
        return switch (name) {
            case "owner" -> owner;
            case "client" -> client;
            case "admin" -> admin;
            default -> outsider;
        };
    }

    private String token(Consumer actor) {
        return "Bearer " + jwtUtil.generarToken(actor.getCredentials().getUsername(),
                actor.getCredentials().getRol().name(), actor.getIdConsumer());
    }

    private Consumer consumer(String username, ERoles role) {
        Credential credential = new Credential();
        credential.setUsername(username);
        credential.setPassword("unused-password-hash");
        credential.setRol(role);
        Consumer consumer = new Consumer();
        consumer.setFirstname(username);
        consumer.setLastname("Prueba");
        consumer.setCredentials(credential);
        return consumers.saveAndFlush(consumer);
    }

    private Space space(Consumer owner) {
        Space space = new Space();
        space.setNameSpace("Sala");
        space.setConsumerOwner(owner);
        space.setBasePrice(new BigDecimal("1000"));
        space.setBufferTime(30);
        space.setIsActive(true);
        space.setServices(new ArrayList<>());
        return spaces.saveAndFlush(space);
    }

    private String reservationBody(Consumer consumer) {
        DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        return """
                {"id":%d,"title":"Editada","description":"Edición de prueba","idConsumer":%d,
                 "idSpace":%d,"fromDate":"%s","untilDate":"%s","idServicesSelec":[],
                 "status":"CONFIRMED","isActive":false,"createdAt":"2000-01-01T00:00:00"}
                """.formatted(reservation.getId(), consumer.getIdConsumer(), space.getIdSpace(),
                reservation.getFromDate().format(format), reservation.getUntilDate().format(format));
    }

    private String imageBody(Space destination) {
        return """
                {"idImage":%d,"idSpace":%d,"fileName":"editada.jpg",
                 "urlImage":"https://example.com/edited.jpg","dateSend":"2027-01-01T12:00:00"}
                """.formatted(image.getIdSpaceImages(), destination.getIdSpace());
    }

    private String selectedServiceBody() {
        return """
                [{"priceAtReservation":100,"descriptionFrozen":"Extra","idReservation":%d}]
                """.formatted(reservation.getId());
    }

    private String commentBody(Consumer claimedAuthor) {
        return """
                {"idConsumer":%d,"idSpace":%d,"description":"Editado","score":4}
                """.formatted(claimedAuthor.getIdConsumer(), space.getIdSpace());
    }
}
