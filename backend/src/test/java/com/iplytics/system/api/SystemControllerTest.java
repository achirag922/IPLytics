package com.iplytics.system.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iplytics.common.web.DataVersionService;
import com.iplytics.system.service.SystemService;
import com.iplytics.system.service.SystemStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SystemController.class)
class SystemControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DataVersionService dataVersions;

    @MockitoBean
    private SystemService systemService;

    @Test
    void returnsStatusAsJson() throws Exception {
        when(systemService.status()).thenReturn(new SystemStatus("iplytics", "0.0.1", "1"));

        mvc.perform(get("/api/v1/system/status"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.application").value("iplytics"))
                .andExpect(jsonPath("$.version").value("0.0.1"))
                .andExpect(jsonPath("$.schemaVersion").value("1"));
    }

    @Test
    void unknownRouteReturnsProblemDetail() throws Exception {
        mvc.perform(get("/api/v1/system/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }
}
