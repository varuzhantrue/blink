package com.truecorp.blink.service;

import com.truecorp.blink.exception.ResourceNotFoundException;
import com.truecorp.blink.model.FileMetadata;
import com.truecorp.blink.repository.FileMetadataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class S3FileServiceTest {
    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private FileMetadataRepository fileMetadataRepository;

    @InjectMocks
    private S3FileService s3FileService;

    private FileMetadata sampleMetadata;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(s3FileService, "bucketName", "test-bucket");

        sampleMetadata = new FileMetadata();
        sampleMetadata.setId(1L);
        sampleMetadata.setOriginalFileName("test.txt");
        sampleMetadata.setS3ObjectKey("uuid-test.txt");
        sampleMetadata.setFileSize(10L);
        sampleMetadata.setContentType("text/plain");
    }


    @Test
    void getMetadata_ShouldReturnMetadata_WhenIdExists() {
        when(fileMetadataRepository.findById(anyLong())).thenReturn(Optional.of(sampleMetadata));

        FileMetadata result = s3FileService.getMetadata(1L);

        assertNotNull(result);
        assertEquals(sampleMetadata.getOriginalFileName(), result.getOriginalFileName());
        verify(fileMetadataRepository, times(1)).findById(1L);
    }

    @Test
    void getMetadata_ShouldThrowException_WhenIdDoesNotExist() {
        when(fileMetadataRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> s3FileService.getMetadata(1L));
    }

    @Test
    void uploadFile_ShouldSaveMetadataAndUploadToS3() {
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "scooby.png",
                "image/png",
                "dummy-data".getBytes()
        );

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        when(fileMetadataRepository.save(any(FileMetadata.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FileMetadata result = s3FileService.uploadFile(multipartFile);

        assertNotNull(result);
        assertEquals("scooby.png", result.getOriginalFileName());
        assertTrue(result.getS3ObjectKey().endsWith("scooby.png"));

        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(fileMetadataRepository).save(any(FileMetadata.class));
    }

    @Test
    void uploadFile_ShouldThrowRuntimeException_WhenS3UploadFails() {
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "scooby.png",
                "image/png",
                "dummy-data".getBytes()
        );

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("Access Denied").build());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> s3FileService.uploadFile(multipartFile));

        assertTrue(exception.getMessage().contains("S3 upload failed"));
        assertTrue(exception.getMessage().contains("Access Denied"));
    }

    @Test
    void downloadFile_ShouldReturnInputStream_WhenFileExists() {
        when(fileMetadataRepository.findById(anyLong())).thenReturn(Optional.of(sampleMetadata));

        @SuppressWarnings("unchecked")
        ResponseInputStream<GetObjectResponse> mockS3Stream = mock(ResponseInputStream.class);
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(mockS3Stream);

        InputStream result = s3FileService.downloadFile(1L);

        assertNotNull(result);
        verify(s3Client).getObject(any(GetObjectRequest.class));
    }

    @Test
    void downloadFile_ShouldThrowResourceNotFound_WhenS3ObjectIsMissing() {
        when(fileMetadataRepository.findById(anyLong())).thenReturn(Optional.of(sampleMetadata));

        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("Key not found").build());

        RuntimeException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> s3FileService.downloadFile(1L)
        );
        assertTrue(exception.getMessage().contains("File not found in storage with key"));
    }

    @Test
    void deleteFile_ShouldDeleteMetadataFromDBAndObjectFromS3() {
        when(fileMetadataRepository.findById(anyLong())).thenReturn(Optional.of(sampleMetadata));

        s3FileService.deleteFile(1L);

        verify(fileMetadataRepository).deleteById(1L);
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void deleteFile_ShouldThrowResourceNotFound_WhenIdDoesNotExist() {
        when(fileMetadataRepository.findById(anyLong())).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> s3FileService.deleteFile(99L)
        );

        assertTrue(exception.getMessage().contains("File not found with ID"));
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void deleteFile_ShouldThrowRuntimeException_WhenS3DeletionFails() {
        when(fileMetadataRepository.findById(anyLong())).thenReturn(Optional.of(sampleMetadata));

        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("Access Denied").build());

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> s3FileService.deleteFile(1L)
        );

        assertTrue(exception.getMessage().contains("S3 file deletion failed"));
    }

    @Test
    void generatePresignedUrl_ShouldReturnUrl_WhenFileExists() throws MalformedURLException {
        when(fileMetadataRepository.findById(anyLong())).thenReturn(Optional.of(sampleMetadata));

        PresignedGetObjectRequest mockPresignedGetObjectRequest = mock(PresignedGetObjectRequest.class);
        URL mockUrl = URI.create("https://example.com/presigned-url").toURL();
        when(mockPresignedGetObjectRequest.url()).thenReturn(mockUrl);

        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(mockPresignedGetObjectRequest);

        String resultURL = s3FileService.generatePresignedUrl(1L, Duration.ofHours(1));

        assertNotNull(resultURL);
        assertEquals(mockUrl.toString(), resultURL);
        verify(fileMetadataRepository).findById(1L);
        verify(s3Presigner).presignGetObject(any(GetObjectPresignRequest.class));
    }
}
