package com.swyp.backend.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

	private static final String TYPE_CLAIM = "typ";
	private static final String REALM_CLAIM = "realm";
	private static final String ROLE_CLAIM = "role";
	private static final String ACCESS_TYPE = "access";

	private final SecretKey key;
	private final Duration accessTtl;

	public JwtTokenProvider(JwtProperties properties) {
		this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
		this.accessTtl = properties.accessTtl();
	}

	public String createAccessToken(TokenRealm realm, Long principalId, String role) {
		return createToken(
			ACCESS_TYPE,
			String.valueOf(principalId),
			Map.of(REALM_CLAIM, realm.name(), ROLE_CLAIM, role),
			accessTtl);
	}

	public AccessToken parseAccessToken(String token) {
		Claims claims = parse(ACCESS_TYPE, token);
		String realm = claims.get(REALM_CLAIM, String.class);
		if (realm == null) {
			throw new JwtException("access token carries no realm claim");
		}
		return new AccessToken(
			Long.valueOf(claims.getSubject()),
			TokenRealm.valueOf(realm),
			claims.get(ROLE_CLAIM, String.class));
	}

	public String createToken(String type, String subject, Map<String, String> claims, Duration ttl) {
		Instant now = Instant.now();
		return Jwts.builder()
			.subject(subject)
			.claim(TYPE_CLAIM, type)
			.claims(claims)
			.issuedAt(Date.from(now))
			.expiration(Date.from(now.plus(ttl)))
			.signWith(key)
			.compact();
	}

	public Claims parse(String type, String token) {
		return Jwts.parser()
			.verifyWith(key)
			.require(TYPE_CLAIM, type)
			.build()
			.parseSignedClaims(token)
			.getPayload();
	}

	public record AccessToken(Long principalId, TokenRealm realm, String role) {
	}
}
