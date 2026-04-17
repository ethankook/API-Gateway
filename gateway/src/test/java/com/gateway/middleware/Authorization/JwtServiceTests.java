package com.gateway.middleware.Authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.gateway.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.IncorrectClaimException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTests {

  private static final String SECRET = "01234567890123456789012345678901";
  private static final String ISSUER = "Orchard";

  private JwtService jwtService;

  @BeforeEach
  void setUp() {
    JwtProperties properties = new JwtProperties();
    properties.setSecret(SECRET);
    properties.setIssuer(ISSUER);
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
    String token =
        signedToken(42L, Instant.now().plusSeconds(300), "abcdefghijklmnopqrstuvwxyz123456");

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

  @Test
  void rejectsTokenWithWrongIssuer() {
    String token = signedToken(42L, Instant.now().plusSeconds(300), SECRET, "DifferentIssuer");

    assertThrows(IncorrectClaimException.class, () -> jwtService.validate(token));
  }

  private String signedToken(Long userId, Instant expiration, String secret) {
    return signedToken(userId, expiration, secret, ISSUER);
  }

  private String signedToken(Long userId, Instant expiration, String secret, String issuer) {
    return Jwts.builder()
        .issuer(issuer)
        .claim("userId", userId)
        .expiration(Date.from(expiration))
        .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(secret.getBytes()))
        .compact();
  }
}
