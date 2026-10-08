# KnowledgeDrop API

Spring Boot template for **KnowledgeDrop**, a drop box API for building a searchable knowledge base
(hybrid vector + keyword retrieval merged with reciprocal rank fusion). This is the API component
only. Endpoint logic that is not built yet is marked `TODO(<endpoint>)` and returns
`501 Not Implemented`. The AI-dependent endpoints are additionally marked **DEFERRED** until an
LLM/embedding provider is chosen.

## Quick start

Requires JDK 17+ and a PostgreSQL database. No Maven install needed; use the wrapper.

```bash
# PostgreSQL for local dev (defaults match src/main/resources/application.yml)
docker run -d --name kd-postgres -p 5432:5432 \
  -e POSTGRES_DB=knowledgedrop -e POSTGRES_USER=knowledgedrop -e POSTGRES_PASSWORD=knowledgedrop \
  postgres:16

./mvnw verify            # compile, test, JaCoCo report, Checkstyle, PMD
./mvnw spring-boot:run   # start on http://localhost:8080
```

Override the database with `DB_URL`, `DB_USER`, `DB_PASSWORD`. **Tests do not need PostgreSQL**: they
run against in-memory H2 (`application-test.yml`).

Redis and Elasticsearch are not needed yet (connections are lazy). You will need them when the
ingestion/retrieval TODOs are built: **Redis Stack** (RediSearch is required for vector search) on
`localhost:6379` and **Elasticsearch** on `http://localhost:9200`. Override with `REDIS_HOST`,
`REDIS_PORT`, `ELASTICSEARCH_URIS`.

> On Windows, if you commit `mvnw`, keep it executable: `git update-index --chmod=+x mvnw`.

## Endpoint status

All paths are under `/knowledgeDrop`. Everything except `registerClient` and `health` needs
`Authorization: Bearer <client token>` and returns `401` otherwise.

| Endpoint | Status | Implement in |
|---|---|---|
| `POST registerClient` | **Built** (reference implementation) | `ClientService.register` |
| `POST create` | TODO(create) | `DocumentService.create` |
| `PUT update` | TODO(update) | `DocumentService.update` |
| `GET read` | TODO(read) | `DocumentService.read` |
| `DELETE delete` | TODO(delete) | `DocumentService.delete` |
| `GET list` | TODO(list) | `DocumentService.list` |
| `DELETE clientDrop` | TODO(clientDrop) | `ClientService.drop` |
| `GET health` | TODO(health) | `HealthService.check` |
| Ingestion pipeline (extract, chunk, keyword index) | TODO(ingestion) | `IngestionService` |
| `POST ask` | **DEFERRED** (LLM + embeddings) | `QaService.ask` |
| `POST similarity` | **DEFERRED** (embeddings) | `QaService.similarity` |
| `POST compare` | **DEFERRED** (embeddings) | `SearchService.compare` |
| `POST eval` | **DEFERRED** (embeddings) | `SearchService.eval` |
| `POST summarize` | **DEFERRED** (LLM) | `SummarizationService.summarize` |
| Chunk embeddings in Redis, RRF | **DEFERRED** | `IngestionService`, shared retrieval component |

Find every open item: `grep -rn "TODO(" src/main`. Matching disabled tests live in
`EndpointTodoTests`; move each into a real test class as you build the endpoint.

## Uploading documents (create / update)

The request is `multipart/form-data` with two parts:

- `metadata`: JSON (`Content-Type: application/json`)
- `file`: the document as a raw binary part (`application/octet-stream`)

`create` metadata: `{"name": "...", "type": "pdf|text|image"}` (optionally `"text": "..."` for raw
text instead of a file). `update` metadata: `{"documentId": "..."}` **or**
`{"documentName": "..."}`, plus the new content. Responses are JSON. `create`/`update` return
`202 Accepted` with the document ID and status `processing`, because ingestion runs in the
background (status later becomes `ready` or `failed`).

## Try it

```bash
curl -s -X POST localhost:8080/knowledgeDrop/registerClient \
  -H 'Content-Type: application/json' -d '{"clientName":"demo"}'
# => {"clientId":"...","clientToken":"..."}   (token is shown once)

TOKEN=<clientToken>
curl -s localhost:8080/knowledgeDrop/list -H "Authorization: Bearer $TOKEN"   # 501 until built

# create: JSON metadata part + binary file part
curl -s -X POST localhost:8080/knowledgeDrop/create -H "Authorization: Bearer $TOKEN" \
  -F 'metadata={"name":"policy.pdf","type":"pdf"};type=application/json' \
  -F 'file=@policy.pdf;type=application/octet-stream'                          # 501 until built
```

Errors always look like `{"error": "Not Found", "reason": "..."}`.

## Layout

```
src/main/java/dev/coms4156/knowledgedrop/
  api/          controllers (wiring, params, status codes) + api/dto request/response records
  service/      business logic; stubs carry the TODOs
  model/        JPA entities (ClientRecord, DocumentRecord) and enums
  repository/   Spring Data JPA repositories (always scoped to the owning client)
  security/     AuthInterceptor (Bearer token -> client), TokenUtils (generate + SHA-256 hash)
  exception/    ApiException, ErrorResponse, GlobalExceptionHandler
  config/       WebConfig (auth on /knowledgeDrop/**), AsyncConfig, KnowledgeDropProperties
```

Adding an endpoint: fill in the service method (the controller, DTOs, auth, and error handling are
already wired). `ClientService.register` plus `ClientServiceTest` and `ApiAuthIntegrationTest` show
the pattern.

## Tooling

| Purpose | Tool | Notes |
|---|---|---|
| CI | GitHub Actions | `.github/workflows/ci.yml` runs `./mvnw verify` and uploads the JaCoCo report |
| Build | Maven via wrapper | `./mvnw` (wrapper 3.3.x, script-only, no jar committed) |
| Unit / API tests | JUnit 5, Mockito, MockMvc | `./mvnw test` |
| Coverage | JaCoCo | `target/site/jacoco/index.html` |
| Style | Checkstyle (Google Java Style) | runs at `verify` |
| Static analysis | PMD | runs at `verify` |
| Network API tests | Postman + curl | use the examples above |
| IDE | VS Code | `.vscode/extensions.json` recommends extensions |

**Checkstyle, PMD, and the JaCoCo gate are non-blocking by default** so the first CI run is not red.
Once the team has a clean run, enforce them in `pom.xml` `<properties>`:

```xml
<quality.failOnViolation>true</quality.failOnViolation>
<quality.checkstyle.severity>warning</quality.checkstyle.severity>
<jacoco.line.coverage.min>0.80</jacoco.line.coverage.min>
```

## Design assumptions (change freely)

- Spring Boot 4.1 on Java 17; package `dev.coms4156.knowledgedrop`.
- Auth: `Authorization: Bearer <token>`. Only a SHA-256 hash of the token is stored.
- Records (clients, documents) in PostgreSQL via JPA; `ddl-auto: update` for dev, so add Flyway (or
  similar) migrations before a real deployment. Original uploads go under `./data/files`.
- Documents are identified by `documentId` or `documentName` (query params for read/delete, metadata
  fields for update); names are unique per client.
- 501 stubs return the normal JSON error body so the API contract is testable before the logic.
