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

        try {
            FileMetadata fileMetadata = s3FileService.uploadFile(file);
            return new ResponseEntity<>(fileMetadata, HttpStatus.CREATED);
        } catch (Exception e) {
            System.err.println("Upload failed: " + e.getMessage());
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> downloadFile(@PathVariable Long id) {
        FileMetadata metadata = fileMetadataRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("File not found with ID " + id));

        try {
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

        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("Download failed for ID " + id + ": " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<FileMetadata> getMetadata(@PathVariable Long id){
        try {
            FileMetadata metadata = s3FileService.getMetadata(id);
            return ResponseEntity.ok(metadata);
        } catch (ResourceNotFoundException e) {
            throw e;
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFile(@PathVariable Long id){
        try {
            s3FileService.deleteFile(id);
            return ResponseEntity.noContent().build();
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("Deletion failed for ID " + id + ": " + e.getMessage());
            return  ResponseEntity.internalServerError().build();
        }
    }
}
