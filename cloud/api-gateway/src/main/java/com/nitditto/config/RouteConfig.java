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
    private static final Pattern CUSTOMER_WRITE_PATH = Pattern.compile("^/api/seat-instances/(hold|release)/?$");
    // confirm-booked / free: booking-service gọi qua Feign nội bộ, seat-service không kiểm tra hãng sở hữu ghế. Cho OWNER
    // gọi qua gateway là để một hãng giải phóng/chốt ghế của hãng khác -> chỉ ADMIN. So khớp chuỗi con như payment.
    private static final List<String> INTERNAL_SEAT_SEGMENTS = List.of("confirm-booked", "/free");
    // payment-service /initiate (nhận userId và amount từ body) và /batch-by-ids (tra payment của booking bất kỳ) chỉ
    // booking-service gọi qua Feign nội bộ, không qua gateway; qua gateway chỉ ADMIN. So khớp theo chuỗi con (không
    // phân biệt hoa thường) để đường dẫn biến thể như /api/payments/./initiate hay /initiate;x cũng không lọt.
    private static final List<String> INTERNAL_PAYMENT_SEGMENTS = List.of("initiate", "batch-by-ids");
    // booking-service /{id}/confirm-payment: payment-service gọi qua Eureka (không qua gateway). Response là TOÀN BỘ chi
    // tiết booking nên cho user thường gọi qua gateway là lộ dữ liệu booking của người khác; qua gateway chỉ ADMIN.
    private static final List<String> INTERNAL_BOOKING_SEGMENTS = List.of("confirm-payment");
    private static final Pattern AIRLINE_LISTING_PATH = Pattern.compile("^/api/airlines/?$");

    /**
     * Endpoint đọc dành cho khách chưa đăng nhập (tìm chuyến bay trên trang chủ). Danh sách này cố ý
     * hẹp và khai báo tường minh: thêm đường dẫn mới là một quyết định bảo mật, không phải mặc định.
     * POST /api/fares/search là POST nhưng chỉ tra giá (không ghi), giống ngoại lệ batch-by-ids.
     */
    private static final List<PublicRead> PUBLIC_READS = List.of(
            new PublicRead(HttpMethod.GET, Pattern.compile("^/api/airports(/[^/]+|/city/[^/]+)?/?$")),
            new PublicRead(HttpMethod.GET, Pattern.compile("^/api/flight-instances/search/?$")),
            new PublicRead(HttpMethod.GET, Pattern.compile("^/api/flights/search/?$")),
            new PublicRead(HttpMethod.GET, Pattern.compile("^/api/cabin-classes/aircraft/[^/]+/?$")),
            new PublicRead(HttpMethod.POST, Pattern.compile("^/api/fares/search/?$")),
            new PublicRead(HttpMethod.GET, Pattern.compile("^/api/payments/check-payment-vnpay/?$")),
            // IPN của VNPay (server gọi server, không có JWT): payment-service kiểm chữ ký trước khi làm gì.
            new PublicRead(HttpMethod.GET, Pattern.compile("^/api/payments/vnpay-ipn/?$")));

    private record PublicRead(HttpMethod method, Pattern path) {
    }

    private static final UserRole[] ADMIN = {UserRole.ROLE_SYSTEM_ADMIN};
    private static final UserRole[] ADMIN_OR_OWNER = {UserRole.ROLE_SYSTEM_ADMIN, UserRole.ROLE_AIRLINE_OWNER};
    private static final UserRole[] ANY_AUTHENTICATED = {};

    private final JwtUtil jwtUtil;

    private static final String FORWARDED_FOR = "X-Forwarded-For";

    // false (mặc định): gateway là điểm vào của Internet nên IP khách = địa chỉ của kết nối TCP, bỏ qua mọi X-Forwarded-For
    // client tự gửi (chống giả IP). true: gateway đứng sau reverse proxy tin cậy (nginx...) đã ghi X-Forwarded-For; lấy địa chỉ đầu.
    private final boolean trustForwardedFor;

    public RouteConfig(final JwtUtil jwtUtil,
            @org.springframework.beans.factory.annotation.Value("${gateway.client-ip.trust-forwarded-for:false}")
            final boolean trustForwardedFor) {
        this.jwtUtil = jwtUtil;
        this.trustForwardedFor = trustForwardedFor;
    }

    @Bean
    public RouterFunction<ServerResponse> authRoutes() {
        return GatewayRouterFunctions.route("auth-routes")
                .route(RequestPredicates.path("/auth/**"), HandlerFunctions.http())
                .before(request -> this.withClientIp(this.stripIdentityHeaders(request)))
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
        return this.route("seat-service-routes", "seat-service", this::seatAccess,
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
        return this.route("booking-service-routes", "booking-service", this::bookingAccess,
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
        return builder.before(request -> this.withClientIp(this.isPublicRead(request)
                        ? this.stripIdentityHeaders(request)
                        : access.apply(request)))
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

    // Hoàn tiền (yêu cầu, retry, danh sách): booking-service gọi qua Feign nội bộ; qua gateway chỉ ADMIN, MỌI method (kể
    // cả GET danh sách, vì lộ thông tin hoàn tiền của người khác). Khớp theo chuỗi con như INTERNAL_PAYMENT_SEGMENTS.
    private static final List<String> ADMIN_ONLY_PAYMENT_SEGMENTS = List.of("refund");

    /** Thanh toán: đăng nhập là đủ, trừ các lệnh nội bộ (khởi tạo thanh toán, tra theo danh sách booking, hoàn tiền) chỉ dành cho ADMIN. */
    private ServerRequest paymentAccess(final ServerRequest request) {
        final ServerRequest authenticated = this.secured(request, ANY_AUTHENTICATED);
        final String path = authenticated.path().toLowerCase(java.util.Locale.ROOT);
        if (ADMIN_ONLY_PAYMENT_SEGMENTS.stream().anyMatch(path::contains)) {
            return this.requireAnyRole(authenticated, ADMIN);
        }
        if (HttpMethod.POST.equals(authenticated.method())
                && INTERNAL_PAYMENT_SEGMENTS.stream().anyMatch(path::contains)) {
            return this.requireAnyRole(authenticated, ADMIN);
        }
        return authenticated;
    }

    /** Booking: đăng nhập là đủ (ownership kiểm tra ở service), trừ lệnh nội bộ xác nhận thanh toán chỉ dành cho ADMIN. */
    private ServerRequest bookingAccess(final ServerRequest request) {
        final ServerRequest authenticated = this.secured(request, ANY_AUTHENTICATED);
        final String path = authenticated.path().toLowerCase(java.util.Locale.ROOT);
        if (INTERNAL_BOOKING_SEGMENTS.stream().anyMatch(path::contains)) {
            return this.requireAnyRole(authenticated, ADMIN);
        }
        return authenticated;
    }

    /** Ghế: ghi cần ADMIN/OWNER (trừ hold/release của khách); lệnh nội bộ chốt/giải phóng ghế chỉ dành cho ADMIN. */
    private ServerRequest seatAccess(final ServerRequest request) {
        final ServerRequest authenticated = this.secured(request, ADMIN_OR_OWNER);
        final String path = authenticated.path().toLowerCase(java.util.Locale.ROOT);
        if (INTERNAL_SEAT_SEGMENTS.stream().anyMatch(path::contains)) {
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

    /**
     * Ghi đè {@code X-Forwarded-For} bằng IP thật của khách để service phía sau (payment-service: vnp_IpAddr) dùng. Luôn
     * {@code set} (không {@code add}) nên giá trị client tự gửi không bao giờ đi tiếp trừ khi {@code trustForwardedFor}.
     */
    ServerRequest withClientIp(final ServerRequest request) {
        final String ip = this.clientIpOf(request);
        return ServerRequest.from(request).headers(headers -> {
            if (ip == null) {
                headers.remove(FORWARDED_FOR);
            } else {
                headers.set(FORWARDED_FOR, ip);
            }
        }).build();
    }

    private String clientIpOf(final ServerRequest request) {
        if (this.trustForwardedFor) {
            final String forwarded = request.headers().firstHeader(FORWARDED_FOR);
            if (forwarded != null) {
                final String first = forwarded.split(",", 2)[0].trim();
                if (ClientIpFormat.isIpLiteral(first)) {
                    return first;
                }
            }
        }
        return request.remoteAddress().map(address -> address.getAddress() == null ? null
                : address.getAddress().getHostAddress()).orElse(null);
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
