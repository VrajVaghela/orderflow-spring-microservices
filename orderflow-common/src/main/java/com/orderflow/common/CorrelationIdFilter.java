package com.orderflow.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = Correlation.resolve(request.getHeader(Correlation.HEADER));
        Correlation.set(correlationId);
        response.setHeader(Correlation.HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            Correlation.clear();
        }
    }
}
