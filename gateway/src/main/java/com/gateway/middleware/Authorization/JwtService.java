package com.gateway.middleware.Authorization;

import com.gateway.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

@Component
public class JwtService {

    private final SecretKey signInKey;
    private final JwtProperties properties;

    public JwtService(JwtProperties properties) {
        byte[] keyBytes = properties.getSecret().getBytes();
        this.signInKey = Keys.hmacShaKeyFor(keyBytes);
        this.properties = properties;
    }

    public Claims validate(String token) {
        return Jwts.parser()
                .verifyWith(signInKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
