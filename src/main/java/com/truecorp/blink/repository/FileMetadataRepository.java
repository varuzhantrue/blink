package com.truecorp.blink.repository;

import com.truecorp.blink.model.FileMetadata;
import com.truecorp.blink.model.UploadStatus;
import com.truecorp.blink.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface FileMetadataRepository extends JpaRepository<FileMetadata, Long> {

    List<FileMetadata> findByOwnerAndUploadStatus(User owner, UploadStatus uploadStatus);

    List<FileMetadata> findByUploadStatus(UploadStatus uploadStatus);

    List<FileMetadata> findByUploadStatusAndUploadTimestampBefore(UploadStatus uploadStatus, Instant cutoffTime);
}
