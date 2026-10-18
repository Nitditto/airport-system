package com.nitditto.config;

import com.nitditto.enums.UserRole;
import org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Mỗi service đúng một route; quyền được quyết định trong một filter duy nhất nên không còn
 * phụ thuộc thứ tự bean {@code @Order}. Gateway chỉ kiểm tra role — quyền sở hữu bản ghi
 * (owner A sửa dữ liệu của owner B) thuộc về service nghiệp vụ, đối chiếu qua X-User-Id.
 */
@Configuration
public class RouteConfig {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_EMAIL_HEADER = "X-User-Email";
    private static final String USER_ROLES_HEADER = "X-User-Roles";

    private static final Set<HttpMethod> WRITE_METHODS =
            Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);

    // POST nhưng bản chất là đọc (CLAUDE.md §6): không được coi là thao tác ghi
    private static final Pattern BATCH_READ_PATH = Pattern.compile("^/api/[^/]+/batch-by-ids/?$");
    private static final Pattern AIRLINE_LISTING_PATH = Pattern.compile("^/api/airlines/?$");

    private static final UserRole[] ADMIN = {UserRole.ROLE_SYSTEM_ADMIN};
    private static final UserRole[] ADMIN_OR_OWNER = {UserRole.ROLE_SYSTEM_ADMIN, UserRole.ROLE_AIRLINE_OWNER};
    private static final UserRole[] ANY_AUTHENTICATED = {};

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
    public RouterFunction<ServerResponse> userServiceRoutes() {
        return GatewayRouterFunctions.route("user-service-core-routes")
                .route(RequestPredicates.path("/api/users/**"), HandlerFunctions.http())
                .before(request -> this.secured(request, ANY_AUTHENTICATED))
                .filter(LoadBalancerFilterFunctions.lb("user-service"))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> locationServiceRoutes() {
        return GatewayRouterFunctions.route("location-service-routes")
                .route(RequestPredicates.path("/api/cities/**"), HandlerFunctions.http())
                .route(RequestPredicates.path("/api/airports/**"), HandlerFunctions.http())
                .before(request -> this.secured(request, ADMIN))
                .filter(LoadBalancerFilterFunctions.lb("location-service"))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> airlineCoreServiceRoutes() {
        return GatewayRouterFunctions.route("airline-core-routes")
                .route(RequestPredicates.path("/api/airlines/**"), HandlerFunctions.http())
                .route(RequestPredicates.path("/api/aircrafts/**"), HandlerFunctions.http())
                .before(this::airlineCoreAccess)
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
                .before(request -> this.secured(request, ADMIN_OR_OWNER))
                .filter(LoadBalancerFilterFunctions.lb("seat-service"))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> flightOpsServiceRoutes() {
        return GatewayRouterFunctions.route("flight-ops-routes")
                .route(RequestPredicates.path("/api/flights/**"), HandlerFunctions.http())
                .route(RequestPredicates.path("/api/flight-instances/**"), HandlerFunctions.http())
                .route(RequestPredicates.path("/api/flight-schedules/**"), HandlerFunctions.http())
                .before(request -> this.secured(request, ADMIN_OR_OWNER))
                .filter(LoadBalancerFilterFunctions.lb("flight-ops-service"))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> pricingServiceRoutes() {
        return GatewayRouterFunctions.route("pricing-service-routes")
                .route(RequestPredicates.path("/api/fares/**"), HandlerFunctions.http())
                .route(RequestPredicates.path("/api/fare-rules/**"), HandlerFunctions.http())
                .route(RequestPredicates.path("/api/baggage-policies/**"), HandlerFunctions.http())
                .before(request -> this.secured(request, ADMIN_OR_OWNER))
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
                .before(request -> this.secured(request, ADMIN_OR_OWNER))
                .filter(LoadBalancerFilterFunctions.lb("ancillary-service"))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> bookingServiceRoutes() {
        return GatewayRouterFunctions.route("booking-service-routes")
                .route(RequestPredicates.path("/api/bookings/**"), HandlerFunctions.http())
                .before(request -> this.secured(request, ANY_AUTHENTICATED))
                .filter(LoadBalancerFilterFunctions.lb("booking-service"))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> paymentServiceRoutes() {
        return GatewayRouterFunctions.route("payment-service-routes")
                .route(RequestPredicates.path("/api/payments/**"), HandlerFunctions.http())
                .before(request -> this.secured(request, ANY_AUTHENTICATED))
                .filter(LoadBalancerFilterFunctions.lb("payment-service"))
                .build();
    }

    /** Danh sách toàn bộ hãng (kèm trạng thái quản trị) chỉ dành cho admin; còn lại theo quy tắc ghi chung. */
    private ServerRequest airlineCoreAccess(final ServerRequest request) {
        final ServerRequest authenticated = this.secured(request, ADMIN_OR_OWNER);
        if (HttpMethod.GET.equals(authenticated.method())
                && AIRLINE_LISTING_PATH.matcher(authenticated.path()).matches()) {
            return this.requireAnyRole(authenticated, ADMIN);
        }
        return authenticated;
    }

    /** Xác thực JWT; nếu là thao tác ghi thì yêu cầu thêm một trong {@code writeRoles} (rỗng = chỉ cần đăng nhập). */
    private ServerRequest secured(final ServerRequest request, final UserRole... writeRoles) {
        final ServerRequest authenticated = this.jwtAuthFilter(request);
        if (writeRoles.length > 0 && this.isWrite(authenticated)) {
            return this.requireAnyRole(authenticated, writeRoles);
        }
        return authenticated;
    }

    private boolean isWrite(final ServerRequest request) {
        return WRITE_METHODS.contains(request.method())
                && !(HttpMethod.POST.equals(request.method()) && BATCH_READ_PATH.matcher(request.path()).matches());
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

    private ServerRequest stripIdentityHeaders(final ServerRequest request) {
        return ServerRequest.from(request)
                .headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(USER_EMAIL_HEADER);
                    headers.remove(USER_ROLES_HEADER);
                }).build();
    }

    private ServerRequest requireAnyRole(final ServerRequest request, final UserRole... allowedRoles) {
        final String roles = request.headers().firstHeader(USER_ROLES_HEADER);
        if (roles != null) {
            final Set<String> granted = Set.copyOf(Arrays.stream(roles.split(",")).map(String::trim).toList());
            for (final UserRole allowed : allowedRoles) {
                if (granted.contains(allowed.name())) {
                    return request;
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "access denied, required one of roles: " + Arrays.toString(allowedRoles));
    }
}
