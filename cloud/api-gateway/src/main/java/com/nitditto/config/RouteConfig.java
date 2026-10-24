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
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;
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
    // Thao tác ghi mà khách hàng (mọi người dùng đăng nhập) được phép: giữ/nhả ghế của chính mình.
    // confirm-booked và free (nội bộ, booking-service gọi) cố ý KHÔNG nằm đây nên chỉ ADMIN/OWNER gọi được qua gateway.
    private static final Pattern CUSTOMER_WRITE_PATH = Pattern.compile("^/api/seat-instances/(hold|release)/?$");
    // payment-service /initiate nhận userId và amount từ body nên chỉ booking-service (Feign nội bộ, không qua gateway)
    // được gọi; qua gateway chỉ ADMIN. So khớp theo chuỗi con (không phân biệt hoa thường) để đường dẫn biến thể
    // như /api/payments/./initiate hay /api/payments/initiate;x cũng không lọt.
    private static final String INTERNAL_PAYMENT_SEGMENT = "initiate";
    private static final Pattern AIRLINE_LISTING_PATH = Pattern.compile("^/api/airlines/?$");

    /**
     * Endpoint đọc dành cho khách chưa đăng nhập (tìm chuyến bay trên trang chủ). Danh sách này cố ý
     * hẹp và khai báo tường minh: thêm đường dẫn mới là một quyết định bảo mật, không phải mặc định.
     * POST /api/fares/search là POST nhưng chỉ tra giá (không ghi), giống ngoại lệ batch-by-ids.
     */
    private static final List<PublicRead> PUBLIC_READS = List.of(
            new PublicRead(HttpMethod.GET, Pattern.compile("^/api/airports(/[^/]+|/city/[^/]+)?/?$")),
            new PublicRead(HttpMethod.GET, Pattern.compile("^/api/flight-instances/search/?$")),
            new PublicRead(HttpMethod.GET, Pattern.compile("^/api/cabin-classes/aircraft/[^/]+/?$")),
            new PublicRead(HttpMethod.POST, Pattern.compile("^/api/fares/search/?$")),
            new PublicRead(HttpMethod.GET, Pattern.compile("^/api/payments/check-payment-vnpay/?$")));

    private record PublicRead(HttpMethod method, Pattern path) {
    }

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
        return this.route("user-service-core-routes", "user-service", this.authenticated(ANY_AUTHENTICATED),
                "/api/users/**");
    }

    @Bean
    public RouterFunction<ServerResponse> locationServiceRoutes() {
        return this.route("location-service-routes", "location-service", this.authenticated(ADMIN),
                "/api/cities/**", "/api/airports/**");
    }

    @Bean
    public RouterFunction<ServerResponse> airlineCoreServiceRoutes() {
        return this.route("airline-core-routes", "airline-core-service", this::airlineCoreAccess,
                "/api/airlines/**", "/api/aircrafts/**");
    }

    @Bean
    public RouterFunction<ServerResponse> seatServiceRoutes() {
        return this.route("seat-service-routes", "seat-service", this.authenticated(ADMIN_OR_OWNER),
                "/api/cabin-classes/**", "/api/seat-maps/**", "/api/seats/**",
                "/api/seat-instances/**", "/api/flight-instance-cabins/**");
    }

    @Bean
    public RouterFunction<ServerResponse> flightOpsServiceRoutes() {
        return this.route("flight-ops-routes", "flight-ops-service", this.authenticated(ADMIN_OR_OWNER),
                "/api/flights/**", "/api/flight-instances/**", "/api/flight-schedules/**");
    }

    @Bean
    public RouterFunction<ServerResponse> pricingServiceRoutes() {
        return this.route("pricing-service-routes", "pricing-service", this.authenticated(ADMIN_OR_OWNER),
                "/api/fares/**", "/api/fare-rules/**", "/api/baggage-policies/**");
    }

    @Bean
    public RouterFunction<ServerResponse> ancillaryServiceRoutes() {
        return this.route("ancillary-service-routes", "ancillary-service", this.authenticated(ADMIN_OR_OWNER),
                "/api/meals/**", "/api/ancillaries/**", "/api/insurance-coverages/**",
                "/api/flight-meals/**", "/api/flight-cabin-ancillaries/**");
    }

    @Bean
    public RouterFunction<ServerResponse> bookingServiceRoutes() {
        return this.route("booking-service-routes", "booking-service", this.authenticated(ANY_AUTHENTICATED),
                "/api/bookings/**");
    }

    @Bean
    public RouterFunction<ServerResponse> paymentServiceRoutes() {
        return this.route("payment-service-routes", "payment-service", this::paymentAccess,
                "/api/payments/**");
    }

    private RouterFunction<ServerResponse> route(final String routeId, final String serviceId,
                                                 final UnaryOperator<ServerRequest> access, final String... paths) {
        final RouterFunctions.Builder builder = GatewayRouterFunctions.route(routeId);
        for (final String path : paths) {
            builder.route(RequestPredicates.path(path), HandlerFunctions.http());
        }
        return builder.before(request -> this.isPublicRead(request)
                        ? this.stripIdentityHeaders(request)
                        : access.apply(request))
                .filter(LoadBalancerFilterFunctions.lb(serviceId))
                .build();
    }

    private boolean isPublicRead(final ServerRequest request) {
        // Đường dẫn chưa chuẩn hoá (../, //, %2e) có thể khớp luật công khai ở gateway nhưng được service
        // phía sau chuẩn hoá thành đường dẫn khác, nên không bao giờ coi là public.
        final String path = request.path();
        if (path.contains("..") || path.contains("//") || path.contains("%")) {
            return false;
        }
        return PUBLIC_READS.stream().anyMatch(rule -> rule.method().equals(request.method())
                && rule.path().matcher(request.path()).matches());
    }

    private UnaryOperator<ServerRequest> authenticated(final UserRole... writeRoles) {
        return request -> this.secured(request, writeRoles);
    }

    /** Thanh toán: đăng nhập là đủ, trừ lệnh khởi tạo thanh toán (nội bộ) chỉ dành cho ADMIN. */
    private ServerRequest paymentAccess(final ServerRequest request) {
        final ServerRequest authenticated = this.secured(request, ANY_AUTHENTICATED);
        if (HttpMethod.POST.equals(authenticated.method())
                && authenticated.path().toLowerCase(java.util.Locale.ROOT).contains(INTERNAL_PAYMENT_SEGMENT)) {
            return this.requireAnyRole(authenticated, ADMIN);
        }
        return authenticated;
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
                && !(HttpMethod.POST.equals(request.method())
                && (BATCH_READ_PATH.matcher(request.path()).matches()
                || CUSTOMER_WRITE_PATH.matcher(request.path()).matches()));
    }

    private ServerRequest jwtAuthFilter(final ServerRequest request) {
        final String authHeader = request.headers().firstHeader(JwtConstant.JWT_HEADER);
        if (authHeader == null || !authHeader.startsWith(JwtConstant.TOKEN_PREFIX)) {
            throw new UnauthorizedException("missing or invalid authorization token");
        }
        final String token = authHeader.substring(JwtConstant.TOKEN_PREFIX.length());

        // verify + lấy danh tính một lần; token thiếu/sai kiểu claim cũng bị từ chối, không forward "null"
        final JwtUtil.Identity identity = this.jwtUtil.parseIdentity(token)
                .orElseThrow(() -> new UnauthorizedException("invalid or expired JWT token"));
        final String email = identity.email();
        final String authorities = identity.authorities();
        final UUID userId = identity.userId();

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
