package com.iplytics.system.api;

import com.iplytics.system.service.SystemService;
import com.iplytics.system.service.SystemStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
@Tag(name = "System", description = "Platform status and metadata")
public class SystemController {

    private final SystemService systemService;

    public SystemController(SystemService systemService) {
        this.systemService = systemService;
    }

    @GetMapping("/status")
    @Operation(summary = "Application version and database schema version")
    public SystemStatus status() {
        return systemService.status();
    }
}
