package com.truecorp.blink.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.truecorp.blink.service.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String operation = resolveOperation(request);

        if (operation == null) {
            filterChain.doFilter(request, response);
            return;
        }

        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        String username = auth.getName();
        if (!rateLimitService.isAllowed(username, operation)) {
            log.warn("Rate limit exceeded for user '{}' on operation '{}'", username, operation);
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(),
                    Map.of("error", "Rate limit exceeded. Try again later."));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveOperation(HttpServletRequest request) {
        String method = request.getMethod();
        String uri = request.getRequestURI();

        if ("POST".equalsIgnoreCase(method) && uri.startsWith("/api/files/upload")) {
            return "upload";
        }
        if ("GET".equalsIgnoreCase(method) && uri.matches("/api/files/[^/]+/download")) {
            return "download";
        }
        return null;
    }
}
