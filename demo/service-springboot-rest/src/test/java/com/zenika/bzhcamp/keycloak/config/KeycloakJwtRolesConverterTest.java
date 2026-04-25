package com.zenika.bzhcamp.keycloak.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakJwtRolesConverterTest {

    private KeycloakJwtRolesConverter converter;

    @BeforeEach
    void setUp() {
        converter = new KeycloakJwtRolesConverter();
    }

    @Test
    void convert_shouldExtractRealmRoles() {
        Jwt jwt = buildJwt(
                Map.of("roles", List.of("admin", "user")),
                Map.of()
        );

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_realm_admin", "ROLE_realm_user");
    }

    @Test
    void convert_shouldExtractResourceRoles() {
        Jwt jwt = buildJwt(
                Map.of(),
                Map.of("my-client", Map.of("roles", List.of("read", "write")))
        );

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_my-client_read", "ROLE_my-client_write");
    }

    @Test
    void convert_shouldReturnEmpty_whenNoRoles() {
        Jwt jwt = buildJwt(Map.of(), Map.of());

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities).isEmpty();
    }

    @Test
    void convert_shouldExtractBothRealmAndResourceRoles() {
        Jwt jwt = buildJwt(
                Map.of("roles", List.of("admin")),
                Map.of("app-client", Map.of("roles", List.of("viewer")))
        );

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_realm_admin", "ROLE_app-client_viewer");
    }

    private Jwt buildJwt(Map<String, Object> realmAccess, Map<String, Object> resourceAccess) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("realm_access", realmAccess.isEmpty() ? null : realmAccess)
                .claim("resource_access", resourceAccess.isEmpty() ? null : resourceAccess)
                .build();
    }
}