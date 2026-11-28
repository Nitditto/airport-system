# Redis cache cho Airport/City (`location-service`)

**Code:** `location-service` — `config/RedisCacheConfig`, `config/AirportBatchCache`, `config/EvictLocationCaches`,
`service/impl/AirportServiceImpl`, `service/impl/CityServiceImpl`. Test: `AirportServiceCacheTest`.
Đưa vào ở PR #76.

## 5W1H

| | |
|---|---|
| **What** | Cache-aside (lazy loading) dữ liệu sân bay/thành phố trên Redis, dùng Spring Cache (`@Cacheable`) và một đường batch riêng cho `batch-by-ids`. |
| **Why** | Airport/City gần như không đổi nhưng bị đọc liên tục: frontend gọi `GET /api/airports` mỗi lần mở form tìm chuyến, flight-ops gọi `POST /api/airports/batch-by-ids` qua Feign mỗi lần tìm chuyến và dựng danh sách chuyến bay. Trước đây mọi lần đọc đều query MySQL. |
| **Who** | Chỉ `location-service` đọc/ghi Redis. Bên gọi (frontend, flight-ops) không biết cache tồn tại và không phải đổi gì. |
| **When** | Đọc: mọi API đọc Airport theo id / theo city / toàn bộ, City theo id. Xoá: mọi thao tác tạo/sửa/xoá Airport hoặc City. |
| **Where** | Redis riêng của hệ thống (container `redis`), key có tiền tố `location:`. |
| **How** | Hit → trả từ Redis. Miss → query MySQL, ghi vào Redis với TTL 12h, rồi trả về. Ghi dữ liệu → xoá sạch các cache location. |

## Vì sao là Redis, không phải cache trong RAM của service

Cache trong JVM (`ConcurrentHashMap`, Caffeine) nằm riêng ở **từng instance**. Khi location-service chạy nhiều bản sau
load balancer, admin sửa một sân bay trên instance A thì instance B vẫn giữ bản cũ tới hết TTL, và mỗi instance phải tự
nạp cache từ đầu sau mỗi lần restart/deploy.

Redis là cache **dùng chung (distributed)** cho mọi instance:

```
                     ┌────────────── location-service (instance A) ──┐
frontend ─┐          │                                               │
          ├─▶ gateway ┤   @Cacheable / AirportBatchCache ──┐          │
flight-ops┘ (Feign)  │                                    ▼          │
                     └────────────── location-service (instance B) ──┤
                                                          │          │
                                                   ┌──────▼──────┐   │
                                                   │    Redis    │◀──┘  một bản dữ liệu cho mọi instance
                                                   └──────┬──────┘
                                                     miss │
                                                   ┌──────▼──────┐
                                                   │    MySQL    │  airline_location (nguồn sự thật)
                                                   └─────────────┘
```

- Evict ở một instance là mọi instance thấy ngay.
- Deploy/restart service không làm mất cache.
- MySQL vẫn là nguồn sự thật. Redis mất dữ liệu (restart container, bị evict vì hết RAM) chỉ làm request chậm hơn,
  không làm sai dữ liệu.

## Cache nào, chứa gì

| Cache (tên) | Method | Key Redis | Giá trị |
|---|---|---|---|
| `airport` | `AirportServiceImpl.getAirportById(id)` và `getAirportsByIds(ids)` | `location:airport::<uuid>` | `AirportResponse` |
| `airports` | `AirportServiceImpl.getAllAirports()` | `location:airports::…` (một key duy nhất, tên do Spring sinh cho method không tham số) | `ArrayList<AirportResponse>` |
| `airportsByCity` | `AirportServiceImpl.getAirportsByCityId(cityId)` | `location:airportsByCity::<uuid>` | `ArrayList<AirportResponse>` |
| `city` | `CityServiceImpl.getCityById(id)` | `location:city::<uuid>` | `CityResponse` |

