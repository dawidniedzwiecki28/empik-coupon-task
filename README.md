# Coupon Service

A REST service for managing discount coupons and registering their redemption, built for the
Empik backend recruitment task. **Kotlin · Spring Boot · PostgreSQL.**

The emphasis is on the qualities the task asks for: correctness under concurrency, a clear
architecture, and decisions that would hold up in a real production service — each explained below.

## Requirements coverage

- **Create a coupon** (no authentication) — unique code, creation date, max uses, current uses,
  target country.
- **Register a redemption** for a user.
- Code uniqueness is **case-insensitive** (`WIOSNA` == `wiosna`).
- Usage is capped by max uses, **"first come, first served"**, correct under concurrent load.
- The coupon's country restricts redemption to callers **from that country**, resolved from the
  caller's IP.
- **Distinct outcomes** for every case: not found, limit reached, wrong country, already redeemed.
- **One redemption per user per coupon** (the optional requirement) is implemented.

## Running it

Requires **JDK 21** and **Docker** (for PostgreSQL, and for the Testcontainers-based tests).

```bash
# 1. Start PostgreSQL (schema is applied automatically by Flyway on app start)
docker compose up -d postgres

# 2. Run the service (defaults match the compose database)
./gradlew bootRun
```

The service listens on `http://localhost:8080`. Health is at `/actuator/health`.

```bash
# Build and run the full test suite (needs Docker)
./gradlew build
```

## API

Interactive docs (when running): **Swagger UI** at `/swagger-ui.html`, OpenAPI document at
`/v3/api-docs`.

### Create a coupon — `POST /api/coupons`

```bash
curl -X POST localhost:8080/api/coupons \
  -H 'Content-Type: application/json' \
  -d '{"code":"WIOSNA","maxUses":100,"country":"PL"}'
# 201 Created  →  {"couponId":"3f9a…"}
```

### Redeem a coupon — `POST /api/coupons/redemptions`

```bash
curl -X POST localhost:8080/api/coupons/redemptions \
  -H 'Content-Type: application/json' \
  -d '{"code":"WIOSNA","userId":"6b1e…"}'
# 200 OK on success
```

