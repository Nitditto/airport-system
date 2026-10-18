package com.nitditto.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

@Service
public class JwtProvider {

  private final SecretKey key;

  public JwtProvider(@Value("${jwt.secret}") final String secret) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }

  public Claims parseClaims(final String token) throws JwtException {
    return Jwts.parser()
        .verifyWith(key)
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  public String generateToken(final Authentication auth, final UUID userId) {
    Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
    String roles = populateAuthorities(authorities);
    String jwt = Jwts.builder()
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + 86400000))
        .claim("email", auth.getName())
        .claim("authorities", roles)
        .claim("userId", userId)
        .signWith(key)
        .compact();
    return jwt;
  }

  private String populateAuthorities(Collection<? extends GrantedAuthority> authorities) {
    Set<String> auths = new HashSet<>();
    for (GrantedAuthority auth : authorities) {
      auths.add(auth.getAuthority());
    }
    return String.join(",", auths);
  }
}
