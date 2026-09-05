package api.gateway.api_gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import api.gateway.api_gateway.filter.JwtAuthFilter;

@Configuration
public class GatewayConfig {
    private final JwtAuthFilter jwtAuthFilter;

    public GatewayConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
            .route("auth", r -> r.path("/auth/**")
                .filters(f -> f.filter(jwtAuthFilter))
                .uri("lb://auth-service"))
            .route("user", r -> r.path("/users/**")
                .filters(f -> f.filter(jwtAuthFilter))
                .uri("lb://user"))
            .route("product", r -> r.path("/products/**")
                .filters(f -> f.filter(jwtAuthFilter))
                .uri("lb://product"))
            .route("order", r -> r.path("/orders/**")
                .filters(f -> f.filter(jwtAuthFilter))
                .uri("lb://order"))
            .route("payment", r -> r.path("/payments/**")
                .filters(f -> f.filter(jwtAuthFilter))
                .uri("lb://payment"))
            .route("notification", r -> r.path("/notifications/**")
                .filters(f -> f.filter(jwtAuthFilter))
                .uri("lb://notification-service"))
            .route("file-storage", r -> r.path("/files/**")
                .filters(f -> f.filter(jwtAuthFilter))
                .uri("lb://file-storage-service"))
            .route("role", r -> r.path("/roles/**")
                .filters(f -> f.filter(jwtAuthFilter))
                .uri("lb://role-service"))
            .route("merchant", r -> r.path("/merchants/**", "/merchant-documents/**")
                .filters(f -> f.filter(jwtAuthFilter))
                .uri("lb://merchant-service"))
            .route("cashier", r -> r.path("/cashiers/**")
                .filters(f -> f.filter(jwtAuthFilter))
                .uri("lb://cashier-service"))
            .route("category", r -> r.path("/categories/**")
                .filters(f -> f.filter(jwtAuthFilter))
                .uri("lb://category-service"))
            .route("order-item", r -> r.path("/order-items/**")
                .filters(f -> f.filter(jwtAuthFilter))
                .uri("lb://order-item-service"))
            .route("transaction", r -> r.path("/transactions/**")
                .filters(f -> f.filter(jwtAuthFilter))
                .uri("lb://transaction-service"))
            .route("stats", r -> r.path("/stats/**")
                .filters(f -> f.filter(jwtAuthFilter))
                .uri("lb://stats-reader"))
            .build();
    }
}
