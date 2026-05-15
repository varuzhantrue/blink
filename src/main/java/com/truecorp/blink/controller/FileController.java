package com.truecorp.blink.controller;

import com.truecorp.blink.dto.FileMetadataResponse;
import com.truecorp.blink.dto.PresignedUrlResponse;
import com.truecorp.blink.exception.ResourceNotFoundException;
import com.truecorp.blink.model.FileMetadata;
import com.truecorp.blink.repository.FileMetadataRepository;
import com.truecorp.blink.service.S3FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;

@Tag(name = "Files", description = "Upload, download, manage, and share files")
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final S3FileService s3FileService;
    private final FileMetadataRepository fileMetadataRepository;

    public FileController(S3FileService s3FileService, FileMetadataRepository fileMetadataRepository) {
        this.s3FileService = s3FileService;
        this.fileMetadataRepository = fileMetadataRepository;
    }

    @Operation(summary = "List files",
            description = "Returns the caller's files. Admins may pass ?all=true to list every user's files.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "File list returned"),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "403", description = "Access denied - ?all=true requires ADMIN role",
                    content = @Content),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded", content = @Content)
    })
    @GetMapping
    public ResponseEntity<List<FileMetadataResponse>> listFiles(
            @Parameter(description = "Set to true to list all users' files (admin only)")
            @RequestParam(defaultValue = "false") boolean all) {
        return ResponseEntity.ok(s3FileService.listFiles(all));
    }

    @Operation(summary = "Upload a file")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "File uploaded successfully"),
            @ApiResponse(responseCode = "400", description = "File is empty", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded", content = @Content)
    })
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileMetadataResponse> uploadFile(
            @Parameter(description = "File to upload") @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        FileMetadataResponse fileMetadataResponse = s3FileService.uploadFile(file);
        return new ResponseEntity<>(fileMetadataResponse, HttpStatus.CREATED);
    }

    @Operation(summary = "Download a file by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "File content returned"),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "403", description = "Access denied - not the file owner", content = @Content),
            @ApiResponse(responseCode = "404", description = "File not found", content = @Content),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded", content = @Content)
    })
    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> downloadFile(
            @Parameter(description = "File ID") @PathVariable Long id) {
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

    @Operation(summary = "Get file metadata by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Metadata returned"),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "403", description = "Access denied - not the file owner", content = @Content),
            @ApiResponse(responseCode = "404", description = "File not found", content = @Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<FileMetadataResponse> getMetadata(
            @Parameter(description = "File ID") @PathVariable Long id) {
        FileMetadataResponse metadata = s3FileService.getMetadata(id);
        return ResponseEntity.ok(metadata);
    }

    @Operation(summary = "Delete a file (admin only)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "File deleted"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Access denied - requires ADMIN role"),
            @ApiResponse(responseCode = "404", description = "File not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFile(
            @Parameter(description = "File ID") @PathVariable Long id) {
        s3FileService.deleteFile(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Generate a presigned download URL (valid 1 hour)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Presigned URL returned"),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "403", description = "Access denied - not the file owner", content = @Content),
            @ApiResponse(responseCode = "404", description = "File not found", content = @Content)
    })
    @GetMapping("/{id}/share")
    public ResponseEntity<PresignedUrlResponse> shareFile(
            @Parameter(description = "File ID") @PathVariable Long id) {
        Duration expiration = Duration.ofHours(1);
        String presignedUrl = s3FileService.generatePresignedUrl(id, expiration);
        return ResponseEntity.ok(new PresignedUrlResponse(presignedUrl));
    }
}
