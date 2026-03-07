package com.zenika.bzhcamp.keycloak.appspringboot.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Gère la déconnexion côté Keycloak (back-channel logout).
 *
 * Lorsqu'un utilisateur se déconnecte de l'application Spring Boot, il faut aussi
 * invalider sa session chez Keycloak, sinon il resterait connecté sur d'autres apps
 * du même realm.
 *
 * Mécanisme : appel HTTP GET sur l'endpoint /protocol/openid-connect/logout
 * avec le paramètre id_token_hint (le token ID de l'utilisateur).
 */
@Component
public class KeycloakLogoutHandler implements LogoutHandler {

    private static final Logger logger = LoggerFactory.getLogger(KeycloakLogoutHandler.class);

    private final RestTemplate restTemplate;

    /**
     * Le RestTemplate est injecté via le bean défini dans SecurityConfig.
     * On évite new RestTemplate() qui contourne la gestion Spring (intercepteurs, métriques, etc.).
     */
    public KeycloakLogoutHandler(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication auth) {
        logoutFromKeycloak((OidcUser) auth.getPrincipal());
    }

    private void logoutFromKeycloak(OidcUser user) {
        // L'endpoint de déconnexion est fourni par le serveur OIDC dans son well-known
        String endSessionEndpoint = user.getIssuer() + "/protocol/openid-connect/logout";

        // id_token_hint permet à Keycloak d'identifier la session à invalider
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(endSessionEndpoint)
                .queryParam("id_token_hint", user.getIdToken().getTokenValue());

        ResponseEntity<String> logoutResponse = restTemplate.getForEntity(builder.toUriString(), String.class);

        if (logoutResponse.getStatusCode().is2xxSuccessful()) {
            logger.info("Successfully logged out from Keycloak");
        } else {
            logger.error("Could not propagate logout to Keycloak");
        }
    }
}