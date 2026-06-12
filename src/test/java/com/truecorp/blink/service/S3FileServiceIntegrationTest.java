package com.truecorp.blink.service;

import com.truecorp.blink.dto.FileMetadataResponse;
import com.truecorp.blink.dto.MultipartCompleteRequest;
import com.truecorp.blink.dto.MultipartInitiateRequest;
import com.truecorp.blink.dto.MultipartInitiateResponse;
import com.truecorp.blink.exception.ResourceNotFoundException;
import com.truecorp.blink.model.User;
import com.truecorp.blink.repository.FileMetadataRepository;
import com.truecorp.blink.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
public class S3FileServiceIntegrationTest {

    @Autowired
    private S3FileService s3FileService;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FileMetadataRepository fileMetadataRepository;

    private static final String TEST_BUCKET = "integration-test-bucket";
    private static final String TEST_USERNAME = "testUser";
    private static final String OTHER_USERNAME = "otherUser";

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

        fileMetadataRepository.deleteAll();

        userRepository.findByUsername(TEST_USERNAME).orElseGet(() -> {
            User user = new User();
            user.setUsername(TEST_USERNAME);
            user.setPassword("password");
            user.setRoles(Set.of("USER"));
            return userRepository.save(user);
        });

        userRepository.findByUsername(OTHER_USERNAME).orElseGet(() -> {
            User user = new User();
            user.setUsername(OTHER_USERNAME);
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

    @Test
    @WithMockUser(username = TEST_USERNAME)
    void listFiles_ShouldReturnOnlyOwnFiles() {
        MockMultipartFile file1 = new MockMultipartFile("file", "a.txt", "text/plain", "aaa".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("file", "b.txt", "text/plain", "bbb".getBytes());
        s3FileService.uploadFile(file1);
        s3FileService.uploadFile(file2);

        List<FileMetadataResponse> result = s3FileService.listFiles(false);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(f -> TEST_USERNAME.equals(f.ownerUsername())));
    }

    @Test
    @WithMockUser(username = TEST_USERNAME)
    void listFiles_ShouldNotReturnOtherUsersFiles() {
        MockMultipartFile myFile = new MockMultipartFile("file", "mine.txt", "text/plain", "x".getBytes());
        s3FileService.uploadFile(myFile);

        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(OTHER_USERNAME, null, List.of()));
        MockMultipartFile otherFile = new MockMultipartFile("file", "theirs.txt", "text/plain", "y".getBytes());
        s3FileService.uploadFile(otherFile);

        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(TEST_USERNAME, null, List.of()));

        List<FileMetadataResponse> result = s3FileService.listFiles(false);

        assertEquals(1, result.size());
        assertEquals("mine.txt", result.getFirst().originalFileName());
    }

    @Test
    @WithMockUser(username = TEST_USERNAME, roles = "ADMIN")
    void listFiles_ShouldReturnAllFiles_WhenAdminRequestsAll() {
        MockMultipartFile file = new MockMultipartFile("file", "admin.txt", "text/plain", "z".getBytes());
        s3FileService.uploadFile(file);

        List<FileMetadataResponse> result = s3FileService.listFiles(true);

        assertNotNull(result);
        assertTrue(result.stream().anyMatch(f -> "admin.txt".equals(f.originalFileName())));
    }

    @Test
    @WithMockUser(username = TEST_USERNAME)
    void listFiles_ShouldThrowAccessDenied_WhenNonAdminRequestsAll() {
        assertThrows(AccessDeniedException.class,
                () -> s3FileService.listFiles(true));
    }

    @Test
    @WithMockUser(username = TEST_USERNAME)
    void testFullMultipartUploadFlow() throws Exception {
        // S3/MinIO require non-final multipart parts to be at least 5 MB
        byte[] partOneData = "first part of the file - ".repeat(250_000).getBytes();
        byte[] partTwoData = "second and final part".getBytes();

        MultipartInitiateRequest initiateRequest = new MultipartInitiateRequest(
                "multipart-test.bin",
                "application/octet-stream",
                (long) (partOneData.length + partTwoData.length),
                2
        );

        MultipartInitiateResponse initiateResponse = s3FileService.initiateMultipartUpload(initiateRequest);

        assertNotNull(initiateResponse.fileId());
        assertNotNull(initiateResponse.uploadId());
        assertEquals(2, initiateResponse.partUrls().size());

        HttpClient httpClient = HttpClient.newHttpClient();
        String eTag1 = uploadPart(httpClient, initiateResponse.partUrls().get(0), partOneData);
        String eTag2 = uploadPart(httpClient, initiateResponse.partUrls().get(1), partTwoData);

        MultipartCompleteRequest completeRequest = new MultipartCompleteRequest(List.of(
                new MultipartCompleteRequest.Part(1, eTag1),
                new MultipartCompleteRequest.Part(2, eTag2)
        ));

        FileMetadataResponse completedFile = s3FileService.completeMultipartUpload(initiateResponse.fileId(),
                completeRequest);

        assertEquals("multipart-test.bin", completedFile.originalFileName());

        try (InputStream is = s3FileService.downloadFile(initiateResponse.fileId())) {
            byte[] downloaded = is.readAllBytes();
            byte[] expected = new byte[partOneData.length + partTwoData.length];
            System.arraycopy(partOneData, 0, expected, 0, partOneData.length);
            System.arraycopy(partTwoData, 0, expected, partOneData.length, partTwoData.length);
            assertArrayEquals(expected, downloaded);
        }
    }

    @Test
    @WithMockUser(username = TEST_USERNAME)
    void testMultipartUploadAbortFlow() {
        MultipartInitiateRequest initiateRequest = new MultipartInitiateRequest(
                "aborted-upload.bin",
                "application/octet-stream",
                100L,
                1
        );

        MultipartInitiateResponse initiateResponse = s3FileService.initiateMultipartUpload(initiateRequest);

        s3FileService.abortMultipartUpload(initiateResponse.fileId());

        assertThrows(ResourceNotFoundException.class,
                () -> s3FileService.downloadFile(initiateResponse.fileId()));
    }

    private String uploadPart(HttpClient httpClient, String presignedUrl, byte[] data) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(presignedUrl))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(data))
                .build();

        HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        assertEquals(200, response.statusCode());

        return response.headers().firstValue("ETag")
                .orElseThrow(() -> new IllegalStateException("No ETag returned for uploaded part"));
    }
}
