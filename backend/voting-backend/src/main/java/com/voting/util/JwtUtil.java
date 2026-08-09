package com.voting.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具
 */
@Component
public class JwtUtil {

    @Value("${voting.jwt.secret}")
    private String secret;

    @Value("${voting.jwt.expire-hours:12}")
    private int expireHours;

    private Key key;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 token
     */
    public String generate(Long userId, String accountNo, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("accountNo", accountNo);
        claims.put("role", role);
        Date now = new Date();
        Date exp = new Date(now.getTime() + expireHours * 3600_000L);
        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 解析 token, 失败返回 null
     */
    public Claims parse(String token) {
        try {
            return Jwts.parserBuilder().setSigningKey(key).build()
                    .parseClaimsJws(token).getBody();
        } catch (Exception e) {
            return null;
        }
    }

    public Long getUserId(String token) {
        Claims c = parse(token);
        return c == null ? null : ((Number) c.get("userId")).longValue();
    }

    public String getRole(String token) {
        Claims c = parse(token);
        return c == null ? null : (String) c.get("role");
    }

    public String getAccountNo(String token) {
        Claims c = parse(token);
        return c == null ? null : (String) c.get("accountNo");
    }
}
