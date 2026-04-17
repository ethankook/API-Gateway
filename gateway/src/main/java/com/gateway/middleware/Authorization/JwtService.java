package com.gateway.middleware.Authorization;

import com.gateway.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtService {

  private final SecretKey signingKey;
  private final String issuer;

  public JwtService(JwtProperties properties) {
    byte[] keyBytes = requireValidSecret(properties.getSecret());
    this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    this.issuer = properties.getIssuer();
  }

  public Claims validate(String token) {
    return Jwts.parser()
        .verifyWith(signingKey)
        .requireIssuer(issuer)
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  private byte[] requireValidSecret(String secret) {
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException("auth.jwt.secret must be configured");
    }

    byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
    if (keyBytes.length < 32) {
      throw new IllegalStateException("auth.jwt.secret must be at least 32 bytes for HS256");
    }

    return keyBytes;
  }
}
