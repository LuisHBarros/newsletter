package com.assine.content.adapters.inbound.rest.webhook;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;

/**
 * Captures the raw request body bytes for {@code /webhooks/notion} so the
 * controller can verify the HMAC signature against the exact bytes and then
 * parse JSON without re-reading the stream.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RawBodyWrappingFilter extends OncePerRequestFilter {

    public static final String RAW_BODY_ATTR = "content.raw_body";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod())
                || !request.getRequestURI().startsWith("/webhooks/notion")) {
            chain.doFilter(request, response);
            return;
        }

        byte[] body = request.getInputStream().readAllBytes();
        request.setAttribute(RAW_BODY_ATTR, body);
        chain.doFilter(new CachedRequest(request, body), response);
    }

    private static final class CachedRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        CachedRequest(HttpServletRequest delegate, byte[] body) {
            super(delegate);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return in.read(); }
                @Override public boolean isFinished() { return in.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener l) {}
            };
        }
    }
}
