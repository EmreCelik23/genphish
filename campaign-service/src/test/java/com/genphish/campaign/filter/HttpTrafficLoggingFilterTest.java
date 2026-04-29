package com.genphish.campaign.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class HttpTrafficLoggingFilterTest {

    private final HttpTrafficLoggingFilter filter = new HttpTrafficLoggingFilter();

    @Test
    void doFilterInternal_ShouldCopyWrappedResponseBodyToClientResponse() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/test");
        request.setContentType("application/json");
        request.setCharacterEncoding("UTF-8");
        request.setContent("{\"hello\":\"world\"}".getBytes());

        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;
            httpResponse.setContentType("application/json");
            httpResponse.setCharacterEncoding("UTF-8");
            httpResponse.getWriter().write("{\"ok\":true}");
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo("{\"ok\":true}");
    }

    @Test
    void doFilterInternal_WithBinaryContentType_ShouldStillCompleteSuccessfully() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/upload");
        request.setContentType("multipart/form-data");
        request.setCharacterEncoding("UTF-8");

        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            HttpServletResponse res = (HttpServletResponse) servletResponse;
            res.setContentType("application/octet-stream");
            res.getOutputStream().write(new byte[]{1, 2, 3});
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsByteArray()).containsExactly(1, 2, 3);
    }
}
