package com.genphish.campaign.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CompanyContextMdcFilterTest {

    private final CompanyContextMdcFilter filter = new CompanyContextMdcFilter();

    @Test
    void doFilterInternal_WhenCompanyIdInPath_ShouldPopulateMdcDuringChainAndClearAfter() throws ServletException, IOException {
        String companyId = "123e4567-e89b-12d3-a456-426614174000";

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/companies/" + companyId + "/employees");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> seenInsideChain = new AtomicReference<>();

        FilterChain chain = (req, res) -> seenInsideChain.set(MDC.get("companyId"));

        filter.doFilter(request, response, chain);

        assertThat(seenInsideChain.get()).isEqualTo(companyId);
        assertThat(MDC.get("companyId")).isNull();
    }

    @Test
    void doFilterInternal_WhenNoCompanyIdInPath_ShouldNotPopulateMdc() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> seenInsideChain = new AtomicReference<>("not-empty");
        FilterChain chain = (req, res) -> seenInsideChain.set(MDC.get("companyId"));

        filter.doFilter(request, response, chain);

        assertThat(seenInsideChain.get()).isNull();
        assertThat(MDC.get("companyId")).isNull();
    }
}
