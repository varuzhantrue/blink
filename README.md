# Blink 🚀

**Blink** is a high-performance, temporary file-sharing REST API built to simulate a production-grade cloud ecosystem.
It provides secure, S3-compatible object storage with automated infrastructure management and stateless JWT-based
authentication. Files are automatically purged after a configurable retention period (default 24 h) - Blink is designed
for transient sharing, not long-term storage.

---

## Tech Stack

| Layer                     | Technology                                |
|:--------------------------|:------------------------------------------|
| **Language**              | Java 21                                   |
| **Framework**             | Spring Boot 3.x (Web, Security, Data JPA) |
| **Object Storage**        | MinIO (S3-Compatible)                     |
| **Database**              | PostgreSQL                                |
| **Cache / Rate Limiting** | Redis                                     |
| **Authentication**        | JWT + BCrypt Password Hashing             |
| **DB Migrations**         | Flyway                                    |
| **Infrastructure**        | Docker & Docker Compose                   |
| **Build Tool**            | Gradle                                    |
| **API Docs**              | Springdoc OpenAPI / Swagger UI            |
| **Observability**         | Spring Actuator + Micrometer (Prometheus) |

---

## Features

- **S3-Compatible Storage** - Full integration with MinIO for robust file handling.
- **Zero-Touch Infrastructure** - Uses `spring-boot-docker-compose` to automatically start PostgreSQL and MinIO
  containers alongside the application; no manual `docker-compose up` required.
- **Stateless Security** - Enforces `STATELESS` session policy via a custom JWT filter chain, eliminating server-side
  session state entirely.
- **Collision-Safe File Mapping** - Stores files in MinIO under UUID-based object keys while preserving original
  filenames in PostgreSQL, preventing naming conflicts at scale.
- **Presigned URL Sharing** - Generates time-limited presigned URLs for secure, direct-to-MinIO file access without
  exposing internal credentials.
- **Automatic Expiry** - Files are purged hourly once they exceed the retention period (`blink.retention-period-hours`,
  default 24 h). Blink is intended for temporary sharing, not permanent storage.
- **Per-User Rate Limiting** - Upload and download endpoints are protected by a Redis-backed sliding-window rate
  limiter.
  Limits are configurable via `blink.rate-limit.upload` and `blink.rate-limit.download` (default: 10 uploads / 60 s,
  30 downloads / 60 s). Exceeding the limit returns `429 Too Many Requests`.
- **Interactive API Docs** - Swagger UI is available at `/swagger-ui.html` when the application is running; all
  endpoints are pre-configured with JWT bearer auth.
- **Versioned Schema Migrations** - Database schema is managed by Flyway. Migrations in `db/migration/` are applied
  in order on startup and never re-run, replacing Hibernate DDL auto-generation in production.
- **Observability** - Exposes health probes (`/actuator/health`) with a custom MinIO connectivity check alongside
  standard DB, Redis, and disk indicators. Prometheus-ready metrics (`/actuator/prometheus`) track upload duration,
  download count, and delete count via Micrometer.
- **Input Validation** - Request DTOs are validated via Bean Validation (`@NotBlank`, `@Size`). Constraint violations
  return a structured `400 Bad Request` with a descriptive message before reaching the service layer.
- **Isolated Test Environment** - Maintains fully separate development and integration-test environments. The test
  profile disables Flyway and uses Hibernate `create-drop` against a dedicated `blink_test` database.

---

## API Reference

All file operations require a valid JWT provided in the `Authorization: Bearer <token>` header.

| Method   | Endpoint                   | Description                                                                                    | Access        |
|:---------|:---------------------------|:-----------------------------------------------------------------------------------------------|:--------------|
| `POST`   | `/api/auth/login`          | Authenticate user credentials and receive a JWT.                                               | Public        |
| `GET`    | `/api/files`               | List files. Admins may pass `?all=true` to list every user's files.                            | Authenticated |
| `POST`   | `/api/files/upload`        | Upload a file (`multipart/form-data`) to the storage bucket.                                   | Authenticated |
| `GET`    | `/api/files/{id}`          | Retrieve file metadata (original name, size, upload timestamp).                                | Authenticated |
| `GET`    | `/api/files/{id}/download` | Stream the file content directly for download.                                                 | Authenticated |
| `GET`    | `/api/files/{id}/share`    | Generate a 1-hour presigned URL for temporary external access. File itself expires after 24 h. | Authenticated |
| `DELETE` | `/api/files/{id}`          | Remove the file from MinIO and delete its metadata from PostgreSQL.                            | Admin Only    |

---

## Configuration & Setup

### Prerequisites

- Java 21
- Docker & Docker Compose

### Clone & Environment Configuration

1. Clone the repository:
   ```bash
   git clone https://github.com/varuzhantrue/blink.git
   cd blink
   ```

2. Clone the template to create your local configuration:
    ```bash
   cp src/main/resources/application.yml.template src/main/resources/application.yml
    ```

3. Open `application.yml` and provide a secure JWT_SECRET for token signing.

4. (Optional) Adjust DB or MinIO credentials if you are not using the default Docker Compose settings.

_Blink uses environment variables with sensible defaults for local development._

### Running the Application

Blink uses Spring Boot Docker Compose Support, so infrastructure containers are managed automatically.

Start the application:

   ```bash
   ./gradlew bootRun
   ```

On startup, the application will automatically:

- Pull and start **PostgreSQL**, **MinIO**, and **Redis** containers via Docker Compose.
- Initialize the `blinkdb` and `blink_test` databases using `init-test-db.sql`.
- Verify S3 bucket availability before the server starts.

### Local Dev Tools

| Tool          | URL                     | Purpose                                         |
|:--------------|:------------------------|:------------------------------------------------|
| MinIO Console | `http://localhost:9001` | Browse buckets and objects                      |
| RedisInsight  | `http://localhost:5540` | Inspect Redis keys and monitor rate-limit state |
| Swagger UI    | `/swagger-ui.html`      | Interactive API docs with JWT auth              |

---

## Testing

Blink uses a dedicated test profile to ensure deterministic results without affecting your development data.

- **Profile:** Tests run under `@ActiveProfiles("test")`, targeting the isolated `blink_test` database and a dedicated
  `integration-test-bucket` in MinIO.
- **Auto-cleanup:** The MinIO test bucket is automatically purged before each integration test to prevent state leakage.

Run the full test suite:

```bash
./gradlew test
```

## Note ⚠

This is a learning-focused project. I use this repo to test new tools and strategies. Expect frequent changes and zero
stability.
**Use at your own risk!**