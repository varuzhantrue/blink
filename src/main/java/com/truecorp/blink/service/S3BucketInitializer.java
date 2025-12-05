package com.truecorp.blink.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
public class S3BucketInitializer {
    private final S3Client s3Client;

    @Value("${minio.bucket-name}")
    private String bucketName;

    public S3BucketInitializer(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    @PostConstruct
    public void initializeBucket() {
        try {
            HeadBucketRequest headBucketRequest = HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build();

            try {
                s3Client.headBucket(headBucketRequest);
                System.out.println("S3 Bucket '" + bucketName + "' already exists.");
            } catch (S3Exception e) {
                if (e.statusCode() == 404) {
                    s3Client.createBucket(b -> b.bucket(bucketName));
                    System.out.println("S3 Bucket '" + bucketName + "' created successfully.");
                } else {
                    throw e;
                }
            }
        } catch (Exception e) {
            System.out.println("FATAL: Could not initialize S3 Bucket. Check MinIO/S3 server connection or credentials.");
            e.printStackTrace();
        }
    }
}
