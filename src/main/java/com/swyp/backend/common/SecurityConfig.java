package com.swyp.backend.common;

import com.swyp.backend.common.security.JwtAuthenticationFilter;
import com.swyp.backend.common.security.JwtProperties;
import com.swyp.backend.common.security.JwtTokenProvider;
import com.swyp.backend.common.security.RestAuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Value;
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

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
@EnableWebSecurity
public class SecurityConfig {

	private static final String[] PUBLIC_ENDPOINTS = {
		"/ping",
		"/admin/auth/login",
		"/admin/auth/refresh",
		"/admin/auth/logout",
		"/recipes",
		"/recipes/**",
	};

	private static final String[] API_DOCS_ENDPOINTS = {
		"/v3/api-docs",
		"/v3/api-docs/**",
		"/swagger-ui/**",
		"/swagger-ui.html",
	};

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http, JwtTokenProvider tokenProvider,
			RestAuthenticationEntryPoint authenticationEntryPoint,
			@Value("${springdoc.api-docs.enabled:true}") boolean apiDocsEnabled) throws Exception {
		http
			.csrf(csrf -> csrf.disable())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> {
				auth.requestMatchers(PUBLIC_ENDPOINTS).permitAll();
				if (apiDocsEnabled) {
					auth.requestMatchers(API_DOCS_ENDPOINTS).permitAll();
				}
				auth.anyRequest().authenticated();
			})
			.exceptionHandling(exception -> exception.authenticationEntryPoint(authenticationEntryPoint))
			.addFilterBefore(new JwtAuthenticationFilter(tokenProvider), UsernamePasswordAuthenticationFilter.class)
			.httpBasic(basic -> basic.disable())
			.formLogin(form -> form.disable());
		return http.build();
	}
}
