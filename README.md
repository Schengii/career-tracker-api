# Career Tracker API

[![CI](https://github.com/Schengii/career-tracker-api/actions/workflows/ci.yml/badge.svg)](https://github.com/Schengii/career-tracker-api/actions/workflows/ci.yml)

REST API to track job applications through their whole lifecycle, built with **Java 21**, **Spring Boot 3**, **Spring Data JPA**, **Spring Security (JWT)** and tested with **JUnit 5**, **Mockito**, **MockMvc** and **Testcontainers**.

It is the Java backend counterpart to my full-stack [Job Application & Career Manager](https://github.com/Schengii/job-application-career-manager): same domain, different stack, built to show clean layering and test discipline in plain Spring Boot.

## Features

- CRUD for job applications with Bean Validation (`400` with per-field errors)
- Status workflow enforced in the domain model: `APPLIED → INTERVIEW → OFFER → ACCEPTED`, with `REJECTED` / `WITHDRAWN` as exits. Invalid transitions return `409 Conflict`; terminal states cannot be left
- **Authentication**: stateless Spring Security with JWT (HS256) - register, login, bearer token
- **Per-user data**: every application belongs to its owner; other users' data behaves like `404`
- **Pagination and sorting** (`page`, `size` up to 100, whitelisted `sort` properties), filter by status, statistics per status
- RFC 9457 problem details for all errors
- OpenAPI / Swagger UI out of the box
- H2 in-memory by default, PostgreSQL via profile (also covered by a Testcontainers integration test)
- Dockerfile (multi-stage) and GitHub Actions CI (`mvn verify`)

## Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/auth/register` | public | Register (`201`, `409` if the username is taken, `400` on weak input) |
| `POST` | `/api/auth/login` | public | Returns a bearer token (`401` on wrong credentials) |
| `POST` | `/api/applications` | Bearer | Create (`201` + `Location` header) |
| `GET` | `/api/applications?status=INTERVIEW&page=0&size=20&sort=company,asc` | Bearer | Paged list, optionally filtered |
| `GET` | `/api/applications/{id}` | Bearer | Get one (`404` if unknown or owned by someone else) |
| `PUT` | `/api/applications/{id}` | Bearer | Update (`409` on invalid status transition) |
| `DELETE` | `/api/applications/{id}` | Bearer | Delete (`204`) |
| `GET` | `/api/applications/stats` | Bearer | Count per status |

Paged responses have the shape `{content, page, size, totalElements, totalPages}`. Sortable properties: `id`, `company`, `position`, `status`, `appliedAt` (default: newest first).

Swagger UI (with an *Authorize* button for the bearer token): `http://localhost:8080/swagger-ui.html`

## Quick start

```bash
export JWT_SECRET="at-least-32-bytes-long-random-secret-value"   # optional for local dev
mvn spring-boot:run
```

Without `JWT_SECRET` a random key is generated on every start (fine for development, but tokens become invalid after a restart).

```bash
# 1) register and log in
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"max","password":"supersecret1"}'

TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"max","password":"supersecret1"}' | jq -r .accessToken)

# 2) use the API
curl -X POST http://localhost:8080/api/applications \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"company":"ACME","position":"Junior Java Developer"}'

curl -X PUT http://localhost:8080/api/applications/1 \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"company":"ACME","position":"Junior Java Developer","status":"INTERVIEW"}'

curl -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/applications?size=10&sort=company,asc"
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/applications/stats
```

### PostgreSQL

```bash
DB_URL=jdbc:postgresql://localhost:5432/careertracker DB_USER=postgres DB_PASSWORD=... \
  JWT_SECRET=... mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

### Docker

```bash
docker build -t career-tracker-api .
docker run -p 8080:8080 -e JWT_SECRET="$JWT_SECRET" career-tracker-api
```

## Tests

```bash
mvn verify
```

| Layer | Approach |
|---|---|
| Domain (`JobStatus`) | Plain JUnit 5, parameterized over all statuses |
| Services | Mockito, no Spring context (ownership, defaults, password hashing, credential checks) |
| API | `@SpringBootTest` + MockMvc against in-memory H2, transactional rollback per test; authenticated via the `jwt()` test post-processor (pagination, sorting, ownership isolation, `401`) |
| Auth | Real register/login flow with a really signed token, tampered-token rejection |
| PostgreSQL | **Testcontainers** (`postgres:16-alpine`, `@ServiceConnection`): full register - login - create - page - stats workflow against a real database. Needs Docker; skipped automatically if none is available |

## Project structure

```text
src/main/java/de/schenk/careertracker/
├── domain/        JobApplication and AppUser entities, JobStatus workflow
├── repository/    Spring Data JPA repositories (owner-scoped queries)
├── security/      Security filter chain, JWT encoder/decoder, token issuing
├── service/       Business rules, authentication, domain exceptions
└── web/           Controllers, DTO records, exception handler, OpenAPI config
```

## Design decisions

- **Workflow in the enum, not the controller**: `JobStatus.canTransitionTo` keeps the rules next to the data and makes them trivially unit-testable.
- **DTO records** for request/response keep the entity out of the API surface.
- **No hardcoded secrets**: database credentials come from environment variables.
- **`open-in-view` disabled** to avoid lazy-loading surprises in the web layer.
- **Stateless JWT via Spring's OAuth2 resource server**: the API issues HS256 tokens itself (Nimbus) and validates them with the standard resource-server filter, so there is no hand-rolled filter code. Swapping in an external identity provider later only means replacing the `JwtDecoder` bean.
- **Ownership in the query, not after the fact**: repositories look up by `id` *and* `owner`, so another user's record is indistinguishable from a missing one (no information leak via `403` vs. `404`).
- **Passwords**: BCrypt hashes only; unknown user and wrong password return the same `401`.
- **Sort whitelist**: clients can only sort by public properties (never by `owner`), invalid values return `400`.

## Ideas for next steps

- Refresh tokens and token revocation
- Rate limiting on the login endpoint
- Flyway migrations instead of `ddl-auto=update` for production
