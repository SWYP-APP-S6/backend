package com.swyp.backend.common;

import com.swyp.backend.common.security.JwtAuthenticationFilter;
import com.swyp.backend.common.security.JwtProperties;
import com.swyp.backend.common.security.JwtTokenProvider;
import com.swyp.backend.common.security.RestAuthenticationEntryPoint;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless JWT security. Public: health/ping + admin auth endpoints (login/refresh/logout); every
 * other request needs a valid bearer access token. {@link JwtAuthenticationFilter} populates the
 * context from the token and {@link RestAuthenticationEntryPoint} renders 401s as the standard error
 * envelope. CSRF/basic/form login are off (token-based API, no browser session).
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
@EnableWebSecurity
public class SecurityConfig {

	private static final String[] PUBLIC_ENDPOINTS = {
		"/ping",
		"/admin/auth/login",
		"/admin/auth/refresh",
		"/admin/auth/logout",
	};

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http, JwtTokenProvider tokenProvider,
			RestAuthenticationEntryPoint authenticationEntryPoint) throws Exception {
		http
			.csrf(csrf -> csrf.disable())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(PUBLIC_ENDPOINTS).permitAll()
				.anyRequest().authenticated())
			.exceptionHandling(exception -> exception.authenticationEntryPoint(authenticationEntryPoint))
			.addFilterBefore(new JwtAuthenticationFilter(tokenProvider), UsernamePasswordAuthenticationFilter.class)
			.httpBasic(basic -> basic.disable())
			.formLogin(form -> form.disable());
		return http.build();
	}
}
