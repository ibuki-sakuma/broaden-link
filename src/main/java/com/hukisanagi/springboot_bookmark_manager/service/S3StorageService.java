package com.hukisanagi.springboot_bookmark_manager.service;

import io.awspring.cloud.s3.S3Template;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "spring.cloud.aws.s3.enabled", havingValue = "true")
public class S3StorageService implements StorageService {

    private final S3Template s3Template;
    private final S3Client s3Client;
    private final String bucketName;

    public S3StorageService(S3Template s3Template, S3Client s3Client, @org.springframework.beans.factory.annotation.Value("${spring.cloud.aws.s3.bucket-name}") String bucketName) {
        this.s3Template = s3Template;
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    @Override
    public String saveFile(byte[] fileContent, String fileName) {
        InputStream inputStream = new ByteArrayInputStream(fileContent);
        s3Template.upload(bucketName, fileName, inputStream);
        return fileName;
    }

    @Override
    public void deleteFile(String fileName) {
        s3Template.deleteObject(bucketName, fileName);
    }

    @Override
    public String getFileUrl(String fileName) {
        return s3Template.createSignedGetURL(bucketName, fileName, Duration.ofDays(1)).toString();
    }

    @Override
    public String copyFile(String sourceFileName, String destinationFileName) {
        try {
            CopyObjectRequest copyObjectRequest = CopyObjectRequest.builder()
                    .sourceBucket(bucketName)
                    .sourceKey(sourceFileName)
                    .destinationBucket(bucketName)
                    .destinationKey(destinationFileName)
                    .build();
            s3Client.copyObject(copyObjectRequest);
            return destinationFileName;
        } catch (S3Exception e) {
            throw new RuntimeException("Failed to copy file in S3: " + e.awsErrorDetails().errorMessage(), e);
        }
    }

    @Override
    public void clearRankingFavicons() {
        String prefix = "ranking/";
        ListObjectsV2Request listObjectsV2Request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix)
                .build();

        try {
            List<ObjectIdentifier> objectsToDelete = s3Client.listObjectsV2Paginator(listObjectsV2Request).stream()
                    .flatMap(r -> r.contents().stream())
                    .map(S3Object::key)
                    .map(key -> ObjectIdentifier.builder().key(key).build())
                    .collect(Collectors.toList());

            if (!objectsToDelete.isEmpty()) {
                DeleteObjectsRequest deleteObjectsRequest = DeleteObjectsRequest.builder()
                        .bucket(bucketName)
                        .delete(d -> d.objects(objectsToDelete))
                        .build();
                s3Client.deleteObjects(deleteObjectsRequest);
            }
        } catch (S3Exception e) {
            throw new RuntimeException("Failed to clear ranking favicons in S3: " + e.awsErrorDetails().errorMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("An unexpected error occurred while clearing ranking favicons in S3.", e);
        }
    }

    @Override
    public void deleteFolder(String folderName) {
        try {
            // フォルダ名の末尾にスラッシュがない場合は追加
            String prefix = folderName.endsWith("/") ? folderName : folderName + "/";

            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(prefix)
                    .build();

            List<ObjectIdentifier> objectsToDelete = s3Client.listObjectsV2Paginator(listRequest).stream()
                    .flatMap(response -> response.contents().stream())
                    .map(s3Object -> ObjectIdentifier.builder().key(s3Object.key()).build())
                    .collect(Collectors.toList());

            if (!objectsToDelete.isEmpty()) {
                DeleteObjectsRequest deleteRequest = DeleteObjectsRequest.builder()
                        .bucket(bucketName)
                        .delete(d -> d.objects(objectsToDelete))
                        .build();
                s3Client.deleteObjects(deleteRequest);
            }
        } catch (S3Exception e) {
            throw new RuntimeException("Failed to delete folder from S3: " + e.awsErrorDetails().errorMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("An unexpected error occurred while deleting folder from S3.", e);
        }
    }
}