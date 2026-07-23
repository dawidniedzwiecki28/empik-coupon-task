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

Requires **Docker**. Building and testing locally also needs **JDK 21** — the Gradle toolchain pins it,
so it's used even if your machine's default JDK differs.

**Option A — everything in Docker** (builds the app image and runs it alongside PostgreSQL):

```bash
docker compose up --build
```

**Option B — app on the host, PostgreSQL in Docker** (faster iteration):

```bash
docker compose up -d postgres      # schema is applied automatically by Flyway on app start
./gradlew bootRun                  # defaults match the compose database
```

Either way the service listens on `http://localhost:8080`; health is at `/actuator/health`.

```bash
# Build and run the full test suite (needs Docker for Testcontainers)
./gradlew build
```

## API

Interactive docs (when running): **Swagger UI** at `/swagger-ui.html`, OpenAPI document at
`/v3/api-docs`.

### Create a coupon — `POST /api/coupons`

```bash
curl -i -X POST localhost:8080/api/coupons \
  -H 'Content-Type: application/json' \
  -d '{"code":"WIOSNA","maxUses":100,"country":"US"}'
# 201 Created
# Location: /api/coupons/3f9a…
# {"couponId":"3f9a…"}
```

### Read a coupon — `GET /api/coupons/{id}`

```bash
curl localhost:8080/api/coupons/3f9a…
# 200 OK  →  {"id":"3f9a…","code":"WIOSNA","country":"US","maxUses":100,"currentUses":0,"createdAt":"…"}
# 404 with an application/problem+json body if no coupon has that id
```

### Redeem a coupon — `POST /api/coupons/redemptions`

The caller's country is resolved from their IP and must match the coupon's. On `localhost` the
connection address is loopback, which maps to no country, so a redemption **fails closed** (`422`) —
that is by design, not a bug. To exercise a successful redemption locally, start the app with
`COUPON_REST_TRUST_CLIENT_IP=true` and supply a public address via `X-Forwarded-For` (see
[Client IP & trust](#client-ip--trust)); `8.8.8.8` geolocates to the US in the bundled database, so it
matches the `US` coupon created above:

```bash
curl -X POST localhost:8080/api/coupons/redemptions \
  -H 'Content-Type: application/json' \
  -H 'X-Forwarded-For: 8.8.8.8' \
  -d '{"code":"WIOSNA","userId":"6b1e5a2c-0000-4000-8000-000000000001"}'
# 200 OK on success
```

`userId` is any client-supplied UUID (see [Limitations](#limitations)). Without the trust flag the IP
is taken from the connection; see [Client IP & trust](#client-ip--trust).

### Outcomes

Each outcome has a documented HTTP status. Every rejection carries an
[RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) `application/problem+json` body, and each kind has a
distinct `type` URI (e.g. `urn:coupon:limit-reached`) — so the two conflict cases that share `409` are
told apart by a machine on `type`, not by parsing `detail`.

| Outcome                  | Status | `type`                       | Notes                                             |
|--------------------------|--------|------------------------------|---------------------------------------------------|
| Redeemed                 | `200`  | —                            | no body                                           |
| Coupon not found         | `404`  | `urn:coupon:coupon-not-found`|                                                   |
| Usage limit reached      | `409`  | `urn:coupon:limit-reached`   |                                                   |
| Already redeemed by user | `409`  | `urn:coupon:already-redeemed`|                                                   |
| Country not allowed      | `403`  | `urn:coupon:country-not-allowed` | body includes `requiredCountry` and `callerCountry` |
| Caller country unknown   | `422`  | `urn:coupon:country-unresolved`  | the IP can't be mapped to a country — fail-closed, not a server outage |
| Malformed request        | `400`  | `urn:coupon:invalid-request` |                                                   |

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
  without value. The geo-IP resolver, by contrast, **is** a port (`GeoIpResolver` interface +
  `DatabaseGeoIpResolver` adapter): it fronts an external, swappable data source — the embedded
  database today, a hosted provider tomorrow — and is the one dependency worth faking in tests, so the
  seam earns its keep where the repositories' would not.
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
into memory at startup — a microsecond-scale in-process lookup with **no per-request network call and no
quota**. This deletes an entire class of production problems (rate limits, latency, retries, circuit
breakers) that a hosted geo-IP API would introduce, and is why holding the transaction across the
lookup is safe.

- **Fail-closed:** if the IP can't be mapped to a country, the redemption is rejected (`422`), never
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
`coupon.rest.trust-client-ip=true`. When trusted, the service takes the **leftmost** `X-Forwarded-For`
entry as the client — which is correct **only** behind an ingress/load balancer that **replaces** the
header with the real client address. Behind one that merely **appends** to a client-supplied header,
that leftmost entry is attacker-controlled, so enabling trust there would let a caller spoof their
country. Enable it solely behind a balancer you know overwrites the header; on a directly exposed
service, leave it off. This reflects the reality that a scalable deployment sits behind a load
balancer, where the remote address is the balancer's, not the client's.

## Observability

Every request is tagged with a **correlation id** (`RequestIdFilter`): the caller's `X-Request-Id` when
it is present and well-formed — validated to a short id charset, so a caller can't forge log lines or
bloat the logs — otherwise a generated UUID. The id is placed in the logging MDC, so **every log line
for a request carries it**, and echoed back on the response `X-Request-Id` header for client-side
correlation. Health is exposed at `/actuator/health`; other actuator endpoints (metrics, info) are
deliberately kept off the public HTTP surface — in a real deployment they'd move to a separate
management port rather than be exposed here.

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
  `ClientIpResolverTest`, and the geo-IP tests (`DatabaseGeoIpResolverTest`, `GeoIpDatabaseTest`,
  `GeoIpDatabaseUpdaterTest`).
- **Web slice** — `CouponControllerTest` (`@WebMvcTest`) maps every outcome, edge case and validation
  error to the right HTTP status/body, with the service mocked.
- **Domain integration (the primary test)** — `CouponOperationsTest` drives the full create/redeem
  behaviour through `CouponOperations` against a real PostgreSQL (Testcontainers), geo-IP faked;
  covers every outcome and the parallel-redemption concurrency proof.
- **End-to-end** — `CouponE2eTest` checks the main flows through the full stack (HTTP → domain →
  PostgreSQL) plus `OpenApiIntegrationTest` for the generated docs.
- **Architecture** — `ArchitectureTest` (ArchUnit) enforces the layer boundaries above.
- Coverage is a **build-failing gate at 93%**: `jacocoTestCoverageVerification` runs as part of
  `check`, so `./gradlew build` fails below **93%** line coverage (excluding framework wiring and data
  holders); CI additionally posts the number on each PR.

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

detekt's `config/detekt/detekt.yml` relaxes a handful of rules that are noise for this codebase — magic
numbers (self-evident domain constants), a wider max line length, a higher return-count ceiling,
`serialVersionUID`, an unused-parameter exception for `@ExceptionHandler` methods, and the idiomatic
`*args` spread — with a local `@Suppress` for the one genuinely-exceptional spot (the deliberate broad
catch in the geo-IP refresh). ktlint is intentionally not wired: the ktlint releases available don't
cleanly support this project's Kotlin 2.3 + tab indentation, and detekt's `style` ruleset already covers
that ground.

## Configuration

All settings have working defaults. The first group are environment variables bound to Spring
configuration properties (most through `application.yml`, `GEOIP_UPDATE_URL` via relaxed binding); the
last is a Spring property (set via `--coupon.rest.trust-client-ip=true` or the
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
