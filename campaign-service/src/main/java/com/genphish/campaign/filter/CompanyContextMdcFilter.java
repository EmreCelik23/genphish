package com.genphish.campaign.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE) // 1st in the filter chain
public class CompanyContextMdcFilter extends OncePerRequestFilter {

    private static final String COMPANY_ID_MDC_KEY = "companyId";
    // Regex to extract UUID after /companies/, making it agnostic to API version (v1, v2) or context paths
    private static final Pattern COMPANY_ID_PATTERN = Pattern.compile("(?i)/companies/([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            extractAndSetMdc(request.getRequestURI());
            filterChain.doFilter(request, response);
        } finally {
            // Clean up the thread-local MDC to prevent memory leaks in thread pools
            MDC.clear();
        }
    }

    private void extractAndSetMdc(String requestUri) {
        if (requestUri == null) return;
        Matcher matcher = COMPANY_ID_PATTERN.matcher(requestUri);
        if (matcher.find()) {
            String companyId = matcher.group(1);
            MDC.put(COMPANY_ID_MDC_KEY, companyId);
        }
    }
}
