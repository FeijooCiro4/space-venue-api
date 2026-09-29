package com.utn.space.venueaapi;

import com.utn.space.venueaapi.exceptions.InvalidReservationException;
import com.utn.space.venueaapi.model.*;
import com.utn.space.venueaapi.model.records.ReservationDTO;
import com.utn.space.venueaapi.model.records.SelectServiceDTO;
import com.utn.space.venueaapi.service.ServiceSelectedService;
import com.utn.space.venueaapi.repository.*;
import com.utn.space.venueaapi.service.NotificationService;
import com.utn.space.venueaapi.service.ReservationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:reservation-concurrency;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
@ActiveProfiles("test")
class ReservationConcurrencyTests {
    @Autowired ReservationService service;
    @Autowired ServiceSelectedService selectionService;
    @Autowired SpaceServiceItemRepository catalog;
    @Autowired SpaceRepository spaces;
    @Autowired ConsumerRepository consumers;
    @Autowired ReservationRepository reservations;
    @Autowired NotificationRepository notifications;
    @Autowired ServiceSelectedRepository selectedServices;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoSpyBean NotificationService notificationService;
    ExecutorService executor;
    TransactionTemplate transaction;
    Integer firstSpaceId, secondSpaceId;
    LocalDateTime start;

    @BeforeEach
    void setup() {
        executor = Executors.newFixedThreadPool(2);
        transaction = new TransactionTemplate(transactionManager);
        start = LocalDateTime.now().plusDays(3).withNano(0);
        transaction.executeWithoutResult(status -> {
            notifications.deleteAllInBatch();
            selectedServices.deleteAllInBatch();
            reservations.deleteAllInBatch();
            spaces.deleteAll();
            consumers.deleteAll();
        });
        transaction.executeWithoutResult(status -> {
            Consumer owner = consumer("concurrent-owner");
            consumer("concurrent-client-a");
            consumer("concurrent-client-b");
            firstSpaceId = space(owner).getIdSpace();
            secondSpaceId = space(owner).getIdSpace();
        });
    }

    @AfterEach
    void cleanup() throws InterruptedException {
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        SecurityContextHolder.clearContext();
    }

