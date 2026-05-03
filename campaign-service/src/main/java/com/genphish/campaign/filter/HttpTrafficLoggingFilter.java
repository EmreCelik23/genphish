package com.genphish.campaign.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.io.UnsupportedEncodingException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2) // 3rd in the filter chain, after MDC and auth filters
@Slf4j
public class HttpTrafficLoggingFilter extends OncePerRequestFilter {

    @Value("${app.logging.http.log-payloads:false}")
    private boolean logPayloads;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Wrap the request and response to cache their payloads
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request, 10240); // 10KB limit
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();

        try {
            // Proceed with the filter chain
            filterChain.doFilter(requestWrapper, responseWrapper);
        } finally {
            long duration = System.currentTimeMillis() - startTime;

            // Log Request Payload
            String requestBody = extractPayload(
                    request.getContentType(),
                    requestWrapper.getContentAsByteArray(),
                    request.getCharacterEncoding()
            );
            log.info("[REQ] {} {} - Payload: {}", request.getMethod(), request.getRequestURI(), truncate(requestBody, 1000));

            // Log Response Payload
            String responseBody = extractPayload(
                    response.getContentType(),
                    responseWrapper.getContentAsByteArray(),
                    response.getCharacterEncoding()
            );
            log.info("[RES] {} {} - Status: {} - Duration: {}ms - Payload: {}", request.getMethod(), request.getRequestURI(), responseWrapper.getStatus(), duration, truncate(responseBody, 1000));

            // IMPORTANT: Copy content to original response, otherwise the client gets an empty body!
            responseWrapper.copyBodyToResponse();
        }
    }

    private String getStringValue(String contentType, byte[] contentAsByteArray, String characterEncoding) {
        if (ObjectUtils.isEmpty(contentAsByteArray)) {
            return "";
        }
        String resolvedEncoding = resolveEncoding(contentType, characterEncoding);
        try {
            return new String(contentAsByteArray, resolvedEncoding).replaceAll("[\\r\\n\\t]+", " ");
        } catch (UnsupportedEncodingException e) {
            log.warn("Failed to parse payload with encoding: {}", resolvedEncoding);
            return "[Parsing Error]";
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.isBlank()) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) + "... [Truncated]" : text;
    }

    private String extractPayload(String contentType, byte[] payloadBytes, String encoding) {
        if (!isLoggable(contentType)) {
            return "[Binary File / Unsupported Content]";
        }
        if (!logPayloads) {
            return "[Payload Logging Disabled]";
        }
        return maskSensitiveValues(getStringValue(contentType, payloadBytes, encoding));
    }

    private String resolveEncoding(String contentType, String encoding) {
        if (StringUtils.hasText(encoding)) {
            return encoding;
        }
        if (contentType == null) {
            return StandardCharsets.UTF_8.name();
        }
        try {
            MediaType mediaType = MediaType.parseMediaType(contentType);
            if (mediaType.getCharset() != null) {
                return mediaType.getCharset().name();
            }
            if (isJsonMediaType(mediaType)) {
                return StandardCharsets.UTF_8.name();
            }
        } catch (Exception ignored) {
            // Fall through to UTF-8 default.
        }
        if (contentType.contains("application/json") || contentType.contains("+json")) {
            return StandardCharsets.UTF_8.name();
        }
        return StandardCharsets.UTF_8.name();
    }

    private String maskSensitiveValues(String payload) {
        if (payload == null || payload.isBlank()) {
            return "";
        }

        String masked = payload;
        masked = masked.replaceAll(
                "(?i)(\\\"(?:password|passwd|pwd|token|accessToken|refreshToken|authorization|secret|apiKey|credential)\\\"\\s*:\\s*\\\")(.*?)(\\\")",
                "$1***$3"
        );
        masked = masked.replaceAll(
                "(?i)((?:password|passwd|pwd|token|access_token|refresh_token|authorization|secret|api_key|credential)=)([^&\\s]+)",
                "$1***"
        );
        masked = masked.replaceAll("(?i)(Bearer\\s+)[a-z0-9._\\-~+/]+=*", "$1***");
        return masked;
    }

    private boolean isLoggable(String contentType) {
        if (contentType == null) {
            return false;
        }
        if (contentType.contains("text/")) {
            return true;
        }
        try {
            return isJsonMediaType(MediaType.parseMediaType(contentType));
        } catch (Exception ignored) {
            return contentType.contains("application/json") || contentType.contains("+json");
        }
    }

    private boolean isJsonMediaType(MediaType mediaType) {
        String subtype = mediaType.getSubtype();
        return MediaType.APPLICATION_JSON.includes(mediaType) || (subtype != null && subtype.contains("+json"));
    }
}
