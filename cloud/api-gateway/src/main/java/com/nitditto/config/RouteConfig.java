package com.nitditto.config;

import com.nitditto.enums.UserRole;
import org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.function.*;

import java.util.Arrays;
import java.util.UUID;

@Configuration
public class RouteConfig {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_EMAIL_HEADER = "X-User-Email";
    private static final String USER_ROLES_HEADER = "X-User-Roles";

    private final JwtUtil jwtUtil;

    public RouteConfig(final JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Bean
    public RouterFunction<ServerResponse> authRoutes() {
        return GatewayRouterFunctions.route("auth-routes")
                .route(RequestPredicates.path("/auth/**"), HandlerFunctions.http())
                .before(this::stripIdentityHeaders)
                .filter(LoadBalancerFilterFunctions.lb("user-service"))
                .build();
    }

    @Bean
    @Order(1)
    public RouterFunction<ServerResponse> adminLocationServiceRoutes() {
        return GatewayRouterFunctions.route("admin-location-routes")
                .route(RequestPredicates.POST("/api/cities/**"), HandlerFunctions.http())
                .route(RequestPredicates.POST("/api/airports/**"), HandlerFunctions.http())
                .before(this::jwtAuthFilter)
                .before(request -> requireRole(request, UserRole.ROLE_SYSTEM_ADMIN.toString()))
                .filter(LoadBalancerFilterFunctions.lb("location-service"))
                .build();
    }


    @Bean
    @Order(1)
    public RouterFunction<ServerResponse> adminAirlineCoreServiceRoutes() {
        return GatewayRouterFunctions.route("admin-airline-core-routes")
                .route(RequestPredicates.GET("/api/airlines"), HandlerFunctions.http())
                .before(this::jwtAuthFilter)
                .before(request -> requireRole(request, UserRole.ROLE_SYSTEM_ADMIN.toString()))
                .filter(LoadBalancerFilterFunctions.lb("airline-core-service"))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> userServiceRoutes() {
        return GatewayRouterFunctions.route("user-service-core-routes")
                .route(RequestPredicates.path("/api/users/**"), HandlerFunctions.http())
                .before(this::jwtAuthFilter)
                .filter(LoadBalancerFilterFunctions.lb("user-service"))
                .build();
    }

    @Bean
    @Order(2)
    public RouterFunction<ServerResponse> airlineCoreServiceRoutes() {
        return GatewayRouterFunctions.route("airline-core-routes")
                .route(RequestPredicates.path("/api/airlines/**"), HandlerFunctions.http())
                .route(RequestPredicates.path("/api/aircrafts/**"), HandlerFunctions.http())
                .before(this::jwtAuthFilter)
                .filter(LoadBalancerFilterFunctions.lb("airline-core-service"))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> seatServiceRoutes() {
        return GatewayRouterFunctions.route("seat-service-routes")
                .route(RequestPredicates.path("/api/cabin-classes/**"), HandlerFunctions.http())
                .route(RequestPredicates.path("/api/seat-maps/**"), HandlerFunctions.http())
                .route(RequestPredicates.path("/api/seats/**"), HandlerFunctions.http())
                .route(RequestPredicates.path("/api/seat-instances/**"), HandlerFunctions.http())
                .route(RequestPredicates.path("/api/flight-instance-cabins/**"), HandlerFunctions.http())
                .before(this::jwtAuthFilter)
                .filter(LoadBalancerFilterFunctions.lb("seat-service"))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> flightOpsServiceRoutes() {
        return GatewayRouterFunctions.route("flight-ops-routes")
                .route(RequestPredicates.path("/api/flights/**"), HandlerFunctions.http())
                .route(RequestPredicates.path("/api/flight-instances/**"), HandlerFunctions.http())
                .route(RequestPredicates.path("/api/flight-schedules/**"), HandlerFunctions.http())
                .before(this::jwtAuthFilter)
                .filter(LoadBalancerFilterFunctions.lb("flight-ops-service"))
                .build();
    }


    @Bean
    public RouterFunction<ServerResponse> pricingServiceRoutes() {
        return GatewayRouterFunctions.route("pricing-service-routes")
                .route(RequestPredicates.path("/api/fares/**"), HandlerFunctions.http())
                .route(RequestPredicates.path("/api/fare-rules/**"), HandlerFunctions.http())
                .route(RequestPredicates.path("/api/baggage-policies/**"), HandlerFunctions.http())
                .before(this::jwtAuthFilter)
                .filter(LoadBalancerFilterFunctions.lb("pricing-service"))
                .build();
    }


    @Bean
    public RouterFunction<ServerResponse> ancillaryServiceRoutes() {
        return GatewayRouterFunctions.route("ancillary-service-routes")
                .route(RequestPredicates.path("/api/meals/**"), HandlerFunctions.http())
                .route(RequestPredicates.path("/api/ancillaries/**"), HandlerFunctions.http())
                .route(RequestPredicates.path("/api/insurance-coverages/**"), HandlerFunctions.http())
                .route(RequestPredicates.path("/api/flight-meals/**"), HandlerFunctions.http())
                .route(RequestPredicates.path("/api/flight-cabin-ancillaries/**"), HandlerFunctions.http())
                .before(this::jwtAuthFilter)
                .filter(LoadBalancerFilterFunctions.lb("ancillary-service"))
                .build();
    }

    @Bean
    @Order(2)
    public RouterFunction<ServerResponse> locationServiceRoutes() {
        return GatewayRouterFunctions.route("location-service-routes")
                .route(RequestPredicates.path("/api/cities/**"), HandlerFunctions.http())
                .route(RequestPredicates.path("/api/airports/**"), HandlerFunctions.http())
                .before(this::jwtAuthFilter)
                .filter(LoadBalancerFilterFunctions.lb("location-service"))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> bookingServiceRoutes() {
        return GatewayRouterFunctions.route("booking-service-routes")
                .route(RequestPredicates.path("/api/bookings/**"), HandlerFunctions.http())
                .before(this::jwtAuthFilter)
                .filter(LoadBalancerFilterFunctions.lb("booking-service"))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> paymentServiceRoutes() {
        return GatewayRouterFunctions.route("payment-service-routes")
                .route(RequestPredicates.path("/api/payments/**"), HandlerFunctions.http())
                .before(this::jwtAuthFilter)
                .filter(LoadBalancerFilterFunctions.lb("payment-service"))
                .build();
    }

    private ServerRequest jwtAuthFilter(ServerRequest request) {

        String authHeader = request.headers().firstHeader(JwtConstant.JWT_HEADER);

        // step 1 check auth header exist
        if (authHeader == null || !authHeader.startsWith(JwtConstant.TOKEN_PREFIX)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "missing or invalid authorization token");
        }

        // step 2 remove prefix from token
        String token = authHeader.substring(JwtConstant.TOKEN_PREFIX.length());

        // validate
        if (!jwtUtil.isTokenValid(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid or expired JWT token");
        }

        // step 3 grab user info from jwt token
        String email = jwtUtil.extractEmail(token);
        String authorities = jwtUtil.extractAuthorities(token);
        UUID userId = jwtUtil.extractUserId(token);

        // token ký đúng nhưng thiếu claim danh tính thì không được forward "null" xuống downstream
        if (email == null || authorities == null || userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT token is missing identity claims");
        }

        // set (không add) để ghi đè header X-User-* mà client có thể tự gửi kèm
        return ServerRequest.from(request)
                .headers(headers -> {
                    headers.set(USER_ID_HEADER, String.valueOf(userId));
                    headers.set(USER_EMAIL_HEADER, email);
                    headers.set(USER_ROLES_HEADER, authorities);
                }).build();
    }

    private ServerRequest stripIdentityHeaders(ServerRequest request) {
        return ServerRequest.from(request)
                .headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(USER_EMAIL_HEADER);
                    headers.remove(USER_ROLES_HEADER);
                }).build();
    }

    private ServerRequest requireRole(ServerRequest request, String role) {
        String roles = request.headers().firstHeader(USER_ROLES_HEADER);
        if (roles == null || !Arrays.asList(roles.split(",")).contains(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "access denied, required role: '" + role + "'");
        }
        return request;
    }
}