    @Test
    void simultaneousCreatesForSameEmptySpaceProduceOnlyOneReservation() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        List<Future<Reservation>> futures = new ArrayList<>();
        // Mantener el mismo bloqueo de BD desde otra transacción permite comprobar
        // que ninguna creación puede saltarse la exclusión, aun sin reservas previas.
        transaction.executeWithoutResult(status -> {
            spaces.findByIdForUpdate(firstSpaceId).orElseThrow();
            futures.add(book("concurrent-client-a", firstSpaceId, ready));
            futures.add(book("concurrent-client-b", firstSpaceId, ready));
            try {
                assertTrue(ready.await(5, TimeUnit.SECONDS));
                for (Future<Reservation> future : futures) {
                    assertThrows(TimeoutException.class, () -> future.get(250, TimeUnit.MILLISECONDS));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        });
        int successes = 0;
        int unavailable = 0;
        for (Future<Reservation> future : futures) {
            try {
                assertEquals(ReservationStatus.TENTATIVE, future.get(10, TimeUnit.SECONDS).getStatus());
                successes++;
            } catch (ExecutionException e) {
                assertInstanceOf(InvalidReservationException.class, e.getCause());
                unavailable++;
            }
        }
        assertEquals(1, successes);
        assertEquals(1, unavailable);
        assertEquals(1, reservations.count());
        assertEquals(1, notifications.count());
    }

    @Test
    void lockingOneSpaceDoesNotBlockReservationsForAnother() {
        transaction.executeWithoutResult(status -> {
            spaces.findByIdForUpdate(firstSpaceId).orElseThrow();
            Future<Reservation> future = book("concurrent-client-a", secondSpaceId, new CountDownLatch(0));
            try {
                assertNotNull(future.get(5, TimeUnit.SECONDS).getId());
            } catch (Exception e) {
                throw new AssertionError("Reservar otro espacio no debe esperar este bloqueo", e);
            }
        });
        assertEquals(1, reservations.count());
    }

    @Test
    void notificationFailureRollsBackReservationAndReleasesSpaceLock() throws Exception {
        doThrow(new IllegalStateException("Fallo de notificación de prueba"))
                .when(notificationService).createNotification(any(Consumer.class), anyString());
        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> book("concurrent-client-a", firstSpaceId, new CountDownLatch(0)).get(5, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, failure.getCause());
        assertEquals(0, reservations.count());
        assertEquals(0, notifications.count());
        doCallRealMethod().when(notificationService).createNotification(any(Consumer.class), anyString());
        assertNotNull(book("concurrent-client-b", firstSpaceId, new CountDownLatch(0))
                .get(5, TimeUnit.SECONDS).getId());
        assertEquals(1, reservations.count());
    }

    @Test
    void simultaneousEditsCannotMoveTwoReservationsIntoTheSameSlot() throws Exception {
        Reservation first = book("concurrent-client-a", firstSpaceId, new CountDownLatch(0)).get(5, TimeUnit.SECONDS);
        Reservation second = book("concurrent-client-b", secondSpaceId, new CountDownLatch(0)).get(5, TimeUnit.SECONDS);
        Integer targetId = transaction.execute(status -> space(spaces.findById(firstSpaceId).orElseThrow().getConsumerOwner()).getIdSpace());
        CountDownLatch ready = new CountDownLatch(2);
        List<Future<Reservation>> tasks = new ArrayList<>();
        transaction.executeWithoutResult(status -> {
            spaces.findByIdForUpdate(targetId).orElseThrow();
            for (Reservation booked : List.of(first, second)) {
                tasks.add(executor.submit(() -> {
                    ready.countDown();
                    return service.modify(new ReservationDTO(booked.getId(), "Movida", "Edición concurrente",
                            start, start.plusHours(2), null, null, null, null,
                            booked.getConsumer().getIdConsumer(), targetId, List.of()));
                }));
            }
            try {
                assertTrue(ready.await(5, TimeUnit.SECONDS));
                for (Future<?> task : tasks) {
                    assertThrows(TimeoutException.class, () -> task.get(250, TimeUnit.MILLISECONDS));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        });
        int succeeded = 0;
        for (Future<Reservation> task : tasks) {
            try {
                task.get(10, TimeUnit.SECONDS);
                succeeded++;
            } catch (ExecutionException e) {
                assertInstanceOf(InvalidReservationException.class, e.getCause());
            }
        }
        assertEquals(1, succeeded);
        assertEquals(1, reservations.findAllBySpace_IdSpace(targetId).size());
    }

    @Test
    void simultaneousServiceAdditionsKeepBothSelectionsAndTheCorrectTotal() throws Exception {
        Reservation booked = book("concurrent-client-a", firstSpaceId, new CountDownLatch(0)).get(5, TimeUnit.SECONDS);
        List<Integer> ids = transaction.execute(status -> {
            Space space = spaces.findById(firstSpaceId).orElseThrow();
            return List.of(
                    catalog.saveAndFlush(new SpaceServiceItem(null, "Proyector", new BigDecimal("250"), true, space)).getId(),
                    catalog.saveAndFlush(new SpaceServiceItem(null, "Sonido", new BigDecimal("125"), true, space)).getId());
        });
        CountDownLatch ready = new CountDownLatch(2);
        List<Future<?>> tasks = new ArrayList<>();
        transaction.executeWithoutResult(status -> {
            service.findByIdForUpdate(booked.getId());
            for (Integer id : ids) {
                tasks.add(executor.submit(() -> {
                    ready.countDown();
                    selectionService.insertListOfServicesSelectedInAReservation(booked.getId(), List.of(new SelectServiceDTO(id)));
                }));
            }
            try {
                assertTrue(ready.await(5, TimeUnit.SECONDS));
                for (Future<?> task : tasks) {
                    assertThrows(TimeoutException.class, () -> task.get(250, TimeUnit.MILLISECONDS));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        });
        for (Future<?> task : tasks) task.get(10, TimeUnit.SECONDS);
        transaction.executeWithoutResult(status -> {
            Reservation result = service.findById(booked.getId());
            assertEquals(2, result.getServices().size());
            assertEquals(0, new BigDecimal("2375").compareTo(result.getFinalPrice()));
        });
    }

    private Future<Reservation> book(String username, Integer spaceId, CountDownLatch ready) {
        return executor.submit(() -> {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    username, null, List.of(new SimpleGrantedAuthority("ROLE_CLIENT"))));
            ready.countDown();
            try {
                return service.create(new ReservationDTO(null, "Reunión", "Prueba concurrente", start,
                        start.plusHours(2), null, null, null, null, null, spaceId, List.of()));
            } finally {
                SecurityContextHolder.clearContext();
            }
        });
    }

    private Consumer consumer(String username) {
        Credential credential = new Credential();
        credential.setUsername(username);
        credential.setPassword("unused-test-password");
        Consumer consumer = new Consumer();
        consumer.setCredentials(credential);
        consumer.setFirstname(username);
        consumer.setLastname("Prueba");
        return consumers.saveAndFlush(consumer);
    }

    private Space space(Consumer owner) {
        Space space = new Space();
        space.setConsumerOwner(owner);
        space.setNameSpace("Sala concurrente");
        space.setBasePrice(new BigDecimal("1000"));
        space.setIsActive(true);
        space.setBufferTime(30);
        space.setServices(new ArrayList<>());
        return spaces.saveAndFlush(space);
    }
}
