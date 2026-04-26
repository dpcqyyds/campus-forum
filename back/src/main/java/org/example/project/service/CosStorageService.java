package org.example.project.service;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import jakarta.annotation.PreDestroy;
import org.example.project.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class CosStorageService {
    private static final Logger log = LoggerFactory.getLogger(CosStorageService.class);

    private final boolean enabled;
    private final String secretId;
    private final String secretKey;
    private final String region;
    private final String bucket;
    private final String domain;
    private final String folder;
    private COSClient cosClient;

    public CosStorageService(
            @Value("${app.cos.enabled:false}") boolean enabled,
            @Value("${app.cos.secret-id:}") String secretId,
            @Value("${app.cos.secret-key:}") String secretKey,
            @Value("${app.cos.region:ap-beijing}") String region,
            @Value("${app.cos.bucket:}") String bucket,
            @Value("${app.cos.domain:}") String domain,
            @Value("${app.cos.folder:uploads/images/}") String folder
    ) {
        this.enabled = enabled;
        this.secretId = trim(secretId);
        this.secretKey = trim(secretKey);
        this.region = trim(region);
        this.bucket = trim(bucket);
        this.domain = trimTrailingSlash(domain);
        this.folder = normalizeFolder(folder);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String upload(byte[] content, String originalFilename, String contentType) {
        if (!enabled) {
            throw new ApiException("COS 上传未启用");
        }
        if (content == null || content.length == 0) {
            throw new ApiException("图片内容为空");
        }
        initClientIfNecessary();
        String key = buildObjectKey(originalFilename);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(content.length);
        if (StringUtils.hasText(contentType)) {
            metadata.setContentType(contentType.trim());
        }
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(content)) {
            log.info("COS 上传请求开始 - Bucket: {}, Region: {}, ObjectKey: {}, Size: {}, ContentType: {}",
                    bucket,
                    region,
                    key,
                    content.length,
                    StringUtils.hasText(contentType) ? contentType : "-");
            cosClient.putObject(new PutObjectRequest(bucket, key, inputStream, metadata));
            log.info("COS 上传请求完成 - Bucket: {}, Region: {}, ObjectKey: {}",
                    bucket,
                    region,
                    key);
            return key;
        } catch (Exception ex) {
            log.error("COS 上传请求失败 - Bucket: {}, Region: {}, ObjectKey: {}",
                    bucket,
                    region,
                    key,
                    ex);
            throw new ApiException("COS 图片上传失败");
        }
    }

    public void deleteQuietly(String key) {
        if (!enabled || !StringUtils.hasText(key)) {
            return;
        }
        try {
            initClientIfNecessary();
            String objectKey = stripLeadingSlash(key);
            cosClient.deleteObject(bucket, objectKey);
            log.info("COS 对象清理完成 - Bucket: {}, ObjectKey: {}", bucket, objectKey);
        } catch (Exception ex) {
            log.warn("COS 对象清理失败 - Bucket: {}, ObjectKey: {}, Message: {}",
                    bucket,
                    key,
                    ex.getMessage());
        }
    }

    public String getUrl(String key) {
        if (!StringUtils.hasText(key)) {
            throw new ApiException("COS 文件 Key 不能为空");
        }
        return effectiveDomain() + "/" + stripLeadingSlash(key);
    }

    public String extractObjectKey(String imageReference) {
        String value = trim(imageReference);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            return stripLeadingSlash(value);
        }
        try {
            URI uri = URI.create(value);
            String expectedHost = URI.create(effectiveDomain()).getHost();
            if (!equalsIgnoreCase(uri.getHost(), expectedHost)) {
                return null;
            }
            return stripLeadingSlash(uri.getPath());
        } catch (Exception ex) {
            return null;
        }
    }

    public URL generatePresignedGetUrl(String key, Map<String, String> requestParameters, Duration ttl) {
        initClientIfNecessary();
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, stripLeadingSlash(key), HttpMethodName.GET);
        Date expiration = Date.from(Instant.now().plus(ttl == null ? Duration.ofMinutes(10) : ttl));
        request.setExpiration(expiration);
        if (requestParameters != null) {
            requestParameters.forEach((name, value) -> {
                if (StringUtils.hasText(name) && value != null) {
                    request.addRequestParameter(name, value);
                }
            });
        }
        return cosClient.generatePresignedUrl(request);
    }

    @PreDestroy
    public void destroy() {
        if (cosClient != null) {
            cosClient.shutdown();
        }
    }

    private synchronized void initClientIfNecessary() {
        if (cosClient != null) {
            return;
        }
        validateProperties();
        COSCredentials credentials = new BasicCOSCredentials(secretId, secretKey);
        ClientConfig clientConfig = new ClientConfig(new Region(region));
        cosClient = new COSClient(credentials, clientConfig);
        log.info("COS 客户端初始化完成 - Bucket: {}, Region: {}, DomainConfigured: {}",
                bucket,
                region,
                StringUtils.hasText(domain));
    }

    private void validateProperties() {
        if (!StringUtils.hasText(secretId)
                || !StringUtils.hasText(secretKey)
                || !StringUtils.hasText(region)
                || !StringUtils.hasText(bucket)) {
            throw new ApiException("COS 配置不完整");
        }
    }

    private String buildObjectKey(String originalFilename) {
        LocalDate today = LocalDate.now();
        String dateFolder = today.getYear() + "/" + String.format("%02d", today.getMonthValue()) + "/";
        return folder + dateFolder + UUID.randomUUID().toString().replace("-", "") + safeSuffix(originalFilename);
    }

    private String safeSuffix(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "";
        }
        String filename = originalFilename.trim();
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        String suffix = filename.substring(dot).toLowerCase(Locale.ROOT);
        return suffix.matches("\\.[a-z0-9]{1,10}") ? suffix : "";
    }

    private String effectiveDomain() {
        if (StringUtils.hasText(domain)) {
            return domain;
        }
        return "https://" + bucket + ".cos." + region + ".myqcloud.com";
    }

    private String normalizeFolder(String rawFolder) {
        String normalized = stripLeadingSlash(trim(rawFolder));
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }

    private String stripLeadingSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        return result;
    }

    private String trimTrailingSlash(String value) {
        String result = trim(value);
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }
}
