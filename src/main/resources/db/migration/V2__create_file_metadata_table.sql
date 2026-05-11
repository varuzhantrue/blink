CREATE TABLE file_metadata (
    id                 BIGSERIAL                NOT NULL PRIMARY KEY,
    original_file_name VARCHAR(255)             NOT NULL,
    s3object_key       VARCHAR(255)             NOT NULL UNIQUE,
    content_type       VARCHAR(255)             NOT NULL,
    file_size          BIGINT                   NOT NULL,
    upload_timestamp   TIMESTAMP WITH TIME ZONE NOT NULL,
    owner_id           BIGINT                   NOT NULL REFERENCES users (id),
    share_token        VARCHAR(255)
);
