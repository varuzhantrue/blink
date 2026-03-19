# Blink 🚀

**Blink** is a high-performance, private cloud storage REST API built to simulate a production-grade cloud ecosystem. It
provides secure, S3-compatible object storage with automated infrastructure management and stateless JWT-based
authentication.

---

## Tech Stack

| Layer              | Technology                                |
|:-------------------|:------------------------------------------|
| **Language**       | Java 21                                   |
| **Framework**      | Spring Boot 3.x (Web, Security, Data JPA) |
| **Object Storage** | MinIO (S3-Compatible)                     |
| **Database**       | PostgreSQL                                |
| **Authentication** | JWT + BCrypt Password Hashing             |
| **Infrastructure** | Docker & Docker Compose                   |
| **Build Tool**     | Gradle                                    |

---

## Features

- **S3-Compatible Storage** — Full integration with MinIO for robust file handling.
- **Zero-Touch Infrastructure** — Uses `spring-boot-docker-compose` to automatically start PostgreSQL and MinIO
  containers alongside the application; no manual `docker-compose up` required.
- **Stateless Security** — Enforces `STATELESS` session policy via a custom JWT filter chain, eliminating server-side
  session state entirely.
- **Collision-Safe File Mapping** — Stores files in MinIO under UUID-based object keys while preserving original
  filenames in PostgreSQL, preventing naming conflicts at scale.
- **Presigned URL Sharing** — Generates time-limited presigned URLs for secure, direct-to-MinIO file access without
  exposing internal credentials.
- **Isolated Test Environment** — Maintains fully separate development and integration-test environments, with automated
  database initialization via `init-test-db.sql`.

---

## API Reference

All file operations require a valid JWT provided in the `Authorization: Bearer <token>` header.

| Method   | Endpoint                   | Description                                                         | Access        |
|:---------|:---------------------------|:--------------------------------------------------------------------|:--------------|
| `POST`   | `/api/auth/login`          | Authenticate user credentials and receive a JWT.                    | Public        |
| `POST`   | `/api/files/upload`        | Upload a file (`multipart/form-data`) to the storage bucket.        | Authenticated |
| `GET`    | `/api/files/{id}`          | Retrieve file metadata (original name, size, upload timestamp).     | Authenticated |
| `GET`    | `/api/files/{id}/download` | Stream the file content directly for download.                      | Authenticated |
| `GET`    | `/api/files/{id}/share`    | Generate a 1-hour Presigned URL for temporary external access.      | Authenticated |
| `DELETE` | `/api/files/{id}`          | Remove the file from MinIO and delete its metadata from PostgreSQL. | Admin Only    |

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

- Pull and start **PostgreSQL** and **MinIO** containers via Docker Compose.
- Initialize the `blinkdb` and `blink_test` databases using `init-test-db.sql`.
- Verify S3 bucket availability before the server starts.

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
This is a learning-focused project. I use this repo to test new tools and strategies. Expect frequent changes and zero stability.
**Use at your own risk!**