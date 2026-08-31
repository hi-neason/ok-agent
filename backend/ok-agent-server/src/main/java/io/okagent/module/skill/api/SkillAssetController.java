package io.okagent.module.skill.api;

import io.okagent.module.skill.application.*;
import io.okagent.shared.api.Response;
import io.okagent.shared.api.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/skills")
public class SkillAssetController {
    private final SkillAssetService service;

    public SkillAssetController(SkillAssetService service) {
        this.service = service;
    }

    /** Returns reusable skill assets paginated by most-recently-updated. */
    @GetMapping
    public Response<PageResponse<SkillAssetResponse>> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return Response.success(PageResponse.of(service.list(page, size)));
    }

    /** Imports one complete Skill ZIP archive and parses metadata from its root SKILL.md. */
    @PostMapping(path = "/import", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public Response<SkillAssetResponse> importArchive(
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "") String name,
            @RequestParam(defaultValue = "") String description,
            @RequestParam String businessDomain,
            @RequestParam(defaultValue = "false") boolean overwrite)
            throws IOException {
        return Response.success(service.importArchive(
                file.getOriginalFilename(), file.getBytes(), name, description, businessDomain, overwrite));
    }

    /** Updates only the editable name, description, and business domain of an imported Skill. */
    @PutMapping("/{id}/metadata")
    public Response<SkillAssetResponse> updateMetadata(
            @PathVariable UUID id, @Valid @RequestBody SkillMetadataRequest request) {
        return Response.success(service.updateMetadata(id, request));
    }

    /** Lists the complete file manifest of an imported Skill for directory-tree rendering. */
    @GetMapping("/{id}/files")
    public Response<List<SkillFileResponse>> listFiles(@PathVariable UUID id) {
        return Response.success(service.listFiles(id));
    }

    /** Returns one Skill file for text preview without exposing unrelated archive content. */
    @GetMapping("/{id}/file")
    public Response<SkillFileContentResponse> getFile(@PathVariable UUID id, @RequestParam String path) {
        return Response.success(service.getFile(id, path));
    }

    /** Saves one UTF-8 Skill text file and synchronizes SKILL.md metadata when applicable. */
    @PutMapping("/{id}/file")
    public Response<SkillFileContentResponse> updateFile(
            @PathVariable UUID id, @Valid @RequestBody SkillFileUpdateRequest request) {
        return Response.success(service.updateFile(id, request));
    }

    /** Enables or disables a skill asset for new Agent configuration references. */
    @PatchMapping("/{id}/enabled")
    public Response<SkillAssetResponse> enabled(@PathVariable UUID id, @RequestParam boolean value) {
        return Response.success(service.enabled(id, value));
    }

    /** Deletes a skill asset that is no longer managed by the platform. */
    @DeleteMapping("/{id}")
    public Response<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return Response.success(null);
    }

    /** Returns a safe, actionable validation response for a rejected Skill archive. */
    @ExceptionHandler(SkillArchiveValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Response<Void> handleArchiveValidation(
            SkillArchiveValidationException exception, HttpServletRequest request) {
        return Response.error(exception.getCode(), exception.getMessage(), request.getRequestURI());
    }
}
