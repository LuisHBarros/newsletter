package com.assine.content.adapters.outbound.storage.s3;

import com.assine.content.config.ContentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3ContentUploader {

    private final S3Client s3Client;
    private final ContentProperties contentProperties;

    public String uploadHtml(String newsletterSlug, UUID issueId, int version, String html) {
        String key = String.format("%s/%s/%s/v%d/index.html",
                contentProperties.getStorage().getKeyPrefix(),
                newsletterSlug, issueId, version);
        putObject(key, html.getBytes(StandardCharsets.UTF_8), "text/html; charset=utf-8");
        return key;
    }

    public String uploadAsset(String newsletterSlug, UUID issueId, int version, String filename,
                              byte[] bytes, String contentType) {
        String key = String.format("%s/%s/%s/v%d/assets/%s",
                contentProperties.getStorage().getKeyPrefix(),
                newsletterSlug, issueId, version, filename);
        putObject(key, bytes, contentType != null ? contentType : "application/octet-stream");
        return key;
    }

    private void putObject(String key, byte[] bytes, String contentType) {
        String bucket = contentProperties.getStorage().getBucket();
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .contentLength((long) bytes.length)
                            .build(),
                    RequestBody.fromBytes(bytes));
            log.info("Uploaded s3://{}/{} ({} bytes, {})", bucket, key, bytes.length, contentType);
        } catch (Exception e) {
            log.error("Failed to upload s3://{}/{}", bucket, key, e);
            throw new RuntimeException("S3 upload failed", e);
        }
    }
}
