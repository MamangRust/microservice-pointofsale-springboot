package api.gateway.api_gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;

import api.gateway.api_gateway.util.JwtUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.opentelemetry.api.OpenTelemetry;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Reactive unit tests for {@link JwtAuthFilter} — no server, no Spring context.
 * Uses MockServerWebExchange + a stubbed GatewayFilterChain + StepVerifier.
 * The real {@link JwtUtil} (with an injected test secret) is used so that
 * valid/invalid token handling is exercised end to end.
 */
class JwtAuthFilterTest {

    // exactly 32 bytes -> unambiguous HS256 after Base64 decode (mirrors JwtUtil)
    private static final String RAW_SECRET = "01234567890123456789012345678901";
    private static final String BASE64_SECRET = Base64.getEncoder().encodeToString(RAW_SECRET.getBytes());

    private JwtAuthFilter jwtAuthFilter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secretKey", BASE64_SECRET);
        jwtAuthFilter = new JwtAuthFilter(jwtUtil, OpenTelemetry.noop());
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    private String validToken() {
        var key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(BASE64_SECRET));
        return Jwts.builder()
                .setSubject("john")
                .setExpiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(key)
                .compact();
    }

    private MockServerWebExchange exchangeFor(String path, String authorizationHeader) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.get(path);
        if (authorizationHeader != null) {
            request = request.header("Authorization", authorizationHeader);
        }
        return MockServerWebExchange.from(request.build());
    }

    @Test
    void bypassesAuthLoginEndpoint() {
        MockServerWebExchange exchange = exchangeFor("/auth/login", null);

        StepVerifier.create(jwtAuthFilter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void bypassesAuthRegisterEndpoint() {
        MockServerWebExchange exchange = exchangeFor("/auth/register", null);

        StepVerifier.create(jwtAuthFilter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void bypassesAnyPathContainingAuthEndpointSubstring() {
        // quirk of the product: the bypass matches via String.contains, not equals
        MockServerWebExchange exchange = exchangeFor("/docs/auth/login/page", null);

        StepVerifier.create(jwtAuthFilter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void rejectsRequestWithoutAuthorizationHeader() {
        MockServerWebExchange exchange = exchangeFor("/orders", null);

        StepVerifier.create(jwtAuthFilter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst("error-message"))
                .isEqualTo("Missing Authorization header");
        verify(chain, never()).filter(any());
    }

    @Test
    void rejectsNonBearerAuthorizationHeader() {
        MockServerWebExchange exchange = exchangeFor("/orders", "Basic am9objpob2hv");

        StepVerifier.create(jwtAuthFilter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst("error-message"))
                .isEqualTo("Invalid Authorization header format");
        verify(chain, never()).filter(any());
    }

    @Test
    void rejectsInvalidBearerToken() {
        MockServerWebExchange exchange = exchangeFor("/orders", "Bearer this-is-not-a-jwt");

        StepVerifier.create(jwtAuthFilter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst("error-message"))
                .isEqualTo("Invalid token");
        verify(chain, never()).filter(any());
    }

    @Test
    void forwardsValidBearerTokenToDownstreamChain() {
        MockServerWebExchange exchange = exchangeFor("/orders", "Bearer " + validToken());

        StepVerifier.create(jwtAuthFilter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
    }
}
