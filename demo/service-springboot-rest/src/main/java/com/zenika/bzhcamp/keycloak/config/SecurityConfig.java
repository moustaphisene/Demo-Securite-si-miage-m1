package com.zenika.bzhcamp.keycloak.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.DelegatingJwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	public SecurityFilterChain clientFilterChain(HttpSecurity http) throws Exception {

		DelegatingJwtGrantedAuthoritiesConverter authoritiesConverter =
				// Using the delegating converter multiple converters can be combined
				new DelegatingJwtGrantedAuthoritiesConverter(
						// First add the default converter
						new JwtGrantedAuthoritiesConverter(),
						// Second add our custom Keycloak specific converter
						new KeycloakJwtRolesConverter());

		http.cors(Customizer.withDefaults());

		http.authorizeHttpRequests(
				authorize -> authorize
						.requestMatchers(new AntPathRequestMatcher("/public")).permitAll()
						.requestMatchers(new AntPathRequestMatcher("/swagger-ui/**")).permitAll()
						.requestMatchers(new AntPathRequestMatcher("/v3/api-docs/**")).permitAll()
						.requestMatchers(new AntPathRequestMatcher("/planets"))
						.hasAuthority(KeycloakJwtRolesConverter.PREFIX_RESOURCE_ROLE + "realm_user")
						.anyRequest().authenticated());

		http.oauth2ResourceServer(
				oAuth2ResourceServerConfigurer -> oAuth2ResourceServerConfigurer.jwt(
						jwtConfigurer -> jwtConfigurer.jwtAuthenticationConverter(
								jwt -> new JwtAuthenticationToken(jwt, authoritiesConverter.convert(jwt)))));

		http.sessionManagement((sessionManagement) -> sessionManagement
				.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

		return http.build();
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(List.of(
				"http://localhost:8090",
				"http://localhost:4200",
				"http://localhost:8081"
		));
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}
}
