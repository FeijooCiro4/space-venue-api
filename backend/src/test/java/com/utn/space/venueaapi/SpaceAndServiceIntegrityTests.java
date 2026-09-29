package com.utn.space.venueaapi;

import com.utn.space.venueaapi.exceptions.*;
import com.utn.space.venueaapi.model.*;
import com.utn.space.venueaapi.model.records.*;
import com.utn.space.venueaapi.repository.*;
import com.utn.space.venueaapi.security.JwtUtil;
import com.utn.space.venueaapi.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SpaceAndServiceIntegrityTests {
    @Autowired ServiceSelectedService selectedService;
    @Autowired SpaceService spaceService;
    @Autowired ConsumerRepository consumers;
    @Autowired SpaceRepository spaces;
    @Autowired SpaceServiceItemRepository catalog;
    @Autowired ReservationRepository reservations;
    @Autowired ServiceSelectedRepository selections;
    @Autowired LocationRepository locations;
    @Autowired CancellationPoliciesRepository policies;
    @Autowired PaymentRepository payments;
    @Autowired SpaceImageRepository images;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwt;
    Consumer owner, client, outsider, admin;
    Space space;
    SpaceServiceItem first, second;
    Reservation reservation;
    Location location;

    @BeforeEach
    void setup() {
        owner = consumer("integrity-owner", ERoles.ROLE_CLIENT);
        client = consumer("integrity-client", ERoles.ROLE_CLIENT);
        outsider = consumer("integrity-outsider", ERoles.ROLE_CLIENT);
        admin = consumer("integrity-admin", ERoles.ROLE_ADMIN);
        location = locations.saveAndFlush(new Location(null, new BigDecimal("-58"), new BigDecimal("-34")));
        CancellationPolicies policy = policies.findByType(EPolicyType.FLEXIBLE).orElseGet(() ->
                policies.saveAndFlush(new CancellationPolicies(null, EPolicyType.FLEXIBLE, 1, BigDecimal.TEN)));
        space = new Space();
        space.setConsumerOwner(owner);
        space.setLocation(location);
        space.setCancellationPolicies(policy);
        space.setNameSpace("Sala original");
        space.setDescription("Descripción original");
        space.setBasePrice(new BigDecimal("1000"));
        space.setBufferTime(30);
        space.setPublicationDate(LocalDate.of(2025, 1, 1));
        space.setIsActive(true);
        space.setServices(new ArrayList<>());
        space = spaces.saveAndFlush(space);
        first = catalog.saveAndFlush(new SpaceServiceItem(null, "Proyector", new BigDecimal("250"), true, space));
        second = catalog.saveAndFlush(new SpaceServiceItem(null, "Sonido", new BigDecimal("125"), true, space));
        reservation = new Reservation();
        reservation.setConsumer(client);
        reservation.setSpace(space);
        reservation.setFromDate(LocalDateTime.now().plusDays(5));
        reservation.setUntilDate(reservation.getFromDate().plusHours(2));
        reservation.setFinalPrice(new BigDecimal("2000"));
        reservation.setStatus(ReservationStatus.TENTATIVE);
        reservation.setServices(new ArrayList<>());
        reservation = reservations.saveAndFlush(reservation);
    }

    @Test
    void selectionIgnoresForgedPricesDescriptionsAndReservationIds() throws Exception {
        mvc.perform(post("/api/servicesselected/insert/list/" + reservation.getId())
                        .header("Authorization", token(client)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            [{"idService":%d,"priceAtReservation":0.01,"descriptionFrozen":"Manipulado",
                              "idReservation":999999,"id":999999}]
                            """.formatted(first.getId())))
                .andExpect(status().isOk());
        reload();
        assertMoney("2250", reservation.getFinalPrice());
        assertEquals(1, reservation.getServices().size());
        assertMoney("250", reservation.getServices().get(0).getPriceAtReservation());
        assertEquals("Proyector", reservation.getServices().get(0).getDescriptionFrozen());
    }

    @Test
    void oldPayloadWithoutCatalogIdIsRejected() throws Exception {
        mvc.perform(post("/api/servicesselected/insert/list/" + reservation.getId())
                        .header("Authorization", token(client)).contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"priceAtReservation\":1,\"descriptionFrozen\":\"Inventado\"}]"))
                .andExpect(status().isBadRequest());
        reload();
        assertTrue(reservation.getServices().isEmpty());
        assertMoney("2000", reservation.getFinalPrice());
    }

    @Test
    void addAndRemoveRecalculateTotalWithoutRepricingBookedBaseOrExistingExtras() {
        select(first.getId());
        em.flush();
        first.setPrice(new BigDecimal("999"));
        first.setDescription("Nuevo nombre");
        space.setBasePrice(new BigDecimal("5000"));
        em.flush();
        select(second.getId());
        reload();
        assertMoney("2375", reservation.getFinalPrice());
        ServiceSelected booked = reservation.getServices().stream()
                .filter(s -> "Proyector".equals(s.getDescriptionFrozen())).findFirst().orElseThrow();
        assertMoney("250", booked.getPriceAtReservation());
        selectedService.deleteServiceSelectedForAReservation(booked.getId());
        reload();
        assertMoney("2125", reservation.getFinalPrice());
        assertEquals(1, reservation.getServices().size());
        assertFalse(selections.existsById(booked.getId()));
        selectedService.deleteSelectedServiceByReserveId(reservation.getId());
        reload();
        assertMoney("2000", reservation.getFinalPrice());
        assertTrue(reservation.getServices().isEmpty());
    }

    @Test
    void invalidItemMakesTheWholeSelectionFail() {
        second.setIsActive(false);
        catalog.saveAndFlush(second);
        assertThrows(InvalidReservationException.class, () -> select(first.getId(), second.getId()));
        reload();
        assertTrue(reservation.getServices().isEmpty());
        assertMoney("2000", reservation.getFinalPrice());
    }

    @Test
    void serviceFromAnotherSpaceIsRejected() {
        Space foreign = new Space();
        foreign.setConsumerOwner(outsider);
        foreign.setIsActive(true);
        foreign = spaces.saveAndFlush(foreign);
        second.setSpace(foreign);
        catalog.saveAndFlush(second);
        assertThrows(InvalidReservationException.class, () -> select(second.getId()));
        reload();
        assertMoney("2000", reservation.getFinalPrice());
        assertTrue(reservation.getServices().isEmpty());
    }

    @Test
    void duplicateIdsAndUnknownServicesAreRejected() {
        assertThrows(InvalidReservationException.class, () -> select(first.getId(), first.getId()));
        assertThrows(IdNotFoundException.class, () -> select(Integer.MAX_VALUE));
        reload();
        assertTrue(reservation.getServices().isEmpty());
    }

    @ParameterizedTest
    @EnumSource(value = ReservationStatus.class, names = "TENTATIVE", mode = EnumSource.Mode.EXCLUDE)
    void nonEditableReservationsRejectAddingAndRemovingServices(ReservationStatus state) {
        select(first.getId());
        em.flush();
        Integer id = reservation.getServices().get(0).getId();
        reservation.setStatus(state);
        em.flush();
        assertThrows(InvalidReservationException.class, () -> select(second.getId()));
        assertThrows(InvalidReservationException.class, () -> selectedService.deleteServiceSelectedForAReservation(id));
        assertThrows(InvalidReservationException.class, () -> selectedService.deleteSelectedServiceByReserveId(reservation.getId()));
        reload();
        assertMoney("2250", reservation.getFinalPrice());
        assertEquals(1, reservation.getServices().size());
    }

    @Test
    void recordedPaymentPreventsServiceChanges() {
        PaymentModel payment = new PaymentModel();
        payment.setIdPayment(98765L);
        payment.setReservation(reservation);
        payment.setStatus("pending");
        payment.setTransactionAmount(reservation.getFinalPrice());
        payments.saveAndFlush(payment);
        assertThrows(InvalidReservationException.class, () -> select(first.getId()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void editingSpacePreservesStateOwnerPublicationAndRelationships(boolean active) throws Exception {
        space.setIsActive(active);
        spaces.saveAndFlush(space);
        SpaceImage image = images.saveAndFlush(new SpaceImage(null, space, "sala.jpg", "https://example.com/sala.jpg", LocalDateTime.now()));
        select(first.getId());
        mvc.perform(put("/api/spaces/ownedspace/" + space.getIdSpace())
                        .header("Authorization", token(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content(spaceBody(null)))
                .andExpect(status().isOk());
        reload();
        Space updated = spaces.findById(space.getIdSpace()).orElseThrow();
        assertEquals("Sala editada", updated.getNameSpace());
        assertEquals("Descripción editada", updated.getDescription());
        assertMoney("1500", updated.getBasePrice());
        assertEquals(45, updated.getBufferTime());
        assertEquals(active, updated.getIsActive());
        assertEquals(owner.getIdConsumer(), updated.getConsumerOwner().getIdConsumer());
        assertEquals(LocalDate.of(2025, 1, 1), updated.getPublicationDate());
        assertEquals(location.getIdLocation(), updated.getLocation().getIdLocation());
        assertEquals(EPolicyType.FLEXIBLE, updated.getCancellationPolicies().getType());
        assertEquals(2, updated.getServices().size());
        assertTrue(catalog.existsById(first.getId()));
        assertTrue(images.existsById(image.getIdSpaceImages()));
        assertEquals(updated.getIdSpace(), reservation.getSpace().getIdSpace());
        assertMoney("2250", reservation.getFinalPrice());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ownedspace/", ""})
    void neitherEditRouteTransfersOwnership(String route) throws Exception {
        mvc.perform(put("/api/spaces/" + route + space.getIdSpace())
                        .header("Authorization", token(route.isEmpty() ? admin : owner))
                        .contentType(MediaType.APPLICATION_JSON).content(spaceBody(outsider.getIdConsumer())))
                .andExpect(status().isBadRequest());
        reload();
        assertEquals(owner.getIdConsumer(), reservation.getSpace().getConsumerOwner().getIdConsumer());
        assertEquals("Sala original", reservation.getSpace().getNameSpace());
    }

    @Test
    void outsiderCannotEditOwnedSpace() throws Exception {
        mvc.perform(put("/api/spaces/ownedspace/" + space.getIdSpace())
                        .header("Authorization", token(outsider)).contentType(MediaType.APPLICATION_JSON)
                        .content(spaceBody(null)))
                .andExpect(status().isForbidden());
        reload();
        assertEquals("Sala original", reservation.getSpace().getNameSpace());
    }

    @Test
    void adminEditPreservesCatalogWithAnEmptyServicesList() throws Exception {
        mvc.perform(put("/api/spaces/" + space.getIdSpace())
                        .header("Authorization", token(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(spaceBody(owner.getIdConsumer()).replace("[{\"description\":\"No reemplazar\",\"price\":1}]", "[]")))
                .andExpect(status().isOk());
        reload();
        assertEquals("Sala editada", reservation.getSpace().getNameSpace());
        assertEquals(2, reservation.getSpace().getServices().size());
        assertTrue(reservation.getSpace().getIsActive());
    }

    @Test
    void invalidAdminEditReturnsBadRequestWithoutChangingSpace() throws Exception {
        mvc.perform(put("/api/spaces/" + space.getIdSpace())
                        .header("Authorization", token(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(spaceBody(null).replace("\"basePrice\":1500", "\"basePrice\":-1")))
                .andExpect(status().isBadRequest());
        reload();
        assertMoney("1000", reservation.getSpace().getBasePrice());
        assertEquals("Sala original", reservation.getSpace().getNameSpace());
    }

    private void select(Integer... ids) {
        selectedService.insertListOfServicesSelectedInAReservation(reservation.getId(),
                java.util.Arrays.stream(ids).map(SelectServiceDTO::new).toList());
    }

    private void reload() {
        Integer id = reservation.getId();
        em.flush();
        em.clear();
        reservation = reservations.findById(id).orElseThrow();
    }

    private Consumer consumer(String name, ERoles role) {
        Credential credential = new Credential();
        credential.setUsername(name);
        credential.setPassword("unused-test-password");
        credential.setRol(role);
        Consumer result = new Consumer();
        result.setFirstname(name);
        result.setCredentials(credential);
        return consumers.saveAndFlush(result);
    }

    private String token(Consumer actor) {
        return "Bearer " + jwt.generarToken(actor.getCredentials().getUsername(), actor.getCredentials().getRol().name(), actor.getIdConsumer());
    }

    private String spaceBody(Integer ownerId) {
        return """
                {"idConsumerOwner":%s,"nameSpace":"Sala editada","description":"Descripción editada",
                 "basePrice":1500,"bufferTime":45,"location":{"latitude":-34,"longitude":-58},
                 "cancellationPolicies":"FLEXIBLE","publicationDate":"2000-01-01","active":false,
                 "services":[{"description":"No reemplazar","price":1}]}
                """.formatted(ownerId);
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
