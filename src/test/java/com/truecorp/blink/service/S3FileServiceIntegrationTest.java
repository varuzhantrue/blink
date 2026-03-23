package com.truecorp.blink.service;

import com.truecorp.blink.dto.FileMetadataResponse;
import com.truecorp.blink.model.User;
import com.truecorp.blink.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
public class S3FileServiceIntegrationTest {

    @Autowired
    private S3FileService s3FileService;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private UserRepository userRepository;

    private static final String TEST_BUCKET = "integration-test-bucket";
    private static final String TEST_USERNAME = "testUser";

    @BeforeEach
    void setUp() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(TEST_BUCKET).build());
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(TEST_BUCKET).build());
            }
        }

        ListObjectsV2Response listObjectsResponse =
                s3Client.listObjectsV2(builder -> builder.bucket(TEST_BUCKET));

        if (listObjectsResponse.hasContents()) {
            listObjectsResponse.contents().forEach(object ->
                    s3Client.deleteObject(builder -> builder.bucket(TEST_BUCKET).key(object.key())));
        }

        userRepository.findByUsername(TEST_USERNAME).orElseGet(() -> {
            User user = new User();
            user.setUsername(TEST_USERNAME);
            user.setPassword("password");
            user.setRoles(Set.of("USER"));
            return userRepository.save(user);
        });
    }

    @Test
    @WithMockUser(username = TEST_USERNAME)
    void testFullFileUploadAndDownloadFlow() throws Exception {

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "integration.txt",
                "text/plain",
                "Integration test content".getBytes()
        );

        FileMetadataResponse uploadedFileMetadata = s3FileService.uploadFile(file);

        assertNotNull(uploadedFileMetadata.id());
        assertEquals(TEST_USERNAME, uploadedFileMetadata.ownerUsername());

        try (InputStream is = s3FileService.downloadFile(uploadedFileMetadata.id())) {
            byte[] downloadedBytes = is.readAllBytes();
            assertEquals("Integration test content", new String(downloadedBytes));
        }
    }
}
