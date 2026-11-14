package com.iplytics.common.config;

import com.iplytics.common.web.ApiEnum;
import com.iplytics.common.web.HttpCaching;
import com.iplytics.common.web.RequestLoggingFilter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.format.FormatterRegistry;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** CORS for the React dev server, conditional-GET caching, and API-name conversion of enum query parameters. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final HttpCaching.ETagInterceptor etagInterceptor;
    private final List<String> allowedOrigins;

    public WebConfig(HttpCaching.ETagInterceptor etagInterceptor,
                     @Value("${iplytics.cors.allowed-origins:http://localhost:5173}") List<String> allowedOrigins) {
        this.etagInterceptor = etagInterceptor;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "HEAD", "OPTIONS")
                .exposedHeaders(HttpHeaders.ETAG, RequestLoggingFilter.REQUEST_ID_HEADER)
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(etagInterceptor).addPathPatterns("/api/v1/**").excludePathPatterns("/api/v1/system/**");
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverterFactory(new ApiEnumConverterFactory());
    }

    /**
     * Enum query parameters: {@link ApiEnum}s match their API name (e.g. {@code strikeRate}) or constant name,
     * case-insensitively; other enums keep Spring's default by-name conversion.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static final class ApiEnumConverterFactory implements ConverterFactory<String, Enum> {

        @Override
        public <T extends Enum> Converter<String, T> getConverter(Class<T> targetType) {
            if (ApiEnum.class.isAssignableFrom(targetType)) {
                return value -> (T) ApiEnum.parse((Class) targetType, value);
            }
            return value -> value.isBlank() ? null : (T) Enum.valueOf(targetType, value.trim());
        }
    }
}