`userId` is any client-supplied UUID (see [Limitations](#limitations)). The caller's IP is taken
from the connection by default; see [Client IP & trust](#client-ip--trust).

### Outcomes

Each outcome has a documented HTTP status. The two conflict cases share `409`, distinguished by the
`detail` of the [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) `application/problem+json` body
that every rejection carries.

| Outcome                  | Status | Notes                                                |
|--------------------------|--------|------------------------------------------------------|
| Redeemed                 | `200`  | no body                                              |
| Coupon not found         | `404`  |                                                      |
| Usage limit reached      | `409`  |                                                      |
| Already redeemed by user | `409`  |                                                      |
| Country not allowed      | `403`  | body includes `requiredCountry` and `callerCountry`  |
| Caller country unknown   | `503`  | geo-IP dependency couldn't resolve the country — fail-closed |
| Malformed request        | `400`  |                                                      |

## Architecture

One Spring Boot module, layered by package with dependencies pointing inward:

```
rest             HTTP edge: controllers, DTOs, RFC 9457 exception handling, client-IP resolution
  └─ core.api    the public contract: CouponOperations, value types, commands, RedemptionResult
       └─ core.domain             business logic (CouponOperationsImpl)
            └─ core.infrastructure   persistence (JPA) and geo-IP adapters
config           Spring wiring
```

- **`core.api` is the boundary.** `rest` depends on it; the implementation and all
  persistence/geo-IP details sit behind it. Expected redemption outcomes are a **sealed
  `RedemptionResult`** returned (not thrown), so the compiler enforces exhaustive HTTP mapping.
- **Pragmatic persistence, not hexagonal ceremony.** Spring Data repositories (`CouponRepository`,
  `CouponRedemptionRepository`) are used directly with the JPA entity as the in-module model. There
  is no separate port/adapter/pure-domain triple: for a single-datastore service it adds indirection
  without value. The geo-IP resolver follows the same principle — one concrete class, no interface,
  since its only other "implementation" would be a test double.
- **Value objects validate at construction** (`CouponCode`, `CountryCode`, `IpAddress`, `UserId`), so
  an invalid value can't travel inward. `userId` is a mandated UUID the service treats as an opaque
  identifier — it derives nothing from it and stores no other user data.

These boundaries are enforced by an ArchUnit test (`ArchitectureTest`), not just documented.

## Concurrency — the core of the task

The service holds **no JVM locks** and keeps no shared state; PostgreSQL is the single source of
truth. Two invariants are enforced entirely by atomic SQL inside one short transaction
(`CouponOperationsImpl.redeem` is `@Transactional`):

1. **Usage cap** — a conditional update, acting on rows-affected:

   ```sql
   UPDATE coupons SET current_uses = current_uses + 1
   WHERE id = :id AND current_uses < max_uses
   ```

   Postgres holds the row lock from this update until the transaction commits — a very short window,
   as the transaction contains only these statements and no external call. `0` rows → the coupon is
   full. The cap can never be exceeded, no matter how many requests race.

2. **One redemption per user** — a composite primary key `(coupon_id, user_id)` plus an
   **insert-first** `INSERT … ON CONFLICT DO NOTHING`, acting on rows-affected (not check-then-act,
   which would race). `0` rows → the user already redeemed.

Insert-first runs before the counter changes, giving already-redeemed precedence; if the coupon
turns out to be full, the tentative redemption is undone within the same transaction. Grouping the
insert + increment + compensating delete in one transaction is what makes that undo atomic — without
`@Transactional`, each repository write would commit separately and a crash mid-sequence could orphan
a redemption row.

Because the country lookup is a **local, in-memory database read** (see below), it adds no network
latency to the transaction. The application is stateless, so it **scales horizontally** — the
concurrency guarantees live in the database, not the process.

Requests run on **JDK 21 virtual threads** (`spring.threads.virtual.enabled`), so the blocking JDBC
calls scale to far more concurrent requests than a fixed platform-thread pool would — without adopting
a reactive stack. Our own code holds no JVM locks (a common source of carrier-thread pinning), though
transitive driver/framework code still can; either way the real ceiling is the database (connections,
per-coupon row contention), not application threads.

The behaviour is proven by a test that races many users for a limited coupon (`CouponOperationsTest`)
and asserts that *exactly* `maxUses` succeed.

## Geo-IP

Country is resolved from a **local IP→country database** (DB-IP Lite, MaxMind `.mmdb` format) loaded
into memory at startup — a sub-microsecond in-process lookup with **no per-request network call and no
quota**. This deletes an entire class of production problems (rate limits, latency, retries, circuit
breakers) that a hosted geo-IP API would introduce, and is why holding the transaction across the
lookup is safe.

- **Fail-closed:** if the IP can't be mapped to a country, the redemption is rejected (`503`), never
  allowed on an unverified country.
- **Bundled snapshot:** `src/main/resources/geoip/dbip-country-lite.mmdb.gz` (~4 MB) ships with the
  app, so it runs offline out of the box. Attribution is in [`NOTICE`](./NOTICE) (DB-IP Lite is
  CC-BY-4.0).
- **Refresh:** the recommended production approach is to bake a fresh database into the image and
  redeploy. An **opt-in** in-app updater (`GEOIP_UPDATE_URL`) can also fetch a fresh copy on startup
  and on a nightly cron, hot-swapping it in memory; it is off by default (so demo/CI run offline and
  deterministically), enforces HTTPS, bounds the download size, and falls back to the current database
  on any failure.

## Client IP & trust

The country check is only as trustworthy as the address it runs on. By default the service uses the
**transport remote address** only. The `X-Forwarded-For` header is honored **only** when
`coupon.rest.trust-client-ip=true` — which is safe solely behind an ingress/load balancer that
overwrites that header. Enabling it on a directly exposed service would let a caller spoof their
country. This reflects the reality that a scalable deployment sits behind a load balancer, where the
remote address is the balancer's, not the client's.

## Scaling further

The design is deliberately partition-ready without building it now:

- **Horizontal scaling adds no application coordination** — stateless pods, Postgres as the source of
  truth (throughput is then bounded by database capacity, connection pools, and lock contention, not
  the app). The geo-IP database is a small per-pod file (no shared cache to coordinate); at large
  fleet sizes it would be baked into the image or pulled by an init-container, still per-pod.
- **Sharding** — the redemption key includes `coupon_id`, so the table hash-partitions on it cleanly
  (time-partitioning would break the per-user uniqueness constraint).
- **Read/emit** — a `CouponFullyRedeemed` domain event published to Kafka when a coupon fills is a
  natural extension point; it is **not** built, because there is no consumer for it in scope and
  speculative infrastructure is a liability, not a feature.
- If a hosted geo-IP provider were ever required instead of the embedded database, a shared Redis
  cache in front of it would collapse per-pod duplication — but the embedded database makes that moot.

## Testing

A pyramid — each concern tested at the layer that owns it, without duplication (mocking uses MockK):

- **Unit** — the value objects (`CountryCodeTest`, `CouponCodeTest`, `IpAddressTest`),
  `ClientIpResolverTest`, and the geo-IP tests (`GeoIpResolverTest`, `GeoIpDatabaseTest`,
  `GeoIpDatabaseUpdaterTest`).
- **Web slice** — `CouponControllerTest` (`@WebMvcTest`) maps every outcome, edge case and validation
  error to the right HTTP status/body, with the service mocked.
- **Domain integration (the primary test)** — `CouponOperationsTest` drives the full create/redeem
  behaviour through `CouponOperations` against a real PostgreSQL (Testcontainers), geo-IP faked;
  covers every outcome and the parallel-redemption concurrency proof.
- **End-to-end** — `CouponE2eTest` checks the main flows through the full stack (HTTP → domain →
  PostgreSQL) plus `OpenApiIntegrationTest` for the generated docs.
- **Architecture** — `ArchitectureTest` (ArchUnit) enforces the layer boundaries above.
- Coverage is gated in CI at **93%** (JaCoCo), excluding framework wiring and data holders.

### Live acceptance & load check

`scripts/acceptance.sh` black-box-tests a **running** instance: it walks every create/redeem outcome,
proves the concurrency guarantee (500 users race for 50 slots → *exactly* 50 succeed), fires a
2000-request load burst, and prints a pass/fail summary. Start the app with
`COUPON_REST_TRUST_CLIENT_IP=true` (so the script can set the caller's country via `X-Forwarded-For`),
then:

```bash
./scripts/acceptance.sh                       # against http://localhost:8080
BASE=http://some-host:8080 ./scripts/acceptance.sh
```

Needs `bash`, `curl`, `python3`, `uuidgen`, `seq`, and `xargs` (with `-P`).

## Code review & quality gates

AI-assisted review (CodeRabbit, configured via `.coderabbit.yaml` with the architecture and
concurrency invariants encoded as path instructions) is used, but findings are **triaged critically,
not rubber-stamped** — accepted when they improve the code, and pushed back on with reasoning when the
existing decision is sound. Correctness is backed by **deterministic gates in CI** that all fail the
build when violated: **detekt** (code smells and style), the **ArchUnit** boundary test, and the
**JaCoCo** coverage threshold. Actions are SHA-pinned and credentials are not persisted.

detekt's `config/detekt/detekt.yml` relaxes only rules that are noise for this codebase (magic numbers
that are self-evident domain constants, the idiomatic `*args` spread), with local `@Suppress` for the
two genuinely-exceptional spots (the branchy IPv6 validator, the deliberate broad catch in the geo-IP
refresh). ktlint is intentionally not wired: the ktlint releases available don't cleanly support this
project's Kotlin 2.3 + tab indentation, and detekt's `style` ruleset already covers that ground.

## Configuration

All settings have working defaults. The first group are environment variables read by
`application.yml`; the last is a Spring property (set via `--coupon.rest.trust-client-ip=true` or the
`COUPON_REST_TRUST_CLIENT_IP` environment alias).

| Setting                       | Default                                   | Purpose                                          |
|-------------------------------|-------------------------------------------|--------------------------------------------------|
| `DB_URL`                      | `jdbc:postgresql://localhost:5432/coupon` | JDBC URL                                         |
| `DB_USERNAME` / `DB_PASSWORD` | `coupon` / `coupon`                       | Database credentials                             |
| `GEOIP_DATABASE_PATH`         | *(bundled snapshot)*                      | External `.mmdb` to use as the startup baseline  |
| `GEOIP_UPDATE_URL`            | *(unset — auto-update off)*               | Source for the opt-in database refresh           |
| `GEOIP_UPDATE_CRON`           | `0 0 3 * * *`                             | Refresh schedule when auto-update is enabled     |
| `coupon.rest.trust-client-ip` | `false`                                   | Honor `X-Forwarded-For` (only behind a trusted ingress) |

## Limitations

- **Authentication is out of scope** per the task. `userId` is client-supplied and only
  format-validated, so the per-user rule is best-effort; in production `userId` would come from an
  authenticated principal at the edge.
- **Geo-IP data freshness** — the bundled database is a point-in-time snapshot; keep it current via
  image redeploys or the opt-in updater.

## Tech

Kotlin, Spring Boot, PostgreSQL, Flyway, Spring Data JPA, MaxMind `geoip2` (DB-IP data), springdoc
OpenAPI, JUnit 5, Testcontainers, MockK, ArchUnit, detekt. Built with Gradle, targeting Java 21.
