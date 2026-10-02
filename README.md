# Career Tracker API

[![CI](https://github.com/Schengii/career-tracker-api/actions/workflows/ci.yml/badge.svg)](https://github.com/Schengii/career-tracker-api/actions/workflows/ci.yml)

REST API to track job applications through their whole lifecycle, built with **Java 21**, **Spring Boot 3**, **Spring Data JPA** and tested with **JUnit 5**, **Mockito** and **MockMvc**.

It is the Java backend counterpart to my full-stack [Job Application & Career Manager](https://github.com/Schengii/job-application-career-manager): same domain, different stack, built to show clean layering and test discipline in plain Spring Boot.

## Features

- CRUD for job applications with Bean Validation (`400` with per-field errors)
- Status workflow enforced in the domain model: `APPLIED → INTERVIEW → OFFER → ACCEPTED`, with `REJECTED` / `WITHDRAWN` as exits. Invalid transitions return `409 Conflict`; terminal states cannot be left
- Filter by status, statistics per status
- RFC 9457 problem details for all errors
- OpenAPI / Swagger UI out of the box
- H2 in-memory by default, PostgreSQL via profile
- Dockerfile (multi-stage) and GitHub Actions CI (`mvn verify`)

## Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/applications` | Create (`201` + `Location` header) |
| `GET` | `/api/applications?status=INTERVIEW` | List, optionally filtered |
| `GET` | `/api/applications/{id}` | Get one (`404` if unknown) |
| `PUT` | `/api/applications/{id}` | Update (`409` on invalid status transition) |
| `DELETE` | `/api/applications/{id}` | Delete (`204`) |
| `GET` | `/api/applications/stats` | Count per status |

Swagger UI: `http://localhost:8080/swagger-ui.html`

## Quick start

```bash
mvn spring-boot:run
```

```bash
curl -X POST http://localhost:8080/api/applications \
  -H "Content-Type: application/json" \
  -d '{"company":"ACME","position":"Junior Java Developer"}'

curl -X PUT http://localhost:8080/api/applications/1 \
  -H "Content-Type: application/json" \
  -d '{"company":"ACME","position":"Junior Java Developer","status":"INTERVIEW"}'

curl http://localhost:8080/api/applications/stats
```

### PostgreSQL

```bash
DB_URL=jdbc:postgresql://localhost:5432/careertracker DB_USER=postgres DB_PASSWORD=... \
  mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

### Docker

```bash
docker build -t career-tracker-api .
docker run -p 8080:8080 career-tracker-api
```

## Tests

```bash
mvn verify
```

| Layer | Approach |
|---|---|
| Domain (`JobStatus`) | Plain JUnit 5, parameterized over all statuses |
| Service | Mockito, no Spring context |
| API | `@SpringBootTest` + MockMvc against in-memory H2, transactional rollback per test |

## Project structure

```text
src/main/java/de/schenk/careertracker/
├── domain/        JobApplication entity, JobStatus workflow
├── repository/    Spring Data JPA repository
├── service/       Business rules, domain exceptions
└── web/           Controller, DTO records, exception handler
```

## Design decisions

- **Workflow in the enum, not the controller**: `JobStatus.canTransitionTo` keeps the rules next to the data and makes them trivially unit-testable.
- **DTO records** for request/response keep the entity out of the API surface.
- **No hardcoded secrets**: database credentials come from environment variables.
- **`open-in-view` disabled** to avoid lazy-loading surprises in the web layer.

## Ideas for next steps

- Testcontainers-based PostgreSQL integration test
- Pagination and sorting for the list endpoint
- Authentication (Spring Security + JWT)
