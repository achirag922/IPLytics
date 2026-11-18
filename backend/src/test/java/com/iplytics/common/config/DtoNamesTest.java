package com.iplytics.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.RegexPatternTypeFilter;

/**
 * springdoc names OpenAPI schemas by simple class name, so two response DTOs with the same simple name (in
 * different packages) would silently be merged into one schema and break generated clients.
 */
class DtoNamesTest {

    @Test
    void responseDtoSimpleNamesAreUnique() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(org.springframework.beans.factory.annotation.AnnotatedBeanDefinition b) {
                return true;
            }
        };
        scanner.addIncludeFilter(new RegexPatternTypeFilter(java.util.regex.Pattern.compile(".*Dto")));
        List<String> names = new ArrayList<>();
        scanner.findCandidateComponents("com.iplytics").forEach(bean -> {
            String className = bean.getBeanClassName();
            if (className != null) {
                names.add(className.substring(Math.max(className.lastIndexOf('.'), className.lastIndexOf('$')) + 1));
            }
        });
        Map<String, Long> counts = names.stream().collect(Collectors.groupingBy(n -> n, Collectors.counting()));
        assertThat(names).hasSizeGreaterThan(50);
        assertThat(counts).allSatisfy((name, count) -> assertThat(count).as(name).isEqualTo(1L));
    }
}
