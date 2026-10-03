package com.teamora.common.web;

import com.teamora.common.exception.ApiErrorWriter;
import com.teamora.config.TeamoraProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Caps every request body at {@code teamora.max-request-bytes} (3.5 MB): a declared
 * {@code Content-Length} over the cap is refused with 413 before anything is read,
 * and a body without one (chunked) is counted as it streams and cut off at the cap
 * (surfacing as {@link PayloadTooLargeException} → 413 in the exception handler).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    static final String MESSAGE = "That upload is too large. Photos must be under 2 MB — please retake or choose a smaller one.";

    private final long maxBytes;
    private final ApiErrorWriter errorWriter;

    public RequestSizeLimitFilter(TeamoraProperties props, ApiErrorWriter errorWriter) {
        this.maxBytes = props.maxRequestBytesOrDefault();
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBytes) {
            response.setHeader("Connection", "close");
            errorWriter.write(request, response, HttpStatus.PAYLOAD_TOO_LARGE, MESSAGE);
            return;
        }
        chain.doFilter(new LimitedRequest(request, maxBytes), response);
    }

    /** Request whose body stream throws once more than {@code limit} bytes have been read. */
    static final class LimitedRequest extends HttpServletRequestWrapper {
        private final long limit;
        private ServletInputStream stream;

        LimitedRequest(HttpServletRequest request, long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) {
                stream = new CountingStream(super.getInputStream(), limit);
            }
            return stream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String enc = getCharacterEncoding();
            Charset cs = enc != null ? Charset.forName(enc) : StandardCharsets.UTF_8;
            return new BufferedReader(new InputStreamReader(getInputStream(), cs));
        }
    }

    static final class CountingStream extends ServletInputStream {
        private final ServletInputStream in;
        private final long limit;
        private long count;

        CountingStream(ServletInputStream in, long limit) {
            this.in = in;
            this.limit = limit;
        }

        private void add(long n) throws PayloadTooLargeException {
            if (n > 0) {
                count += n;
                if (count > limit) {
                    throw new PayloadTooLargeException(limit);
                }
            }
        }

        @Override
        public int read() throws IOException {
            int b = in.read();
            if (b >= 0) add(1);
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = in.read(b, off, len);
            add(n);
            return n;
        }

        @Override
        public boolean isFinished() {
            return in.isFinished();
        }

        @Override
        public boolean isReady() {
            return in.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            in.setReadListener(listener);
        }
    }
}
