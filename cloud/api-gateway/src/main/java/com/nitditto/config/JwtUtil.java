package com.nitditto.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    /** Danh tính rút ra từ một JWT đã verify; mọi field đều non-null. */
    public record Identity(String email, String authorities, UUID userId) {
    }

    private final JwtParser parser;

    public JwtUtil(@Value("${jwt.secret}") final String secret) {
        this.parser = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .build();
    }

    /**
     * Verify chữ ký đúng một lần rồi lấy danh tính. Trả empty cho mọi token không dùng được:
     * sai chữ ký, hết hạn, không có {@code exp}, thiếu claim hoặc claim sai kiểu.
     */
    public Optional<Identity> parseIdentity(final String token) {
        try {
            final Claims claims = this.parser.parseSignedClaims(token).getPayload();
            // token không hạn dùng sẽ hợp lệ vĩnh viễn nếu bị lộ
            if (claims.getExpiration() == null) {
                log.debug("Rejected JWT: missing exp claim");
                return Optional.empty();
            }
            return this.toIdentity(claims);
        } catch (final JwtException | IllegalArgumentException ex) {
            log.debug("Rejected JWT: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<Identity> toIdentity(final Claims claims) {
        final String email = this.stringClaim(claims, "email");
        final String authorities = this.stringClaim(claims, "authorities");
        final String userId = this.stringClaim(claims, "userId");
        if (email == null || authorities == null || userId == null) {
            log.debug("Rejected JWT: missing or non-string identity claim");
            return Optional.empty();
        }
        try {
            return Optional.of(new Identity(email, authorities, UUID.fromString(userId)));
        } catch (final IllegalArgumentException ex) {
            log.debug("Rejected JWT: userId claim is not a UUID");
            return Optional.empty();
        }
    }

    // đọc Object rồi tự kiểm tra kiểu: claim là mảng/số sẽ ra null thay vì ném RequiredTypeException (500)
    private String stringClaim(final Claims claims, final String name) {
        return claims.get(name) instanceof final String value ? value : null;
    }
}
