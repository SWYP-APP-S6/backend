package com.swyp.backend.common;

import com.swyp.backend.common.security.JwtAuthenticationFilter;
import com.swyp.backend.common.security.JwtProperties;
import com.swyp.backend.common.security.JwtTokenProvider;
import com.swyp.backend.common.security.RateLimitFilter;
import com.swyp.backend.common.security.RateLimitProperties;
import com.swyp.backend.common.security.RateLimiter;
import com.swyp.backend.common.security.RestAccessDeniedHandler;
import com.swyp.backend.common.security.RestAuthenticationEntryPoint;
import com.swyp.backend.common.security.TokenRealm;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, RateLimitProperties.class})
@EnableWebSecurity
public class SecurityConfig {

	private static final String OWNER_ROLE = "OWNER";
	private static final String CONSUMER_ROLE = "CONSUMER";

	private static final String[] PUBLIC_ENDPOINTS = {
		"/ping",
		"/admin/auth/login",
		"/admin/auth/refresh",
		"/admin/auth/logout",
		"/auth/guest",
		"/auth/consumer/kakao",
		"/auth/consumer/kakao/exchange",
		"/auth/owner/kakao",
		"/auth/owner/kakao/exchange",
		"/auth/signup",
		"/auth/refresh",
		"/auth/logout",
	};

	private static final String[] CONSUMER_ENDPOINTS = {
		"/holds",
		"/holds/**",
	};

	private static final String[] APP_USER_ENDPOINTS = {
		"/users/me",
		"/notifications",
		"/notifications/device-tokens",
		"/notifications/read-all",
		"/notifications/*/read",
	};

	private static final String[] BROWSE_ENDPOINTS = {
		"/recipes",
		"/recipes/**",
		"/products/nearby",
		"/products/*",
		"/stores/nearby",
		"/stores/*/products",
	};

	private static final String[] DEV_ENDPOINTS = {
		"/dev/**",
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
	CorsConfigurationSource corsConfigurationSource(
			@Value("${cors.allowed-origins}") List<String> allowedOrigins) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(allowedOrigins);
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		configuration.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http, JwtTokenProvider tokenProvider,
			RateLimiter rateLimiter, ObjectMapper objectMapper,
			RestAuthenticationEntryPoint authenticationEntryPoint,
			RestAccessDeniedHandler accessDeniedHandler,
			@Value("${springdoc.api-docs.enabled:true}") boolean apiDocsEnabled,
			@Value("${dev.test-token.enabled:false}") boolean devTestTokenEnabled) throws Exception {
		JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(tokenProvider);
		http
			.cors(Customizer.withDefaults())
			.csrf(csrf -> csrf.disable())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> {
				auth.requestMatchers(PUBLIC_ENDPOINTS).permitAll();
				if (apiDocsEnabled) {
					auth.requestMatchers(API_DOCS_ENDPOINTS).permitAll();
				}
				if (devTestTokenEnabled) {
					auth.requestMatchers(DEV_ENDPOINTS).permitAll();
				}
				auth.requestMatchers(HttpMethod.GET, BROWSE_ENDPOINTS).access(AuthorizationManagers.anyOf(
					AuthorityAuthorizationManager.hasAuthority(TokenRealm.GUEST.authority()),
					AuthorityAuthorizationManager.hasAuthority(TokenRealm.ADMIN.authority()),
					AuthorizationManagers.allOf(
						AuthorityAuthorizationManager.hasAuthority(TokenRealm.USER.authority()),
						AuthorityAuthorizationManager.hasRole(CONSUMER_ROLE))));
				auth.requestMatchers(CONSUMER_ENDPOINTS).access(AuthorizationManagers.allOf(
					AuthorityAuthorizationManager.hasAuthority(TokenRealm.USER.authority()),
					AuthorityAuthorizationManager.hasRole(CONSUMER_ROLE)));
				auth.requestMatchers(APP_USER_ENDPOINTS).hasAuthority(TokenRealm.USER.authority());
				auth.requestMatchers("/admin/**").hasAuthority(TokenRealm.ADMIN.authority());
				auth.requestMatchers("/owner/**").access(AuthorizationManagers.allOf(
					AuthorityAuthorizationManager.hasAuthority(TokenRealm.USER.authority()),
					AuthorityAuthorizationManager.hasRole(OWNER_ROLE)));
				auth.anyRequest().hasAuthority(TokenRealm.USER.authority());
			})
			.exceptionHandling(exception -> exception
				.authenticationEntryPoint(authenticationEntryPoint)
				.accessDeniedHandler(accessDeniedHandler))
			.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
			.addFilterAfter(new RateLimitFilter(rateLimiter, objectMapper), JwtAuthenticationFilter.class)
			.httpBasic(basic -> basic.disable())
			.formLogin(form -> form.disable());
		return http.build();
	}
}
