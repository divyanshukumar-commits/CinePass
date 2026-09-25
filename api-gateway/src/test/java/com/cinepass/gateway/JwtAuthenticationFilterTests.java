package com.cinepass.gateway;

import com.cinepass.gateway.filter.JwtAuthenticationFilter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class JwtAuthenticationFilterTests {

    private static final String SECRET = "CinePassSuperSecretKeyForJWTSigning2026HighConcurrencyCinemaEngine!";
    private final SecretKey signingKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(SECRET);
    }

    private String generateToken(Long userId, String username, String role, long expirationDeltaMs) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationDeltaMs))
                .signWith(signingKey)
                .compact();
    }

    @Test
    @DisplayName("Unsecured routes like /api/movies pass through without requiring JWT")
    void testUnsecuredRoutePassesThrough() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/movies").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();
        assertTrue(chainCalled.get());
    }

    @Test
    @DisplayName("Secured route /api/bookings without Authorization header returns 401")
    void testSecuredRouteMissingAuthHeader() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/bookings").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();
        assertFalse(chainCalled.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("Secured route /api/bookings with expired JWT token returns 401")
    void testSecuredRouteExpiredToken() {
        String expiredToken = generateToken(1L, "bob", "ROLE_CUSTOMER", -1000); // expired
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/bookings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();
        assertFalse(chainCalled.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("Secured route /api/bookings with valid JWT forwards X-User-Id downstream")
    void testSecuredRouteValidTokenForwardsHeaders() {
        String validToken = generateToken(42L, "charlie", "ROLE_CUSTOMER", 3600000);
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/bookings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<ServerWebExchange> mutatedExchange = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            mutatedExchange.set(ex);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertNotNull(mutatedExchange.get());
        assertEquals("42", mutatedExchange.get().getRequest().getHeaders().getFirst("X-User-Id"));
        assertEquals("charlie", mutatedExchange.get().getRequest().getHeaders().getFirst("X-User-Name"));
        assertEquals("ROLE_CUSTOMER", mutatedExchange.get().getRequest().getHeaders().getFirst("X-User-Role"));
    }
}
