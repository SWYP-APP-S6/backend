package com.swyp.backend.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Interim security for the pre-auth phase. The app is a stateless REST API with no login yet, so
 * every request is permitted and CSRF/basic/form login are disabled — otherwise Spring Security's
 * default chain locks all endpoints behind a generated password, which would 401 even a plain
 * {@code GET /ping} (breaking the "run it and curl" completion check).
 *
 * <p>TODO: when the auth model is chosen (see CLAUDE.md "Auth"), replace this with real authn/authz —
 * flip to {@code anyRequest().authenticated()} and permit only genuinely public paths.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
			.csrf(csrf -> csrf.disable())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
			.httpBasic(basic -> basic.disable())
			.formLogin(form -> form.disable());
		return http.build();
	}
}
