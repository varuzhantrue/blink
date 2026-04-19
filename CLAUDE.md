# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Run the application (auto-starts PostgreSQL + MinIO via Spring Boot Docker Compose)
./gradlew bootRun

# Run all tests
./gradlew test

# Build
./gradlew build

# Clean
./gradlew clean
```

There is no lint step configured (no Checkstyle or SpotBugs).

Tests use `@ActiveProfiles("test")` and target isolated infrastructure (`blink_test` DB, `integration-test-bucket` in MinIO). The test JVM requires a Mockito agent: this is already wired in `build.gradle` via `jvmArgs`.

## Architecture

Blink is a private cloud storage REST API (Spring Boot 3, Java 21) backed by PostgreSQL and MinIO (S3-compatible object storage).

**Request flow:**

```
HTTP → JwtAuthenticationFilter → Controller → Service → Repository (PostgreSQL)
                                                      → MinIO (via AWS S3 SDK)
```

**Layer summary:**

- **Controllers** (`controller/`): `AuthController` handles `/api/auth/login` (issues JWT). `FileController` handles `/api/files/*` (upload, download, delete, share).
- **Services** (`service/`): `S3FileService` owns all MinIO operations and file metadata persistence. `JwtService` generates and validates tokens. `FileCleanupService` runs an hourly cron (`0 0 * * * *`) to delete files older than the configured retention period (default 24 h).
- **Security** (`config/SecurityConfig`, `filter/JwtAuthenticationFilter`): Stateless JWT-based auth. CSRF disabled. DELETE endpoints require `ADMIN` role; upload/download require `USER`.
- **Data seeding** (`config/DataSeeder`): Creates a default `admin` user on startup if none exists.

**Key design decisions:**

- Files are stored in MinIO with a UUID-based S3 key (collision-safe); the original filename lives only in `FileMetadata`.
- `FileMetadata.owner` is a lazy-loaded FK to `User`, enforcing per-user access control.
- Presigned URL generation uses the AWS SDK and expires in 1 hour.
- Spring Boot Docker Compose support (`lifecycle-management: start_only`) spins up PostgreSQL and MinIO automatically on `bootRun` — no manual `docker compose up` needed for development.

## Infrastructure

Docker Compose defines two services: `blink-db` (PostgreSQL on 5432) and `blink-minio` (MinIO on 9000; console on 9001). The test profile creates and drops the schema fresh each run (`ddl-auto: create-drop`), and each integration test clears the bucket in `setUp()`.

## Configuration

| Property | Location | Default |
|---|---|---|
| JWT expiry | `application.yml` | 86400000 ms (24 h) |
| Retention period | `blink.retention-period-hours` | 24 h |
| Max upload size | `application.yml` | 100 MB |
| Log level | `application.yml` | TRACE for `com.truecorp.blink` |
