package com.julia_auto_cars.rental_api.security;

import java.time.Instant;
import java.util.Date;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import io.jsonwebtoken.*;

@Component
public class JwtTokenService {
    @Value("${security.jwt.secret}")
    private String secret;

    @Value("${security.jwt.expiration-seconds:3600}")
    private long expirationSeconds;

    public String generateToken(String username, Set<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(username)
                .claim("roles", roles)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(SignatureAlgorithm.HS256, secret)
                .compact();
    }

    public String extractUsername(String token) {
        return Jwts.parser().setSigningKey(secret).parseClaimsJws(token).getBody().getSubject();
    }

    public boolean isValid(String token, UserDetails userDetails) {
        Claims claims = Jwts.parser().setSigningKey(secret).parseClaimsJws(token).getBody();
        return userDetails.getUsername().equalsIgnoreCase(claims.getSubject()) && claims.getExpiration().after(new Date());
    }
}

