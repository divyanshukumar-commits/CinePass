package com.cinepass.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ApiGatewayApplicationTests {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void contextLoads() {
    }

    @Test
    void movieRouteMatchesApiMoviesRequests() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();
        assertNotNull(routes);

        Route movieRoute = routes.stream()
                .filter(route -> "movie-service".equals(route.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("movie-service route was not registered"));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/movies").build()
        );

        assertTrue(Mono.from(movieRoute.getPredicate().apply(exchange)).block());
    }
}
