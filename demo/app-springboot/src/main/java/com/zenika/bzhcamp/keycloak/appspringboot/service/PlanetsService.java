package com.zenika.bzhcamp.keycloak.appspringboot.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class PlanetsService {

    private final WebClient webClient;
    private final String endpoint;

    /**
     * Injection par constructeur : @Value injecte l'URL depuis application.yml (planets.service.url).
     * Le WebClient est configuré avec le token OAuth2 (voir SecurityConfig) et le propage
     * automatiquement à chaque requête vers le service de planètes.
     */
    public PlanetsService(WebClient webClient, @Value("${planets.service.url}") String endpoint) {
        this.webClient = webClient;
        this.endpoint = endpoint;
    }

    public List<String> getPlanets() {
        return webClient
                .get()
                .uri(endpoint)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<String>>() {})
                .block();
    }
}