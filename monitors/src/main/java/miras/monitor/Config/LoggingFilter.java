package miras.monitor.Config;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class LoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        
        // Evitamos loggear las rutas de SSE porque el body es un stream continuo y puede causar problemas
        if (request.getRequestURI().contains("sse/stream")) {
            filterChain.doFilter(request, response);
            return;
        }

        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();
        filterChain.doFilter(requestWrapper, responseWrapper);
        long timeTaken = System.currentTimeMillis() - startTime;

        String requestBody = getStringValue(requestWrapper.getContentAsByteArray(), request.getCharacterEncoding());
        String responseBody = getStringValue(responseWrapper.getContentAsByteArray(), response.getCharacterEncoding());

        System.out.println("\n--- API HTTP REQUEST ---");
        System.out.println("Method: " + request.getMethod() + " " + request.getRequestURI());
        System.out.println("Status: " + response.getStatus() + " | Time: " + timeTaken + "ms");
        
        if (!requestBody.isEmpty()) {
            System.out.println("-> Request Body: " + requestBody);
        }
        if (!responseBody.isEmpty()) {
            System.out.println("<- Response Body: " + responseBody);
        }
        System.out.println("------------------------\n");

        responseWrapper.copyBodyToResponse();
    }

    private String getStringValue(byte[] contentAsByteArray, String characterEncoding) {
        try {
            if (contentAsByteArray.length == 0) return "";
            if (characterEncoding == null) characterEncoding = "UTF-8";
            return new String(contentAsByteArray, 0, contentAsByteArray.length, characterEncoding);
        } catch (Exception e) {
            return "";
        }
    }
}
