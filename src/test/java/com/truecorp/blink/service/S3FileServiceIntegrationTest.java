package com.truecorp.blink.service;

import com.truecorp.blink.model.FileMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = "minio.bucket-name=integration-test-bucket")
public class S3FileServiceIntegrationTest {

    @Autowired
    private S3FileService s3FileService;

    @Autowired
    private S3Client s3Client;

    private static final String TEST_BUCKET = "integration-test-bucket";

    @BeforeEach
    void setUp() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(TEST_BUCKET).build());
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(TEST_BUCKET).build());
            }
        }
    }

    @Test
    void testFullFileUploadAndDownloadFlow() throws Exception {

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "integration.txt",
                "text/plain",
                "Integration test content".getBytes()
        );

        FileMetadata uploadedFileMetadata = s3FileService.uploadFile(file);

        assertNotNull(uploadedFileMetadata.getId());

        try (InputStream is = s3FileService.downloadFile(uploadedFileMetadata.getId())) {
            byte[] downloadedBytes = is.readAllBytes();
            assertEquals("Integration test content", new String(downloadedBytes));
        }
    }
}
