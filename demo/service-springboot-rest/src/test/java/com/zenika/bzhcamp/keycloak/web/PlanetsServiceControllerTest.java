package com.zenika.bzhcamp.keycloak.web;

import com.zenika.bzhcamp.keycloak.service.PlanetsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({PlanetsServiceController.class, CommonServiceController.class})
class PlanetsServiceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlanetsService planetsService;

    @Test
    @WithMockUser(authorities = "ROLE_realm_user")
    void getPlanets_shouldReturn200_whenAuthenticated() throws Exception {
        when(planetsService.getPlanets()).thenReturn(List.of("Tatooine", "Naboo"));

        mockMvc.perform(get("/planets"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$[0]").value("Tatooine"))
                .andExpect(jsonPath("$[1]").value("Naboo"));
    }

    @Test
    void getPlanets_shouldReturn401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/planets"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getPublic_shouldReturn200_withoutAuth() throws Exception {
        mockMvc.perform(get("/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("public"));
    }
}