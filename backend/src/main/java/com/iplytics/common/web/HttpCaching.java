package com.iplytics.common.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import org.springframework.core.MethodParameter;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Conditional GETs (docs/api.md, "Caching"). The ETag is the data version: it changes only when a transform or
 * analytics refresh completes. A matching {@code If-None-Match} is answered with 304 before any query runs;
 * successful responses carry the ETag and {@code Cache-Control: public, max-age=60}. Errors are never cached.
 */
public final class HttpCaching {

    static final String ETAG_ATTRIBUTE = HttpCaching.class.getName() + ".etag";
    static final CacheControl CACHE_CONTROL = CacheControl.maxAge(java.time.Duration.ofSeconds(60)).cachePublic();

    private HttpCaching() {
    }

    static boolean matches(String ifNoneMatch, String etag) {
        if (ifNoneMatch == null) {
            return false;
        }
        String opaque = strip(etag);
        return Arrays.stream(ifNoneMatch.split(",")).map(String::trim)
                .anyMatch(candidate -> candidate.equals("*") || strip(candidate).equals(opaque));
    }

    private static String strip(String tag) {
        return tag.startsWith("W/") ? tag.substring(2) : tag;
    }

    /** Short-circuits unchanged GETs with 304 Not Modified. */
    @Component
    public static class ETagInterceptor implements HandlerInterceptor {

        private final DataVersionService versions;

        public ETagInterceptor(DataVersionService versions) {
            this.versions = versions;
        }

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            if (!"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod())) {
                return true;
            }
            DataVersionService.DataVersion version = versions.current();
            if (version == null) {
                return true;
            }
            String etag = version.etag();
            if (matches(request.getHeader(HttpHeaders.IF_NONE_MATCH), etag)) {
                response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                response.setHeader(HttpHeaders.ETAG, etag);
                response.setHeader(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL.getHeaderValue());
                return false;
            }
            request.setAttribute(ETAG_ATTRIBUTE, etag);
            return true;
        }
    }

    /** Adds ETag and Cache-Control to successful {@link ApiResponse} bodies only. */
    @ControllerAdvice
    public static class CacheHeadersAdvice implements ResponseBodyAdvice<Object> {

        @Override
        public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
            return ApiResponse.class.isAssignableFrom(returnType.getParameterType());
        }

        @Override
        public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                                      Class<? extends HttpMessageConverter<?>> converterType,
                                      ServerHttpRequest request, ServerHttpResponse response) {
            if (request instanceof ServletServerHttpRequest servletRequest
                    && servletRequest.getServletRequest().getAttribute(ETAG_ATTRIBUTE) instanceof String etag) {
                response.getHeaders().setETag(etag);
                response.getHeaders().setCacheControl(CACHE_CONTROL);
            }
            return body;
        }
    }
}
