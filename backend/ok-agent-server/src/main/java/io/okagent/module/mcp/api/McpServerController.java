package io.okagent.module.mcp.api;

import io.okagent.module.mcp.application.*;
import io.okagent.module.mcp.application.McpServerService;
import io.okagent.shared.api.ApiResponse;
import io.okagent.shared.api.PageResponse;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/mcp-servers")
public class McpServerController {
    private final McpServerService service;

    public McpServerController(McpServerService service) {
        this.service = service;
    }

    /** Returns managed MCP server configurations, paginated by most-recently-updated. */
    @GetMapping
    public ApiResponse<PageResponse<McpServerResponse>> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(PageResponse.of(service.list(page, size)));
    }

    /** Registers a new reusable MCP server. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<McpServerResponse> create(@Valid @RequestBody McpServerRequest request) {
        return ApiResponse.success(service.create(request));
    }

    /** Updates an existing MCP server configuration. */
    @PutMapping("/{id}")
    public ApiResponse<McpServerResponse> update(@PathVariable UUID id, @Valid @RequestBody McpServerRequest request) {
        return ApiResponse.success(service.update(id, request));
    }

    /** Enables or disables an MCP server. */
    @PatchMapping("/{id}/enabled")
    public ApiResponse<McpServerResponse> enabled(@PathVariable UUID id, @RequestParam boolean value) {
        return ApiResponse.success(service.setEnabled(id, value));
    }

    /** Deletes an MCP server and its tool snapshots. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.success(null);
    }

    /** Tests an unsaved MCP connection and returns its advertised tools. */
    @PostMapping("/inspect")
    public ApiResponse<McpInspectionResponse> inspectDraft(@Valid @RequestBody McpServerRequest request) {
        return ApiResponse.success(service.inspect(request));
    }

    /** Tests a saved MCP connection and refreshes its advertised tool snapshot. */
    @PostMapping("/{id}/inspect")
    public ApiResponse<McpInspectionResponse> inspect(@PathVariable UUID id) {
        return ApiResponse.success(service.inspect(id));
    }

    /** Returns the latest discovered tools for a saved MCP server. */
    @GetMapping("/{id}/tools")
    public ApiResponse<List<McpToolResponse>> tools(@PathVariable UUID id) {
        return ApiResponse.success(service.tools(id));
    }

    /** Invokes one MCP tool with development arguments and returns its protocol result. */
    @PostMapping("/{id}/tools/{toolName}/call")
    public ApiResponse<McpToolCallResponse> callTool(
            @PathVariable UUID id, @PathVariable String toolName, @Valid @RequestBody McpToolCallRequest request) {
        return ApiResponse.success(service.callTool(id, toolName, request));
    }
}
