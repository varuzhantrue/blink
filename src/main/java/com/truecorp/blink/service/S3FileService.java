package com.truecorp.blink.service;

import com.truecorp.blink.dto.FileMetadataResponse;
import com.truecorp.blink.dto.MultipartCompleteRequest;
import com.truecorp.blink.dto.MultipartInitiateRequest;
import com.truecorp.blink.dto.MultipartInitiateResponse;
import com.truecorp.blink.exception.ResourceNotFoundException;
import com.truecorp.blink.model.FileMetadata;
import com.truecorp.blink.model.UploadStatus;
import com.truecorp.blink.model.User;
import com.truecorp.blink.repository.FileMetadataRepository;
import com.truecorp.blink.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class S3FileService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final FileMetadataRepository fileMetadataRepository;
    private final UserRepository userRepository;
    private final Timer uploadTimer;
    private final Counter downloadCounter;
    private final Counter deleteCounter;
    private final String bucketName;

    public S3FileService(
            S3Client s3Client,
            S3Presigner s3Presigner,
            FileMetadataRepository fileMetadataRepository,
            UserRepository userRepository,
            MeterRegistry meterRegistry,
            @Value("${minio.bucket-name}") String bucketName
    ) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.fileMetadataRepository = fileMetadataRepository;
        this.userRepository = userRepository;
        this.bucketName = bucketName;
        this.uploadTimer = Timer.builder("blink.files.upload.duration")
                .description("Time taken to upload a file to MinIO")
                .register(meterRegistry);
        this.downloadCounter = Counter.builder("blink.files.downloads")
                .description("Total number of file downloads")
                .register(meterRegistry);
        this.deleteCounter = Counter.builder("blink.files.deletes")
                .description("Total number of file deletions")
                .register(meterRegistry);
    }

    @Transactional
    public FileMetadataResponse uploadFile(MultipartFile file) {
        User currentUser = getAuthenticatedUser();

        String s3ObjectKey = String.format("uploads/%d/%s-%s",
                currentUser.getId(),
                UUID.randomUUID(),
                file.getOriginalFilename());

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3ObjectKey)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build();

        try {
            return uploadTimer.recordCallable(() -> {
                s3Client.putObject(
                        putObjectRequest,
                        RequestBody.fromInputStream(file.getInputStream(), file.getSize())
                );

                FileMetadata metadata = new FileMetadata();
                metadata.setOriginalFileName(file.getOriginalFilename());
                metadata.setS3ObjectKey(s3ObjectKey);
                metadata.setContentType(file.getContentType());
                metadata.setFileSize(file.getSize());
                metadata.setUploadTimestamp(Instant.now());
                metadata.setOwner(currentUser);
                metadata.setUploadStatus(UploadStatus.COMPLETE);

                return mapToFileMetadataResponse(fileMetadataRepository.save(metadata));
            });
        } catch (S3Exception e) {
            log.error("S3 upload failed for file: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("S3 upload failed: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Generic upload failed for file: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("Upload failed: " + e.getMessage(), e);
        }
    }

    public InputStream downloadFile(Long fileId) {
        FileMetadata fileMetadata = getAuthorizedFileMetadata(fileId);

        String s3ObjectKey = fileMetadata.getS3ObjectKey();

        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3ObjectKey)
                    .build();

            InputStream stream = s3Client.getObject(getObjectRequest);
            downloadCounter.increment();
            return stream;
        } catch (S3Exception e) {
            log.error("S3 download failed for file: {}", fileMetadata.getOriginalFileName(), e);
            throw new ResourceNotFoundException("File not found in storage with key: " + s3ObjectKey);
        } catch (Exception e) {
            log.error("Generic download failed for file: {}", fileMetadata.getOriginalFileName(), e);
            throw new RuntimeException("Error retrieving file from S3: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public List<FileMetadataResponse> listFiles(boolean all) {
        User currentUser = getAuthenticatedUser();
        boolean isAdmin = isCurrentUserAdmin();

        if (all && !isAdmin) {
            throw new AccessDeniedException("Only admins can list all files.");
        }

        List<FileMetadata> files = all
                ? fileMetadataRepository.findByUploadStatus(UploadStatus.COMPLETE)
                : fileMetadataRepository.findByOwnerAndUploadStatus(currentUser, UploadStatus.COMPLETE);

        return files.stream().map(this::mapToFileMetadataResponse).toList();
    }

    public FileMetadataResponse getMetadata(Long fileId) {
        FileMetadata fileMetadata = getAuthorizedFileMetadata(fileId);
        return mapToFileMetadataResponse(fileMetadata);
    }

    @Transactional
    public void deleteFile(Long fileId) {
        FileMetadata fileMetadata = getAuthorizedFileMetadata(fileId);

        String s3ObjectKey = fileMetadata.getS3ObjectKey();

        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3ObjectKey)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
            fileMetadataRepository.deleteById(fileId);
            deleteCounter.increment();
        } catch (S3Exception e) {
            log.error("S3 file deletion failed for key: {}: {}", s3ObjectKey, e.getMessage(), e);
            throw new RuntimeException("S3 file deletion failed: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Generic file deletion failed for key: {}: {}", s3ObjectKey, e.getMessage(), e);
            throw new RuntimeException("Deletion failed: " + e.getMessage(), e);
        }
    }

    public String generatePresignedUrl(Long fileId, Duration expiration) {
        FileMetadata fileMetadata = getAuthorizedFileMetadata(fileId);

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(fileMetadata.getS3ObjectKey())
                .responseContentDisposition("attachment; filename=\"" + fileMetadata.getOriginalFileName() + "\"")
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(expiration)
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);

        log.info("Generated presigned URL for key {} valid for {}", fileMetadata.getS3ObjectKey(), expiration);

        return presignedRequest.url().toExternalForm();
    }

    @Transactional
    public MultipartInitiateResponse initiateMultipartUpload(MultipartInitiateRequest request) {
        User currentUser = getAuthenticatedUser();

        String s3ObjectKey = String.format("uploads/%d/%s-%s",
                currentUser.getId(),
                UUID.randomUUID(),
                request.fileName());

        CreateMultipartUploadRequest createRequest = CreateMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(s3ObjectKey)
                .contentType(request.contentType())
                .build();

        String uploadId = s3Client.createMultipartUpload(createRequest).uploadId();

        FileMetadata metadata = new FileMetadata();
        metadata.setOriginalFileName(request.fileName());
        metadata.setS3ObjectKey(s3ObjectKey);
        metadata.setContentType(request.contentType());
        metadata.setFileSize(request.fileSize());
        metadata.setUploadTimestamp(Instant.now());
        metadata.setOwner(currentUser);
        metadata.setUploadStatus(UploadStatus.PENDING);
        metadata.setUploadId(uploadId);
        Long fileId = fileMetadataRepository.save(metadata).getId();

        List<String> partUrls = new java.util.ArrayList<>();
        for (int part = 1; part <= request.partCount(); part++) {
            UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                    .bucket(bucketName)
                    .key(s3ObjectKey)
                    .uploadId(uploadId)
                    .partNumber(part)
                    .build();
            UploadPartPresignRequest presignRequest = UploadPartPresignRequest.builder()
                    .signatureDuration(Duration.ofHours(1))
                    .uploadPartRequest(uploadPartRequest)
                    .build();
            partUrls.add(s3Presigner.presignUploadPart(presignRequest).url().toExternalForm());
        }

        log.info("Initiated multipart upload for file '{}', uploadId={}, parts={}", request.fileName(), uploadId, request.partCount());
        return new MultipartInitiateResponse(fileId, uploadId, partUrls);
    }

    @Transactional
    public FileMetadataResponse completeMultipartUpload(Long fileId, MultipartCompleteRequest request) {
        FileMetadata metadata = getPendingFileMetadata(fileId);

        List<CompletedPart> completedParts = request.parts().stream()
                .map(p -> CompletedPart.builder()
                        .partNumber(p.partNumber())
                        .eTag(p.eTag())
                        .build())
                .toList();

        CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(metadata.getS3ObjectKey())
                .uploadId(metadata.getUploadId())
                .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build())
                .build();

        try {
            s3Client.completeMultipartUpload(completeRequest);
        } catch (S3Exception e) {
            log.error("Failed to complete multipart upload for file ID {}: {}", fileId, e.getMessage(), e);
            throw new RuntimeException("Failed to complete multipart upload: " + e.getMessage(), e);
        }

        metadata.setUploadStatus(UploadStatus.COMPLETE);
        log.info("Completed multipart upload for file ID {}", fileId);
        return mapToFileMetadataResponse(fileMetadataRepository.save(metadata));
    }

    @Transactional
    public void abortMultipartUpload(Long fileId) {
        FileMetadata metadata = getPendingFileMetadata(fileId);

        AbortMultipartUploadRequest abortRequest = AbortMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(metadata.getS3ObjectKey())
                .uploadId(metadata.getUploadId())
                .build();

        try {
            s3Client.abortMultipartUpload(abortRequest);
        } catch (S3Exception e) {
            log.warn("S3 abort failed for file ID {} (removing metadata anyway): {}", fileId, e.getMessage());
        }

        fileMetadataRepository.deleteById(fileId);
        log.info("Aborted multipart upload for file ID {}", fileId);
    }

    private FileMetadata getPendingFileMetadata(Long fileId) {
        FileMetadata metadata = fileMetadataRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("Upload not found with ID: " + fileId));

        if (metadata.getUploadStatus() != UploadStatus.PENDING) {
            throw new ResourceNotFoundException("Upload not found with ID: " + fileId);
        }

        User currentUser = getAuthenticatedUser();
        boolean isAdmin = isCurrentUserAdmin();
        if (!metadata.getOwner().getId().equals(currentUser.getId()) && !isAdmin) {
            log.warn("User {} attempted unauthorized access to upload ID {}", currentUser.getUsername(), fileId);
            throw new AccessDeniedException("You do not have permission to access this upload.");
        }

        return metadata;
    }

    private FileMetadataResponse mapToFileMetadataResponse(FileMetadata fileMetadata) {
        return new FileMetadataResponse(
                fileMetadata.getId(),
                fileMetadata.getOriginalFileName(),
                fileMetadata.getContentType(),
                fileMetadata.getFileSize(),
                fileMetadata.getUploadTimestamp(),
                fileMetadata.getOwner().getUsername()
        );
    }

    private User getAuthenticatedUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
    }

    private boolean isCurrentUserAdmin() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private FileMetadata getAuthorizedFileMetadata(Long fileId) {
        FileMetadata fileMetadata = fileMetadataRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found with ID: " + fileId));

        if (fileMetadata.getUploadStatus() != UploadStatus.COMPLETE) {
            throw new ResourceNotFoundException("File not found with ID: " + fileId);
        }

        User currentUser = getAuthenticatedUser();

        // Check if the current user is the owner (or an ADMIN)
        boolean isAdmin = isCurrentUserAdmin();
        if (!fileMetadata.getOwner().getId().equals(currentUser.getId()) && !isAdmin) {
            log.warn("User {} attempted unauthorized access to file ID {}", currentUser.getUsername(), fileId);
            throw new AccessDeniedException("You do not have permission to access this file.");
        }

        return fileMetadata;
    }
}
