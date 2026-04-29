package com.genphish.campaign.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1) // 2nd in the filter chain, right after MDC
@Slf4j
public class HttpTrafficLoggingFilter extends OncePerRequestFilter {

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
            // Sadece JSON veya Text ise body'i oku, değilse [Binary File] yaz geç.
            String requestBody = isLoggable(request.getContentType())
                    ? getStringValue(requestWrapper.getContentAsByteArray(), request.getCharacterEncoding())
                    : "[Binary File / Unsupported Content]";
            log.info("[REQ] {} {} - Payload: {}", request.getMethod(), request.getRequestURI(), truncate(requestBody, 1000));

            // Log Response Payload
            String responseBody = isLoggable(response.getContentType())
                    ? getStringValue(responseWrapper.getContentAsByteArray(), response.getCharacterEncoding())
                    : "[Binary File / Unsupported Content]";
            log.info("[RES] {} {} - Status: {} - Duration: {}ms - Payload: {}", request.getMethod(), request.getRequestURI(), responseWrapper.getStatus(), duration, truncate(responseBody, 1000));

            // IMPORTANT: Copy content to original response, otherwise the client gets an empty body!
            responseWrapper.copyBodyToResponse();
        }
    }

    private String getStringValue(byte[] contentAsByteArray, String characterEncoding) {
        if (contentAsByteArray == null || contentAsByteArray.length == 0) {
            return "";
        }
        try {
            return new String(contentAsByteArray, characterEncoding != null ? characterEncoding : "UTF-8").replaceAll("[\\r\\n\\t]+", " ");
        } catch (UnsupportedEncodingException e) {
            log.warn("Failed to parse payload with encoding: {}", characterEncoding);
            return "[Parsing Error]";
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.isBlank()) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) + "... [Truncated]" : text;
    }

    private boolean isLoggable(String contentType) {
        return contentType != null &&
                (contentType.contains("application/json") || contentType.contains("text/"));
    }
}
