package com.truecorp.blink.service;

import com.truecorp.blink.model.FileMetadata;
import com.truecorp.blink.model.UploadStatus;
import com.truecorp.blink.repository.FileMetadataRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
public class FileCleanupService {
    private final FileMetadataRepository fileMetadataRepository;
    private final S3Client s3Client;
    private final String bucketName;

    @Value("${blink.retention-period-hours:24}")
    private long retentionPeriodHours;

    public FileCleanupService(FileMetadataRepository fileMetadataRepository,
                              S3Client s3Client,
                              @Value("${minio.bucket-name}") String bucketName) {
        this.fileMetadataRepository = fileMetadataRepository;
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void purgeExpiredFiles() {
        log.info("Starting scheduled cleanup of expired files...");

        Instant cutoffTime = Instant.now().minus(Duration.ofHours(retentionPeriodHours));
        List<FileMetadata> expiredFiles = fileMetadataRepository.findByUploadStatusAndUploadTimestampBefore(
                UploadStatus.COMPLETE, cutoffTime);

        if (expiredFiles.isEmpty()) {
            log.info("No expired files found for cleanup.");
            return;
        }

        int deletedCount = 0;
        int failedCount = 0;

        for (FileMetadata metadata : expiredFiles) {
            try {
                DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                        .bucket(bucketName)
                        .key(metadata.getS3ObjectKey())
                        .build();
                s3Client.deleteObject(deleteRequest);

                fileMetadataRepository.delete(metadata);

                log.debug("Successfully purged file: {}", metadata.getS3ObjectKey());
                deletedCount++;

            } catch (S3Exception e) {
                log.error("Failed to delete object from S3: {}. Database record retained.", metadata.getS3ObjectKey(), e);
                failedCount++;
            } catch (Exception e) {
                log.error("Unexpected error during cleanup of file: {}", metadata.getOriginalFileName(), e);
                failedCount++;
            }
        }

        log.info("Cleanup finished. Successfully deleted: {}, Failed: {}", deletedCount, failedCount);
    }
}
