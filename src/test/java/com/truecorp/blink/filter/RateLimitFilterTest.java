package com.truecorp.blink.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.truecorp.blink.service.RateLimitService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private RateLimitService rateLimitService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RateLimitFilter rateLimitFilter;

    @BeforeEach
    void setUp() {
        rateLimitFilter = new RateLimitFilter(rateLimitService, objectMapper);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String username) {
        var auth = new UsernamePasswordAuthenticationToken(username, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void doFilterInternal_ShouldPassThrough_WhenUriIsNotRateLimited() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/files");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        rateLimitFilter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(rateLimitService);
    }

    @Test
    void doFilterInternal_ShouldPassThrough_WhenUserIsNotAuthenticated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/files/upload");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        rateLimitFilter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(rateLimitService);
    }

    @Test
    void doFilterInternal_ShouldPassThrough_WhenUploadIsAllowed() throws Exception {
        authenticateAs("alice");
        when(rateLimitService.isAllowed("alice", "upload")).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/files/upload");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        rateLimitFilter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void doFilterInternal_ShouldReturn429_WhenUploadIsRateLimited() throws Exception {
        authenticateAs("alice");
        when(rateLimitService.isAllowed("alice", "upload")).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/files/upload");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        rateLimitFilter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertEquals(429, response.getStatus());
        assertEquals("application/json", response.getContentType());

        @SuppressWarnings("unchecked")
        Map<String, String> body = objectMapper.readValue(response.getContentAsString(), Map.class);
        assertEquals("Rate limit exceeded. Try again later.", body.get("error"));
    }

    @Test
    void doFilterInternal_ShouldPassThrough_WhenDownloadIsAllowed() throws Exception {
        authenticateAs("bob");
        when(rateLimitService.isAllowed("bob", "download")).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/files/42/download");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        rateLimitFilter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_ShouldReturn429_WhenDownloadIsRateLimited() throws Exception {
        authenticateAs("bob");
        when(rateLimitService.isAllowed("bob", "download")).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/files/42/download");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        rateLimitFilter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertEquals(429, response.getStatus());
        assertEquals("application/json", response.getContentType());

        @SuppressWarnings("unchecked")
        Map<String, String> body = objectMapper.readValue(response.getContentAsString(), Map.class);
        assertEquals("Rate limit exceeded. Try again later.", body.get("error"));
    }

    @Test
    void doFilterInternal_ShouldNotRateLimit_WhenGetRequestIsNotDownload() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/files/42/metadata");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        rateLimitFilter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(rateLimitService);
    }

    @Test
    void doFilterInternal_ShouldNotRateLimit_WhenDeleteRequestOnUploadUri() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/files/upload");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        rateLimitFilter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(rateLimitService);
    }
}
