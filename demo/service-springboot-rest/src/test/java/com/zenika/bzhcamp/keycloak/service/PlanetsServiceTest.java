package com.zenika.bzhcamp.keycloak.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlanetsServiceTest {

    private final PlanetsService planetsService = new PlanetsService();

    @Test
    void getPlanets_shouldReturnNonEmptyList() {
        List<String> planets = planetsService.getPlanets();

        assertThat(planets).isNotEmpty();
    }

    @Test
    void getPlanets_shouldContainKnownPlanets() {
        List<String> planets = planetsService.getPlanets();

        assertThat(planets).contains("Tatooine", "Naboo", "Coruscant");
    }

    @Test
    void getPlanets_shouldReturnSameListEachTime() {
        List<String> first = planetsService.getPlanets();
        List<String> second = planetsService.getPlanets();

        assertThat(first).isEqualTo(second);
    }
}