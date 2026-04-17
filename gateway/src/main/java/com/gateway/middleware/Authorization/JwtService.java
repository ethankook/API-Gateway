package com.gateway.middleware.Authorization;

import com.gateway.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

@Component
public class JwtService {

    private final SecretKey signingKey;
    private final String issuer;

    public JwtService(JwtProperties properties) {
        byte[] keyBytes = properties.getSecret().getBytes();
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
}