Tên cache là hằng số trong `RedisCacheConfig` (`AIRPORT`, `AIRPORTS`, `AIRPORTS_BY_CITY`, `CITY`).

**Không cache** (cố ý):

- `getAllCities`, `searchCities`, `getCitiesByCountryCode`: trả `Page<CityResponse>`. Key sẽ phụ thuộc trang/size/sort/keyword
  (hit rate thấp), và `Page` khó serialize/deserialize đúng bằng JSON.
- Mọi dữ liệu động ở service khác (chuyến bay, giá, ghế): đổi liên tục, cache sẽ trả dữ liệu sai.

Chỉ cache **DTO** (`AirportResponse`, `CityResponse` trong `common-lib`), không bao giờ cache entity JPA.

## Cấu hình (`RedisCacheConfig`)

| Thiết lập | Giá trị | Lý do |
|---|---|---|
| TTL | 12 giờ, chung cho mọi cache (`RedisCacheConfig.TTL`) | Giới hạn thời gian dữ liệu cũ tồn tại nếu evict thất bại; dữ liệu gần như tĩnh nên không cần ngắn hơn. |
| Tiền tố key | `location:` | Tránh đụng key nếu service khác dùng chung Redis sau này; dễ lọc bằng `redis-cli --scan --pattern 'location:*'`. |
| Giá trị null | không cache (`disableCachingNullValues`) | Id không tồn tại ném `ResourceNotFoundException`, không phải null. |
| Key serializer | `StringRedisSerializer` UTF-8 | Key đọc được bằng mắt khi debug. |
| Value serializer | `GenericJacksonJsonRedisSerializer` (Jackson 3) có default typing | JSON đọc được, an toàn hơn JDK serialization. Xem mục dưới. |
| Lỗi Redis | `CacheErrorHandler` chỉ log | Xem [Chịu lỗi](#chịu-lỗi-khi-redis-chết). |

### Serializer và default typing

Giá trị lưu kèm kiểu để đọc lại đúng class, ví dụ:

```json
["java.util.ArrayList",[{"@class":"com.nitditto.payload.response.AirportResponse","id":"…","iataCode":"HAN",
  "timeZone":["java.time.ZoneId","Asia/Ho_Chi_Minh"], "city":{"@class":"com.nitditto.payload.response.CityResponse", …}}]]
```

- Chỉ cho phép giải mã class trong `com.nitditto.`, `java.util.`, `java.time.` (`BasicPolymorphicTypeValidator`).
  Không mở rộng danh sách này tuỳ tiện: default typing không giới hạn cho phép dữ liệu trong Redis chọn class bất kỳ
  để khởi tạo (lỗ hổng deserialization).
- **Danh sách cache phải là `ArrayList`, không được là `List.of()`/`Collections.emptyList()`.** Jackson không ghi type id
  cho lớp final (`ImmutableCollections$ListN`), nên ghi được nhưng **đọc lại lỗi** (`MismatchedInputException`).
  Vì vậy `AirportServiceImpl.toResponses` trả `new ArrayList<>()` cả khi rỗng. Method cache mới trả list thì giữ quy tắc này.

## Luồng đọc

### Một bản ghi / một danh sách: `@Cacheable`

```
getAirportById(id)
   │
   ├─ Spring CacheInterceptor: GET location:airport::<id>
   │     ├─ hit  → trả luôn, KHÔNG vào method, KHÔNG query DB
   │     └─ miss → chạy method (findById + nạp City) → SET key, TTL 12h → trả về
```

`@Cacheable` hoạt động qua proxy: chỉ có hiệu lực khi method được gọi **từ bean khác** (controller gọi service).
Gọi `this.getAirportById(...)` trong cùng class sẽ bỏ qua cache.

### Batch: `getAirportsByIds` (`POST /api/airports/batch-by-ids`)

Đây là endpoint flight-ops gọi nhiều nhất, tối đa 200 id mỗi lần. Đặt `@Cacheable` lên cả danh sách id thì hit rate rất
thấp (mỗi tổ hợp id là một key) và không evict theo từng sân bay được. Còn lặp từng id rồi gọi `cache.get` thì tốn tới
200 lượt Redis mỗi request. `AirportBatchCache` giải quyết bằng số lượt cố định:

```
ids (distinct, ≤ 200)
   │
   ├─ 1 × MGET location:airport::<id1> … <idN>           → các id đã có trong cache
   ├─ 1 × SELECT … WHERE id IN (id còn thiếu)              → chỉ id chưa có
   └─ 1 × pipeline SET (EX 12h) cho các id vừa nạp         → lần sau hit
```

- Dùng **cùng key và serializer** với `@Cacheable getAirportById`, nên hai đường dùng chung dữ liệu: sân bay nạp qua
  batch thì `GET /api/airports/{id}` hit, và ngược lại.
- Key lấy qua `RedisCacheConfiguration.getKeyPrefixFor("airport")`, không tự ghép chuỗi, để không lệch định dạng với Spring.
- Key nằm trong không gian tên của cache `airport` nên `@EvictLocationCaches` xoá được luôn.

## Xoá cache (invalidation)

Mọi method ghi gắn `@EvictLocationCaches`:

| Method | Service |
|---|---|
| `createAirport`, `updateAirport`, `deleteAirportById` | `AirportServiceImpl` |
| `createCity`, `updateCity`, `deleteCityById` | `CityServiceImpl` |

`@EvictLocationCaches` là `@CacheEvict(allEntries = true)` trên cả 4 cache. Xoá sạch thay vì xoá đúng key vì:

- Dữ liệu ghi rất hiếm (thao tác admin), mất toàn bộ cache vài lần mỗi ngày không đáng kể.
- `AirportResponse` nhúng `CityResponse`. Sửa tên một thành phố phải làm mới mọi sân bay của nó, trong cả
  `airport`, `airports` và `airportsByCity`. Tính đúng tập key cần xoá phức tạp và dễ sót.

## Chịu lỗi khi Redis chết

Redis là tầng tăng tốc, không được làm sập API.

| Tình huống | Hành vi |
|---|---|
| Redis không kết nối được khi đọc (`@Cacheable`) | `CacheErrorHandler.handleCacheGetError` log `warn`, coi như miss → đọc MySQL. Request vẫn 200, chỉ chậm hơn. |
| Ghi cache lỗi | Log `warn`, bỏ qua. |
| Evict/clear lỗi | Log `error`: cache có thể còn dữ liệu cũ **tới khi hết TTL 12h**. Theo dõi log này. |
| Batch MGET/pipeline lỗi | `AirportBatchCache` tự bắt lỗi (không đi qua `CacheErrorHandler`), log `warn`, coi như miss toàn bộ. |
| Redis chậm | `spring.data.redis.timeout: 500ms` giới hạn thời gian chờ mỗi lệnh. |

Trong compose, location-service `depends_on: redis` với `condition: service_started` (không chờ healthy), vì service vẫn
chạy được khi Redis chưa lên.

## Chạy và cấu hình

**Docker compose (full stack):** service `redis` (`redis:7-alpine`, không publish cổng ra host, không volume).
Location-service nhận `SPRING_DATA_REDIS_HOST=redis`.

**Chạy từ IDE:** `docker compose -f docker-compose.dev.yml up -d redis` (publish `127.0.0.1:6379`).
`application.yaml` của location-service mặc định `localhost:6379`.

```yaml
spring:
  data:
    redis:
      host: localhost        # compose ghi đè bằng SPRING_DATA_REDIS_HOST
      port: 6379
      timeout: 500ms
  cache:
    type: redis
```

Redis cấu hình `--maxmemory 128mb --maxmemory-policy allkeys-lru`: đầy bộ nhớ thì tự bỏ key ít dùng nhất, không từ chối ghi.
Không có volume: restart container là mất cache, chấp nhận được vì MySQL là nguồn sự thật.

## Kiểm tra và debug

```bash
# vào redis (compose chính: không publish cổng nên dùng docker exec)
docker exec -it airline-redis redis-cli            # dev: airline-redis-dev, hoặc redis-cli -p 6379

--scan --pattern 'location:*'                       # liệt kê key của location-service
GET  "location:airport::<uuid>"                     # xem giá trị JSON
TTL  "location:airport::<uuid>"                     # còn bao nhiêu giây (~43200 ngay sau khi nạp)
DEL  "location:airport::<uuid>"                     # xoá tay một key (bản ghi sẽ nạp lại ở lần đọc sau)
```

Kiểm tra end-to-end:

1. Bật SQL log (`logging.level.org.hibernate.SQL: DEBUG`), gọi `GET /api/airports` hai lần qua gateway: lần 1 có SQL,
   lần 2 không.
2. `PUT /api/airports/{id}` (ADMIN), gọi lại: có SQL và dữ liệu mới; `--scan` thấy key cũ đã mất.
3. `docker stop airline-redis`, gọi `GET /api/airports`: vẫn 200, log có `Cache get failed`.

Test tự động (`mvn -pl services/location-service test`): `AirportServiceCacheTest` dùng `ConcurrentMapCacheManager`
(in-memory) và mock repository/`AirportBatchCache`, kiểm hit lần 2, evict sau khi ghi, batch chỉ query id còn thiếu.
Test này **không** chạy Redis thật, nên không bắt được lỗi serializer; đổi DTO hay serializer thì thử lại bằng Redis thật.

## Giới hạn và lưu ý

- **Evict có độ trễ nhỏ.** Với Spring Data Redis 4.0.1, đo được `clear()` chưa có hiệu lực ngay khi gọi
  (đọc ngay sau vẫn thấy key, sau dưới 500ms thì sạch). Đọc ngay sau khi admin sửa có thể thấy dữ liệu cũ trong khoảng đó.
  Chưa tìm nguyên nhân gốc.
- **Evict chạy cạnh transaction, không chờ commit.** Một request đọc chen giữa lúc evict và lúc commit có thể nạp lại
  bản cũ vào cache; TTL 12h giới hạn tác hại. Cần chặt hơn thì chuyển evict sang `@TransactionalEventListener(AFTER_COMMIT)`.
- **Cache stampede:** key hết hạn đúng lúc nhiều request cùng đọc thì tất cả cùng xuống DB. Với dữ liệu nhỏ và một lần
  hết hạn mỗi 12h, chưa đáng xử lý. Cần thì dùng `@Cacheable(sync = true)` cho từng method.
- **`getAllAirports` không phân trang** (TODO sẵn có). Cache giữ cả danh sách trong một key; số sân bay lớn lên nhiều thì
  phân trang trước rồi mới tính cache.
- **Một node Redis, không HA.** Redis chết thì hệ thống chạy tiếp nhưng toàn bộ đọc xuống MySQL.
- `CLAUDE.md` §1 ghi "không giả định tồn tại Redis": từ PR #76 Redis có ở **location-service** (chỉ làm cache).
  Service khác muốn dùng Redis vẫn phải hỏi trước.

## Thêm một cache mới (checklist)

1. Thêm hằng tên cache vào `RedisCacheConfig`.
2. Gắn `@Cacheable(cacheNames = …, key = "#…")` lên method **public của ServiceImpl**, được gọi từ bean khác.
3. Method trả DTO trong `com.nitditto.`; danh sách trả `ArrayList`.
4. Thêm tên cache vào `@EvictLocationCaches` và đảm bảo **mọi** đường ghi làm đổi dữ liệu đó đều gắn annotation này.
5. Không cache `Page` hay dữ liệu phụ thuộc người dùng.
6. Thêm ca vào `AirportServiceCacheTest` (hoặc test tương tự), rồi kiểm một lần với Redis thật (`GET` key, đọc lại được).
