package com.utn.space.venueaapi;

import com.utn.space.venueaapi.exceptions.InvalidDataException;
import com.utn.space.venueaapi.model.ERoles;
import com.utn.space.venueaapi.model.records.RegistroDTO;
import com.utn.space.venueaapi.repository.ConsumerRepository;
import com.utn.space.venueaapi.repository.CredentialRepository;
import com.utn.space.venueaapi.service.CredentialService;
import com.utn.space.venueaapi.service.RegistrationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:registration-tests;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegistrationTests {
    @Autowired MockMvc mvc;
    @Autowired CredentialRepository credentials;
    @Autowired ConsumerRepository consumers;
    @Autowired PasswordEncoder encoder;
    @Autowired RegistrationService registration;
    @MockitoSpyBean CredentialService credentialService;

    @ParameterizedTest
    @ValueSource(strings = {"/api/usuarios", "/api/auth/register"})
    void eitherRegistrationRouteCreatesAnAccountThatCanLogIn(String route) throws Exception {
        String username = username();
        String password = "Original-password-123";
        mvc.perform(post(route).contentType(MediaType.APPLICATION_JSON).content(payload(username, password)))
                .andExpect(status().isCreated());
        var saved = credentials.findById(username).orElseThrow();
        assertNotEquals(password, saved.getPassword());
        assertTrue(encoder.matches(password, saved.getPassword()));
        assertEquals(ERoles.ROLE_CLIENT, saved.getRol());
        assertTrue(saved.getIsActive());
        var consumer = consumers.findByUsername(username).orElseThrow();
        if (route.endsWith("register")) {
            assertEquals("Ana", consumer.getFirstname());
            assertEquals("ana@example.com", consumer.getEmail());
        } else {
            assertEquals("", consumer.getFirstname());
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("Bearer ")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/usuarios", "/api/auth/register"})
    void duplicateRegistrationCannotOverwriteThePasswordOrProfile(String originalRoute) throws Exception {
        String username = username();
        mvc.perform(post(originalRoute).contentType(MediaType.APPLICATION_JSON).content(payload(username, "Original-password-123")))
                .andExpect(status().isCreated());
        String originalHash = credentials.findById(username).orElseThrow().getPassword();
        Integer consumerId = consumers.findByUsername(username).orElseThrow().getIdConsumer();
        for (String route : List.of("/api/usuarios", "/api/auth/register")) {
            mvc.perform(post(route).contentType(MediaType.APPLICATION_JSON).content(payload(username, "Replacement-password-123")))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(originalHash, credentials.findById(username).orElseThrow().getPassword());
        assertEquals(consumerId, consumers.findByUsername(username).orElseThrow().getIdConsumer());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/usuarios", "/api/auth/register"})
    void missingOrBlankCredentialsDoNotCreateAnAccount(String route) throws Exception {
        long before = credentials.count();
        for (String body : List.of("{}", "{\"username\":\"\",\"password\":\"password\"}",
                "{\"username\":\"missing-password\",\"password\":\"   \"}")) {
            mvc.perform(post(route).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(before, credentials.count());
    }

    @Test
    void profilePersistenceFailureRollsBackTheCredentialInsert() {
        String username = username();
        long before = consumers.count();
        // Consumer.firstname es VARCHAR(255). Forzar un fallo de BD después de persistir la credencial.
        assertThrows(InvalidDataException.class, () -> registration.register(new RegistroDTO(
                "a".repeat(300), "Pérez", "ana@example.com", "+541112345678", username, "Original-password-123")));
        assertFalse(credentials.existsByUsername(username));
        assertTrue(consumers.findByUsername(username).isEmpty());
        assertEquals(before, consumers.count());
    }

    @Test
    void concurrentRegistrationsWithSameUsernameCannotReplaceTheWinningAccount() throws Exception {
        String username = username();
        CountDownLatch checked = new CountDownLatch(2);
        doAnswer(invocation -> {
            checked.countDown();
            assertTrue(checked.await(5, TimeUnit.SECONDS));
            return false; // Ambas solicitudes observaron libre el mismo nombre.
        }).when(credentialService).existsByUsername(username);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<String> passwords = List.of("First-password-123", "Second-password-123");
            List<Future<Integer>> tasks = passwords.stream().map(password -> executor.submit(() ->
                    registration.register(new RegistroDTO("Ana", "Pérez", "ana@example.com", "12345678", username, password)).getIdConsumer())).toList();
            int successes = 0;
            for (int i = 0; i < tasks.size(); i++) {
                try {
                    Integer id = tasks.get(i).get(10, TimeUnit.SECONDS);
                    successes++;
                    assertEquals(id, consumers.findByUsername(username).orElseThrow().getIdConsumer());
                    assertTrue(encoder.matches(passwords.get(i), credentials.findById(username).orElseThrow().getPassword()));
                } catch (ExecutionException e) {
                    assertInstanceOf(InvalidDataException.class, e.getCause());
                }
            }
            assertEquals(1, successes);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private String username() {
        return "reg-" + UUID.randomUUID().toString().substring(0, 20);
    }

    private String payload(String username, String password) {
        return """
                {"username":"%s","password":"%s","firstname":"Ana","lastname":"Pérez",
                 "email":"ana@example.com","phone":"+541112345678","rol":"ROLE_ADMIN","isActive":false}
                """.formatted(username, password);
    }
}
