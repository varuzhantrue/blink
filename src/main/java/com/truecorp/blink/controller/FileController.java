package com.truecorp.blink.controller;

import com.truecorp.blink.exception.ResourceNotFoundException;
import com.truecorp.blink.model.FileMetadata;
import com.truecorp.blink.repository.FileMetadataRepository;
import com.truecorp.blink.service.S3FileService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.Duration;

@RestController
@RequestMapping("api/files")
public class FileController {

    private final S3FileService s3FileService;
    private final FileMetadataRepository fileMetadataRepository;

    public FileController(S3FileService s3FileService, FileMetadataRepository fileMetadataRepository) {
        this.s3FileService = s3FileService;
        this.fileMetadataRepository = fileMetadataRepository;
    }

    @PostMapping("/upload")
    public ResponseEntity<FileMetadata> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        FileMetadata fileMetadata = s3FileService.uploadFile(file);
        return new ResponseEntity<>(fileMetadata, HttpStatus.CREATED);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> downloadFile(@PathVariable Long id) {
        FileMetadata metadata = fileMetadataRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("File not found with ID " + id));

        InputStream fileStream = s3FileService.downloadFile(id);
        HttpHeaders headers = new HttpHeaders();
        String contentDisposition = "attachment; filename=\"" + metadata.getOriginalFileName() + "\"";
        headers.add(HttpHeaders.CONTENT_DISPOSITION, contentDisposition);

        MediaType mediaType = MediaType.parseMediaType(metadata.getContentType());

        return ResponseEntity.ok()
                .headers(headers)
                .contentLength(metadata.getFileSize())
                .contentType(mediaType)
                .body(new InputStreamResource(fileStream));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FileMetadata> getMetadata(@PathVariable Long id) {
        FileMetadata metadata = s3FileService.getMetadata(id);
        return ResponseEntity.ok(metadata);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFile(@PathVariable Long id) {
        s3FileService.deleteFile(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/share")
    public ResponseEntity<String> shareFile(@PathVariable Long id) {
        Duration expiration = Duration.ofHours(1);
        String presignedUrl = s3FileService.generatePresignedUrl(id, expiration);
        return ResponseEntity.ok(presignedUrl);
    }
}
