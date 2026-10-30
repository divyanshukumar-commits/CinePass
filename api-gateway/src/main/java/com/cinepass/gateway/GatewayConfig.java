package com.cinepass.gateway;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("movie-service", route -> route.path("/api/movies/**")
                        .filters(filter -> filter.stripPrefix(1))
                        .uri("lb://MOVIE-SERVICE"))
                .route("show-service", route -> route.path("/api/shows/**", "/api/screens/**")
                        .filters(filter -> filter.stripPrefix(1))
                        .uri("lb://SHOW-SERVICE"))
                .route("user-service", route -> route.path("/api/auth/**", "/api/users/**")
                        .filters(filter -> filter.stripPrefix(1))
                        .uri("lb://USER-SERVICE"))
                .route("booking-service", route -> route.path("/api/bookings/**")
                        .filters(filter -> filter.stripPrefix(1))
                        .uri("lb://BOOKING-SERVICE"))
                .build();
    }
}
