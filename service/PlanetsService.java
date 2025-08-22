package com.zenika.bzhcamp.keycloak.appspringboot.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class PlanetsService {

    @Autowired
    private WebClient webClient;

    @Value("${planets.service.url}")
    private String endpoint;

    public List<String> getPlanets() {

        List<String> body = webClient
                .get()
                .uri(endpoint)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<String>>() {
                })
                .block();

        return body;
    }

}
