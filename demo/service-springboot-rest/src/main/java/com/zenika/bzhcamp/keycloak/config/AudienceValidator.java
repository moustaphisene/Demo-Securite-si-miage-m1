package com.zenika.bzhcamp.keycloak.config;

import java.util.List;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Valide que le JWT a bien été émis pour CE service, en vérifiant le claim "aud" (audience).
 *
 * <p>Sans cette vérification, un serveur de ressources accepte tout jeton signé par le realm,
 * même un jeton obtenu pour un autre client/service. Un attaquant disposant d'un jeton légitime
 * destiné au service A peut alors le rejouer contre le service B : c'est l'attaque du
 * « député confus » (confused deputy).</p>
 *
 * <p>Pour que ce contrôle fonctionne, Keycloak doit ajouter l'audience attendue dans le jeton
 * (via un « Audience mapper » ou un client scope dédié). Le contrôle est donc activé de façon
 * <b>opt-in</b> dans {@code SecurityConfig} : il ne s'applique que si la propriété
 * {@code keycloak.audience} est renseignée.</p>
 */
public class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private final String expectedAudience;

    public AudienceValidator(String expectedAudience) {
        this.expectedAudience = expectedAudience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        List<String> audiences = jwt.getAudience();
        if (audiences != null && audiences.contains(expectedAudience)) {
            return OAuth2TokenValidatorResult.success();
        }
        OAuth2Error error = new OAuth2Error(
                "invalid_token",
                "L'audience attendue (" + expectedAudience + ") est absente du claim 'aud' du jeton",
                null);
        return OAuth2TokenValidatorResult.failure(error);
    }
}