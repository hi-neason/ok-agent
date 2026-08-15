package io.okagent.web.mcp;

import io.okagent.service.mcp.McpServerService;
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

    /** Returns all managed MCP server configurations. */
    @GetMapping
    public List<McpServerResponse> list() {
        return service.list();
    }

    /** Registers a new reusable MCP server. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public McpServerResponse create(@Valid @RequestBody McpServerRequest request) {
        return service.create(request);
    }

    /** Updates an existing MCP server configuration. */
    @PutMapping("/{id}")
    public McpServerResponse update(@PathVariable UUID id, @Valid @RequestBody McpServerRequest request) {
        return service.update(id, request);
    }

    /** Enables or disables an MCP server. */
    @PatchMapping("/{id}/enabled")
    public McpServerResponse enabled(@PathVariable UUID id, @RequestParam boolean value) {
        return service.setEnabled(id, value);
    }

    /** Deletes an MCP server and its tool snapshots. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    /** Tests an unsaved MCP connection and returns its advertised tools. */
    @PostMapping("/inspect")
    public McpInspectionResponse inspectDraft(@Valid @RequestBody McpServerRequest request) {
        return service.inspect(request);
    }

    /** Tests a saved MCP connection and refreshes its advertised tool snapshot. */
    @PostMapping("/{id}/inspect")
    public McpInspectionResponse inspect(@PathVariable UUID id) {
        return service.inspect(id);
    }

    /** Returns the latest discovered tools for a saved MCP server. */
    @GetMapping("/{id}/tools")
    public List<McpToolResponse> tools(@PathVariable UUID id) {
        return service.tools(id);
    }

    /** Invokes one MCP tool with development arguments and returns its protocol result. */
    @PostMapping("/{id}/tools/{toolName}/call")
    public McpToolCallResponse callTool(
            @PathVariable UUID id, @PathVariable String toolName, @Valid @RequestBody McpToolCallRequest request) {
        return service.callTool(id, toolName, request);
    }
}
