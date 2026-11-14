package com.iplytics.support;

import static org.mockito.Mockito.when;

import com.iplytics.common.web.ApiMetaFactory;
import com.iplytics.common.web.DataVersionService;
import com.iplytics.common.web.DataVersionService.DataVersion;
import com.iplytics.common.web.RefLookup;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base for {@code @WebMvcTest} controller slices: the web layer (controllers, advice, interceptors, filters,
 * converters) is real; services and data access are mocks. Data version is fixed at t3-a5.
 */
@Import(ApiMetaFactory.class)
public abstract class ApiControllerTest {

    public static final String ETAG = "W/\"t3-a5\"";

    @Autowired
    protected MockMvc mvc;

    @MockitoBean
    protected DataVersionService dataVersions;

    @MockitoBean
    protected RefLookup refs;

    @BeforeEach
    void fixDataVersion() {
        when(dataVersions.current()).thenReturn(new DataVersion(3L, 5L));
    }
}
