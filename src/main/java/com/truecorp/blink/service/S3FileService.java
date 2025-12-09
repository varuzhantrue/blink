package com.truecorp.blink.service;

import com.truecorp.blink.exception.ResourceNotFoundException;
import com.truecorp.blink.model.FileMetadata;
import com.truecorp.blink.repository.FileMetadataRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class S3FileService {

    private final S3Client s3Client;
    private final FileMetadataRepository fileMetadataRepository;

    @Value("${minio.bucket-name}")
    private String bucketName;


    public S3FileService(S3Client s3Client, FileMetadataRepository fileMetadataRepository) {
        this.s3Client = s3Client;
        this.fileMetadataRepository = fileMetadataRepository;
    }

    @Transactional
    public FileMetadata uploadFile(MultipartFile file) {
        String s3ObjectKey = UUID.randomUUID() + "-" + file.getOriginalFilename();

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3ObjectKey)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build();

        try {
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

            return fileMetadataRepository.save(metadata);
        } catch (S3Exception e) {
            log.error("S3 upload failed for file: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("S3 upload failed: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Generic upload failed for file: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("Upload failed: " + e.getMessage(), e);
        }
    }

    public InputStream downloadFile(Long fileId) {
        FileMetadata fileMetadata = fileMetadataRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found with ID: " + fileId));

        String s3ObjectKey = fileMetadata.getS3ObjectKey();

        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3ObjectKey)
                    .build();

            ResponseInputStream<GetObjectResponse> stream = s3Client.getObject(getObjectRequest);
            return stream;
        } catch (S3Exception e) {
            log.error("S3 download failed for file: {}", fileMetadata.getOriginalFileName(), e);
            throw new ResourceNotFoundException("File not found in storage with key: " + s3ObjectKey);
        } catch (Exception e) {
            log.error("Generic download failed for file: {}", fileMetadata.getOriginalFileName(), e);
            throw new RuntimeException("Error retrieving file from S3: " + e.getMessage(), e);
        }
    }

    public FileMetadata getMetadata(Long fileId) {
        return fileMetadataRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found with ID: " + fileId));
    }

    @Transactional
    public void deleteFile(Long fileId) {
        FileMetadata metadata = fileMetadataRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found with ID: " + fileId));

        String s3ObjectKey = metadata.getS3ObjectKey();

        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3ObjectKey)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
            fileMetadataRepository.deleteById(fileId);
        } catch (S3Exception e) {
            log.error("S3 file deletion failed for key: {}: {}", s3ObjectKey, e.getMessage(), e);
            throw new RuntimeException("S3 file deletion failed: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Generic file deletion failed for key: {}: {}", s3ObjectKey, e.getMessage(), e);
            throw new RuntimeException("Deletion failed: " + e.getMessage(), e);
        }
    }
}
