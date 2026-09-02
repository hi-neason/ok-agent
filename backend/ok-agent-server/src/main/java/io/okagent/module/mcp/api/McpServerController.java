package io.okagent.module.mcp.api;

import io.okagent.module.mcp.application.*;
import io.okagent.module.mcp.application.McpServerService;
import io.okagent.shared.api.Response;
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
    public Response<PageResponse<McpServerResponse>> list(
            @RequestParam(defaultValue = "0") @jakarta.validation.constraints.Min(0) int page, @RequestParam(defaultValue = "20") @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(100) int size) {
        return Response.success(PageResponse.of(service.list(page, size)));
    }

    /** Registers a new reusable MCP server. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response<McpServerResponse> create(@Valid @RequestBody McpServerRequest request) {
        return Response.success(service.create(request));
    }

    /** Updates an existing MCP server configuration. */
    @PutMapping("/{id}")
    public Response<McpServerResponse> update(@PathVariable UUID id, @Valid @RequestBody McpServerRequest request) {
        return Response.success(service.update(id, request));
    }

    /** Enables or disables an MCP server. */
    @PatchMapping("/{id}/enabled")
    public Response<McpServerResponse> enabled(@PathVariable UUID id, @RequestParam boolean value) {
        return Response.success(service.setEnabled(id, value));
    }

    /** Deletes an MCP server and its tool snapshots. */
    @DeleteMapping("/{id}")
    public Response<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return Response.success(null);
    }

    /** Tests an unsaved MCP connection and returns its advertised tools. */
    @PostMapping("/inspect")
    public Response<McpInspectionResponse> inspectDraft(@Valid @RequestBody McpServerRequest request) {
        return Response.success(service.inspect(request));
    }

    /** Tests a saved MCP connection and refreshes its advertised tool snapshot. */
    @PostMapping("/{id}/inspect")
    public Response<McpInspectionResponse> inspect(@PathVariable UUID id) {
        return Response.success(service.inspect(id));
    }

    /** Returns the latest discovered tools for a saved MCP server. */
    @GetMapping("/{id}/tools")
    public Response<List<McpToolResponse>> tools(@PathVariable UUID id) {
        return Response.success(service.tools(id));
    }

    /** Invokes one MCP tool with development arguments and returns its protocol result. */
    @PostMapping("/{id}/tools/{toolName}/call")
    public Response<McpToolCallResponse> callTool(
            @PathVariable UUID id, @PathVariable String toolName, @Valid @RequestBody McpToolCallRequest request) {
        return Response.success(service.callTool(id, toolName, request));
    }
}
