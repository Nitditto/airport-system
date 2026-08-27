package com.nitditto.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import javax.crypto.SecretKey;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

@Service
public class JwtProvider {

  private final SecretKey key = Keys.hmacShaKeyFor(
      JwtConstant.SECRET_KEY.getBytes()
  );

  public Claims parseClaims(final String token) throws JwtException {
    return Jwts.parser()
        .verifyWith(key)
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  public String generateToken(Authentication auth, Long userId) {
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
