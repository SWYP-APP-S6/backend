package com.swyp.backend.common.security;

import com.swyp.backend.common.response.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

public class RateLimitFilter extends OncePerRequestFilter {

	private final RateLimiter rateLimiter;
	private final ObjectMapper objectMapper;

	public RateLimitFilter(RateLimiter rateLimiter, ObjectMapper objectMapper) {
		this.rateLimiter = rateLimiter;
		this.objectMapper = objectMapper;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		Optional<TokenRealm> realm = authentication == null
				? Optional.empty()
				: TokenRealm.fromAuthorities(authentication.getAuthorities());
		if (realm.isPresent() && authentication.getPrincipal() instanceof Long principalId) {
			RateLimiter.Decision decision = rateLimiter.tryAcquire(realm.get(), principalId);
			if (!decision.allowed()) {
				reject(response, decision);
				return;
			}
		}
		chain.doFilter(request, response);
	}

	private void reject(HttpServletResponse response, RateLimiter.Decision decision) throws IOException {
		AuthErrorCode code = AuthErrorCode.TOO_MANY_REQUESTS;
		response.setStatus(code.getStatus().value());
		response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		objectMapper.writeValue(response.getWriter(), ErrorResponse.of(code));
	}
}
