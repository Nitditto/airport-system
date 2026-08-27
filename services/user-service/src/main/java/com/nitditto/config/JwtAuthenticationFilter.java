package com.nitditto.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtProvider jwtProvider;

  @Override
  protected void doFilterInternal(final HttpServletRequest request,
      final HttpServletResponse response, final FilterChain filterChain)
      throws ServletException, IOException {
    final String header = request.getHeader(HttpHeaders.AUTHORIZATION);

    if (header != null && header.startsWith(JwtConstant.TOKEN_PREFIX)) {
      final String token = header.substring(JwtConstant.TOKEN_PREFIX.length());
      try {
        final Claims claims = this.jwtProvider.parseClaims(token);
        final String email = claims.get("email", String.class);
        final List<GrantedAuthority> authorities = this.parseAuthorities(
            claims.get("authorities", String.class));

        final Authentication authentication =
            new UsernamePasswordAuthenticationToken(email, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
      } catch (final JwtException | IllegalArgumentException ex) {
        SecurityContextHolder.clearContext();
      }
    }

    filterChain.doFilter(request, response);
  }

  private List<GrantedAuthority> parseAuthorities(final String authoritiesClaim) {
    if (authoritiesClaim == null || authoritiesClaim.isBlank()) {
      return List.of();
    }
    return Arrays.stream(authoritiesClaim.split(","))
        .map(SimpleGrantedAuthority::new)
        .map(GrantedAuthority.class::cast)
        .toList();
  }
}
