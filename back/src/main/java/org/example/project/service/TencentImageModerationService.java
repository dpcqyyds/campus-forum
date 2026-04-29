package org.example.project.service;

import org.example.project.dto.TencentImageModerationResult;
import org.example.project.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class TencentImageModerationService {
    private static final Logger log = LoggerFactory.getLogger(TencentImageModerationService.class);

    private final CosStorageService cosStorageService;
    private final RestTemplate restTemplate;
    private final boolean enabled;
    private final String bizType;
    private final int largeImageDetect;

    public TencentImageModerationService(
            CosStorageService cosStorageService,
            RestTemplate tencentRestTemplate,
            @Value("${app.moderation.tencent.image.enabled:false}") boolean enabled,
            @Value("${app.moderation.tencent.image.biz-type:}") String bizType,
            @Value("${app.moderation.tencent.image.large-image-detect:0}") int largeImageDetect
    ) {
        this.cosStorageService = cosStorageService;
        this.restTemplate = tencentRestTemplate;
        this.enabled = enabled;
        this.bizType = bizType == null ? "" : bizType.trim();
        this.largeImageDetect = largeImageDetect;
    }

    public boolean isEnabled() {
        return enabled && cosStorageService.isEnabled();
    }

    public boolean isConfiguredEnabled() {
        return enabled;
    }

    public TencentImageModerationResult moderateImageReference(String imageReference) {
        return moderateImageReference(imageReference, null);
    }

    public TencentImageModerationResult moderateImageReference(String imageReference, String traceId) {
        String objectKey = cosStorageService.extractObjectKey(imageReference);
        if (!StringUtils.hasText(objectKey)) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("image", imageReference);
            log.warn("图片引用不是平台 COS 图片 - TraceId: {}, Image: {}", displayTraceId(traceId), imageReference);
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, 42203, "图片必须通过平台上传后再提交", data);
        }
        TencentImageModerationResult result = moderateObjectKey(objectKey, traceId);
        result.setUrl(imageReference);
        return result;
    }

    public TencentImageModerationResult moderateObjectKey(String objectKey) {
        return moderateObjectKey(objectKey, null);
    }

    public TencentImageModerationResult moderateObjectKey(String objectKey, String traceId) {
        if (!isEnabled()) {
            TencentImageModerationResult result = new TencentImageModerationResult();
            result.setSuccess(true);
            result.setResult(0);
            result.setObjectKey(objectKey);
            result.setSkipped(true);
            result.setSkipReason("disabled");
            log.info("腾讯云图片审核跳过 - TraceId: {}, ObjectKey: {}, Reason: disabled",
                    displayTraceId(traceId),
                    objectKey);
            return result;
        }
        try {
            Instant start = Instant.now();
            log.info("腾讯云图片审核请求开始 - TraceId: {}, ObjectKey: {}, BizType: {}, LargeImageDetect: {}",
                    displayTraceId(traceId),
                    objectKey,
                    StringUtils.hasText(bizType) ? bizType : "-",
                    largeImageDetect);
            URL auditUrl = cosStorageService.generatePresignedGetUrl(objectKey, auditParams(), Duration.ofMinutes(10));
            ResponseEntity<byte[]> response = restTemplate.getForEntity(auditUrl.toURI(), byte[].class);
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            String cosRequestId = firstNonBlank(
                    response.getHeaders().getFirst("x-cos-request-id"),
                    response.getHeaders().getFirst("x-ci-request-id")
            );
            String responseBody = decodeResponseBody(response.getBody());
            log.info("腾讯云图片审核响应到达 - TraceId: {}, ObjectKey: {}, HttpStatus: {}, CosRequestId: {}, DurationMs: {}",
                    displayTraceId(traceId),
                    objectKey,
                    response.getStatusCode().value(),
                    firstNonBlank(cosRequestId, "-"),
                    durationMs);
            log.info("腾讯云图片审核原始反馈 - TraceId: {}, ObjectKey: {}, Response: {}",
                    displayTraceId(traceId),
                    objectKey,
                    responseBody);
            TencentImageModerationResult result = parseResponse(responseBody);
            result.setObjectKey(objectKey);
            log.info("腾讯云图片审核反馈 - TraceId: {}, ObjectKey: {}, JobId: {}, Result: {}, Label: {}, SubLabel: {}, Score: {}, Text: {}",
                    displayTraceId(traceId),
                    objectKey,
                    result.getJobId(),
                    result.getResult(),
                    result.getLabel(),
                    result.getSubLabel(),
                    result.getScore(),
                    result.getText());
            return result;
        } catch (ApiException ex) {
            throw ex;
        } catch (HttpStatusCodeException ex) {
            String responseBody = decodeResponseBody(ex.getResponseBodyAsByteArray());
            TencentImageModerationError error = parseErrorResponse(responseBody);
            log.warn("腾讯云图片审核原始反馈 - TraceId: {}, ObjectKey: {}, HttpStatus: {}, Response: {}",
                    displayTraceId(traceId),
                    objectKey,
                    ex.getStatusCode().value(),
                    responseBody);
            log.warn("腾讯云图片审核错误反馈 - TraceId: {}, ObjectKey: {}, HttpStatus: {}, Code: {}, Message: {}, RequestId: {}, CiTraceId: {}",
                    displayTraceId(traceId),
                    objectKey,
                    ex.getStatusCode().value(),
                    firstNonBlank(error.getCode(), "-"),
                    firstNonBlank(error.getMessage(), "-"),
                    firstNonBlank(error.getRequestId(), "-"),
                    firstNonBlank(error.getTraceId(), "-"));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("objectKey", objectKey);
            data.put("tencentCode", error.getCode());
            data.put("tencentMessage", error.getMessage());
            data.put("requestId", error.getRequestId());
            String message = "图片审核失败，请稍后重试或联系管理员";
            if ("InvalidArgument".equals(error.getCode())
                    && StringUtils.hasText(error.getMessage())
                    && error.getMessage().toLowerCase(Locale.ROOT).contains("biztype")) {
                message = "图片审核策略配置不存在，请检查腾讯云图片审核 biz-type";
            }
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, 42204, message, data);
        } catch (Exception ex) {
            log.error("腾讯云图片审核失败 - TraceId: {}, ObjectKey: {}",
                    displayTraceId(traceId),
                    objectKey,
                    ex);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("objectKey", objectKey);
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, 42204, "图片审核失败，请稍后重试或联系管理员", data);
        }
    }

    public void assertAllowed(TencentImageModerationResult result, String imageReference) {
        assertAllowed(result, imageReference, null);
    }

    public void assertAllowed(TencentImageModerationResult result, String imageReference, String traceId) {
        if (result == null || result.isPass() || result.isSkipped() || result.isReview()) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("image", imageReference);
        data.put("result", result.getResult());
        data.put("label", result.getLabel());
        data.put("subLabel", result.getSubLabel());
        data.put("score", result.getScore());
        data.put("text", result.getText());
        String message = imageModerationMessage(result);
        log.warn("图片审核未通过 - TraceId: {}, Image: {}, Result: {}, Label: {}, SubLabel: {}, Score: {}",
                displayTraceId(traceId),
                imageReference,
                result.getResult(),
                result.getLabel(),
                result.getSubLabel(),
                result.getScore());
        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, 42203, message, data);
    }

    private String imageModerationMessage(TencentImageModerationResult result) {
        String label = result == null ? null : firstNonBlank(result.getSubLabel(), result.getLabel());
        if (!StringUtils.hasText(label)) {
            return "图片审核未通过，请更换图片";
        }
        String lower = label.toLowerCase(Locale.ROOT);
        if (lower.contains("porn")
                || lower.contains("sexy")
                || lower.contains("sexual")
                || lower.contains("exposed")
                || label.contains("色情")
                || label.contains("低俗")) {
            return "图片疑似包含色情低俗内容，请更换图片";
        }
        if (lower.contains("terror")
                || lower.contains("violence")
                || lower.contains("bloody")
                || label.contains("暴恐")
                || label.contains("暴力")) {
            return "图片疑似包含暴力恐怖内容，请更换图片";
        }
        if (lower.contains("polit")
                || label.contains("政治")
                || label.contains("敏感")) {
            return "图片疑似包含政治敏感内容，请更换图片";
        }
        if (lower.contains("ad")
                || lower.contains("qr")
                || lower.contains("contact")
                || label.contains("广告")
                || label.contains("推广")
                || label.contains("引流")) {
            return "图片疑似包含广告或引流信息，请更换图片";
        }
        if (lower.contains("illegal")
                || label.contains("违法")
                || label.contains("违规")) {
            return "图片疑似包含违法违规内容，请更换图片";
        }
        return "图片审核未通过，请更换图片";
    }

    private Map<String, String> auditParams() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("ci-process", "sensitive-content-recognition");
        params.put("async", "0");
        if (StringUtils.hasText(bizType)) {
            params.put("biz-type", bizType);
        }
        if (largeImageDetect > 0) {
            params.put("large-image-detect", String.valueOf(largeImageDetect));
        }
        return params;
    }

    private TencentImageModerationResult parseResponse(String responseBody) throws Exception {
        if (!StringUtils.hasText(responseBody)) {
            throw new ApiException("腾讯云图片审核响应为空");
        }
        Document document = parseXml(responseBody);
        Element root = document.getDocumentElement();
        String rootName = root == null ? "" : root.getNodeName();
        if ("Error".equals(rootName)) {
            String code = text(root, "Code");
            String message = text(root, "Message");
            throw new ApiException("腾讯云图片审核返回错误: " + firstNonBlank(message, code));
        }

        TencentImageModerationResult result = new TencentImageModerationResult();
        result.setSuccess(true);
        result.setJobId(text(root, "JobId"));
        result.setState(text(root, "State"));
        result.setObjectKey(text(root, "Object"));
        result.setUrl(text(root, "Url"));
        result.setResult(parseInt(text(root, "Result"), 0));
        result.setLabel(firstNonBlank(text(root, "Label"), "Normal"));
        result.setCategory(text(root, "Category"));
        result.setSubLabel(text(root, "SubLabel"));
        result.setScore(parseInt(text(root, "Score"), 0));
        result.setText(text(root, "Text"));
        result.setRawResponse(responseBody);
        return result;
    }

    private TencentImageModerationError parseErrorResponse(String responseBody) {
        TencentImageModerationError error = new TencentImageModerationError();
        if (!StringUtils.hasText(responseBody)) {
            return error;
        }
        try {
            Document document = parseXml(responseBody);
            Element root = document.getDocumentElement();
            if (root == null || !"Error".equals(root.getNodeName())) {
                return error;
            }
            error.setCode(text(root, "Code"));
            error.setMessage(text(root, "Message"));
            error.setRequestId(text(root, "RequestId"));
            error.setTraceId(text(root, "TraceId"));
            return error;
        } catch (Exception ex) {
            log.debug("腾讯云图片审核错误响应解析失败", ex);
            return error;
        }
    }

    private Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private String text(Element root, String tagName) {
        if (root == null || !StringUtils.hasText(tagName)) {
            return null;
        }
        var nodes = root.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node != null
                    && node.getNodeType() == Node.ELEMENT_NODE
                    && tagName.equals(node.getNodeName())) {
                String value = node.getTextContent();
                return StringUtils.hasText(value) ? value.trim() : null;
            }
        }
        return null;
    }

    private String decodeResponseBody(byte[] responseBody) {
        if (responseBody == null || responseBody.length == 0) {
            return null;
        }
        return new String(responseBody, StandardCharsets.UTF_8);
    }

    private Integer parseInt(String value, Integer fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private String firstNonBlank(String first, String fallback) {
        return StringUtils.hasText(first) ? first : fallback;
    }

    private String displayTraceId(String traceId) {
        return StringUtils.hasText(traceId) ? traceId : "-";
    }

    private static class TencentImageModerationError {
        private String code;
        private String message;
        private String requestId;
        private String traceId;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getRequestId() {
            return requestId;
        }

        public void setRequestId(String requestId) {
            this.requestId = requestId;
        }

        public String getTraceId() {
            return traceId;
        }

        public void setTraceId(String traceId) {
            this.traceId = traceId;
        }
    }
}
