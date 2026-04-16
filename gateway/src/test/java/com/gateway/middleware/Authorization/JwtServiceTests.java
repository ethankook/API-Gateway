package com.gateway.middleware.Authorization;

import com.gateway.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTests {

    private static final String SECRET = "01234567890123456789012345678901";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setIssuer("Orchard");
        jwtService = new JwtService(properties);
    }

    @Test
    void validatesTokenAndReturnsClaims() {
        String token = signedToken(42L, Instant.now().plusSeconds(300), SECRET);

        Claims claims = jwtService.validate(token);

        assertThat(claims.get("userId", Long.class)).isEqualTo(42L);
    }

    @Test
    void rejectsExpiredToken() {
        String token = signedToken(42L, Instant.now().minusSeconds(60), SECRET);

        assertThrows(ExpiredJwtException.class, () -> jwtService.validate(token));
    }

    @Test
    void rejectsTamperedToken() {
        String token = signedToken(42L, Instant.now().plusSeconds(300), "abcdefghijklmnopqrstuvwxyz123456");

        assertThrows(SignatureException.class, () -> jwtService.validate(token));
    }

    @Test
    void rejectsMalformedToken() {
        assertThrows(MalformedJwtException.class, () -> jwtService.validate("not-a-jwt"));
    }

    @Test
    void rejectsMissingToken() {
        assertThrows(IllegalArgumentException.class, () -> jwtService.validate(null));
    }

    private String signedToken(Long userId, Instant expiration, String secret) {
        return Jwts.builder()
                .issuer("Orchard")
                .claim("userId", userId)
                .expiration(Date.from(expiration))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(secret.getBytes()))
                .compact();
    }
}
