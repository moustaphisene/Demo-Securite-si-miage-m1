package com.zenika.bzhcamp.keycloak.appspringboot.config;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;
import org.springframework.security.oauth2.server.resource.web.reactive.function.client.ServletBearerExchangeFilterFunction;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuration de la sécurité de l'application.
 *
 * Cette application joue deux rôles simultanément :
 *  - CLIENT OAuth2 : elle redirige l'utilisateur vers Keycloak pour s'authentifier (oauth2Login)
 *  - RESOURCE SERVER : elle accepte des JWT Bearer tokens pour les appels API directs (oauth2ResourceServer)
 *
 * @EnableMethodSecurity active les annotations @PreAuthorize / @PostAuthorize sur les méthodes,
 * permettant un contrôle d'accès fin par rôle (ex. @PreAuthorize("hasRole('admin')")).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig {

    // Noms des claims Keycloak contenant les rôles
    private static final String GROUPS = "groups";
    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String ROLES_CLAIM = "roles";

    private final KeycloakLogoutHandler keycloakLogoutHandler;

    SecurityConfig(KeycloakLogoutHandler keycloakLogoutHandler) {
        this.keycloakLogoutHandler = keycloakLogoutHandler;
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    protected SessionAuthenticationStrategy sessionAuthenticationStrategy() {
        return new RegisterSessionAuthenticationStrategy(sessionRegistry());
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    /**
     * Définit les règles d'autorisation HTTP et configure les deux modes de sécurité :
     *  1. oauth2Login  → flux navigateur : redirige vers Keycloak, reçoit un code d'autorisation
     *  2. oauth2ResourceServer(jwt) → flux API : valide le JWT Bearer token directement
     */
    @Bean
    public SecurityFilterChain resourceServerFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                // Autorise les requêtes preflight CORS (OPTIONS) sans authentification
                .requestMatchers(new AntPathRequestMatcher("/planets*", HttpMethod.OPTIONS.name()))
                .permitAll()
                // /planets nécessite une authentification (contrôle fin possible avec @PreAuthorize)
                .requestMatchers(new AntPathRequestMatcher("/planets*"))
                .authenticated()
                // Les autres routes racines sont publiques (landing, about)
                .requestMatchers(new AntPathRequestMatcher("/*"))
                .permitAll()
                .anyRequest()
                .authenticated());

        // Mode Resource Server : valide les JWT émis par Keycloak (pour les appels API)
        http.oauth2ResourceServer(oauth2 -> oauth2
                .jwt(Customizer.withDefaults()));

        // Mode Client OAuth2 : gère le flux de login/logout navigateur via Keycloak
        http.oauth2Login(Customizer.withDefaults())
                .logout(logout -> logout
                        .addLogoutHandler(keycloakLogoutHandler)
                        .logoutSuccessUrl("/"));

        return http.build();
    }

    /**
     * Mappe les rôles Keycloak vers les GrantedAuthority Spring Security.
     *
     * Keycloak place les rôles dans le claim "realm_access.roles" (ou "groups").
     * Spring Security préfixe les rôles avec "ROLE_" pour les utiliser avec hasRole().
     *
     * Exemple : rôle Keycloak "admin" → autorité Spring "ROLE_admin"
     *           → utilisable via @PreAuthorize("hasRole('admin')")
     */
    @Bean
    public GrantedAuthoritiesMapper userAuthoritiesMapperForKeycloak() {
        return authorities -> {
            Set<GrantedAuthority> mappedAuthorities = new HashSet<>();
            var authority = authorities.iterator().next();
            boolean isOidc = authority instanceof OidcUserAuthority;

            if (isOidc) {
                // Cas OAuth2 Login : l'utilisateur s'est connecté via le navigateur
                var oidcUserAuthority = (OidcUserAuthority) authority;
                var userInfo = oidcUserAuthority.getUserInfo();

                if (userInfo.hasClaim(REALM_ACCESS_CLAIM)) {
                    var realmAccess = userInfo.getClaimAsMap(REALM_ACCESS_CLAIM);
                    var roles = (Collection<String>) realmAccess.get(ROLES_CLAIM);
                    mappedAuthorities.addAll(generateAuthoritiesFromClaim(roles));
                } else if (userInfo.hasClaim(GROUPS)) {
                    Collection<String> roles = userInfo.getClaim(GROUPS);
                    mappedAuthorities.addAll(generateAuthoritiesFromClaim(roles));
                }
            } else {
                // Cas Resource Server : l'utilisateur envoie un Bearer token
                var oauth2UserAuthority = (OAuth2UserAuthority) authority;
                Map<String, Object> userAttributes = oauth2UserAuthority.getAttributes();

                if (userAttributes.containsKey(REALM_ACCESS_CLAIM)) {
                    Map<String, Object> realmAccess = (Map<String, Object>) userAttributes.get(REALM_ACCESS_CLAIM);
                    Collection<String> roles = (Collection<String>) realmAccess.get(ROLES_CLAIM);
                    mappedAuthorities.addAll(generateAuthoritiesFromClaim(roles));
                }
            }
            return mappedAuthorities;
        };
    }

    Collection<GrantedAuthority> generateAuthoritiesFromClaim(Collection<String> roles) {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());
    }

    /**
     * WebClient configuré pour propager automatiquement le token OAuth2 de l'utilisateur connecté
     * lors des appels vers des services aval (ici : le service planets sur le port 8091).
     *
     * Mode OAuth2 Client (navigateur) :
     *   ServletOAuth2AuthorizedClientExchangeFilterFunction récupère le token stocké en session
     *   et l'ajoute en header Authorization: Bearer <token>.
     *
     * Alternative — Mode Resource Server (propagation du token entrant) :
     *   Si cette app reçoit elle-même un Bearer token et veut le transmettre tel quel :
     *   utiliser ServletBearerExchangeFilterFunction à la place.
     *   Exemple :
     *     return WebClient.builder()
     *         .filter(new ServletBearerExchangeFilterFunction())
     *         .build();
     */
    @Bean
    WebClient webClient(OAuth2AuthorizedClientManager authorizedClientManager) {
        ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2Client =
                new ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
        oauth2Client.setDefaultClientRegistrationId("keycloak");
        return WebClient.builder()
                .apply(oauth2Client.oauth2Configuration())
                .build();
    }
}