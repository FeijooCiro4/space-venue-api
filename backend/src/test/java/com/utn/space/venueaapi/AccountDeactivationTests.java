package com.utn.space.venueaapi;

import com.utn.space.venueaapi.model.Consumer;
import com.utn.space.venueaapi.model.Credential;
import com.utn.space.venueaapi.model.ERoles;
import com.utn.space.venueaapi.repository.ConsumerRepository;
import com.utn.space.venueaapi.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AccountDeactivationTests {
    private static final String PASSWORD = "test-password-123";
    @Autowired MockMvc mvc;
    @Autowired ConsumerRepository consumers;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtUtil jwtUtil;

    @Test
    void activeAccountCanLoginAndUseIssuedToken() throws Exception {
        Consumer consumer = createConsumer("active-account", ERoles.ROLE_CLIENT, true);
        String token = login(consumer);
        mvc.perform(get("/api/reservations/me").header("Authorization", token))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(consumer, "incorrect-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Credenciales inválidas"));
    }

    @ParameterizedTest
    @EnumSource(ERoles.class)
    void disabledAccountCannotLoginEvenWithCorrectPassword(ERoles role) throws Exception {
        Consumer consumer = createConsumer("disabled-account", role, false);
        rejectedLogin(consumer);
        mvc.perform(get("/api/spaces")).andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(ERoles.class)
    void selfDeactivationBlocksPreviouslyIssuedTokensAndNewLogins(ERoles role) throws Exception {
        Consumer consumer = createConsumer("self-deactivation", role, true);
        String token = login(consumer);
        mvc.perform(get("/api/reservations/me").header("Authorization", token))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/usuario").header("Authorization", token))
                .andExpect(status().isOk());
        assertFalse(consumers.findById(consumer.getIdConsumer()).orElseThrow().getCredentials().getIsActive());
        assertTrue(jwtUtil.validarToken(token.substring(7), consumer.getCredentials().getUsername()));
        assertBlocked(token);
        rejectedLogin(consumer);
    }

    @Test
    void adminDeactivationBlocksOtherUserAndKeepsAdminSessionActive() throws Exception {
        Consumer admin = createConsumer("deactivation-admin", ERoles.ROLE_ADMIN, true);
        Consumer consumer = createConsumer("deactivation-client", ERoles.ROLE_CLIENT, true);
        String adminToken = login(admin);
        String clientToken = login(consumer);
        mvc.perform(get("/api/reservations/me").header("Authorization", clientToken))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/usuarios/" + consumer.getIdConsumer()).header("Authorization", adminToken))
                .andExpect(status().isOk());
        assertFalse(consumers.findById(consumer.getIdConsumer()).orElseThrow().getCredentials().getIsActive());
        assertBlocked(clientToken);
        rejectedLogin(consumer);
        mvc.perform(get("/api/usuarios").header("Authorization", adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void signedTokenForMissingAccountIsRejected() throws Exception {
        String token = "Bearer " + jwtUtil.generarToken("missing-account", "ROLE_ADMIN", 123);
        assertBlocked(token);
    }

    private void assertBlocked(String token) throws Exception {
        // Incluye una lectura, una escritura y una ruta que anteriormente bastaba con autenticar.
        mvc.perform(get("/api/reservations/me").header("Authorization", token))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/usuario").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"firstname\":\"No permitido\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/servicesselected/reservation/1").header("Authorization", token))
                .andExpect(status().isUnauthorized());
    }

    private String login(Consumer consumer) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(consumer, PASSWORD)))
                .andExpect(status().isOk()).andExpect(content().string(startsWith("Bearer ")))
                .andReturn().getResponse().getContentAsString();
    }

    private void rejectedLogin(Consumer consumer) throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(consumer, PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Credenciales inválidas"));
    }

    private String loginBody(Consumer consumer, String password) {
        return "{\"username\":\"%s\",\"password\":\"%s\"}"
                .formatted(consumer.getCredentials().getUsername(), password);
    }

    private Consumer createConsumer(String username, ERoles role, boolean active) {
        Credential credential = new Credential();
        credential.setUsername(username);
        credential.setPassword(passwordEncoder.encode(PASSWORD));
        credential.setRol(role);
        credential.setIsActive(active);
        Consumer consumer = new Consumer();
        consumer.setFirstname(username);
        consumer.setLastname("Prueba");
        consumer.setCredentials(credential);
        return consumers.saveAndFlush(consumer);
    }
}
