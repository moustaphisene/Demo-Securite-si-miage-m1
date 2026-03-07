package com.zenika.bzhcamp.keycloak.service;

import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PlanetsService {

    public List<String> getPlanets() {
        return Arrays.asList(
                "Tatooine",
                "Alderaan",
                "Yavin IV",
                "Hoth",
                "Dagobah",
                "Bespin",
                "Endor",
                "Naboo",
                "Coruscant",
                "Kamino2");
    }

}
