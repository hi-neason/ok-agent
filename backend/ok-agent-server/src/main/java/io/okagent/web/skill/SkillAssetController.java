package io.okagent.web.skill;

import io.okagent.service.skill.SkillArchiveValidationException;
import io.okagent.service.skill.SkillAssetService;
import io.okagent.web.observe.PageResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public PageResponse<SkillAssetResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(service.list(page, size));
    }

    /** Imports one complete Skill ZIP archive and parses metadata from its root SKILL.md. */
    @PostMapping(path = "/import", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public SkillAssetResponse importArchive(
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "") String name,
            @RequestParam(defaultValue = "") String description,
            @RequestParam String businessDomain,
            @RequestParam(defaultValue = "false") boolean overwrite)
            throws IOException {
        return service.importArchive(
                file.getOriginalFilename(), file.getBytes(), name, description, businessDomain, overwrite);
    }

    /** Updates only the editable name, description, and business domain of an imported Skill. */
    @PutMapping("/{id}/metadata")
    public SkillAssetResponse updateMetadata(@PathVariable UUID id, @Valid @RequestBody SkillMetadataRequest request) {
        return service.updateMetadata(id, request);
    }

    /** Lists the complete file manifest of an imported Skill for directory-tree rendering. */
    @GetMapping("/{id}/files")
    public List<SkillFileResponse> listFiles(@PathVariable UUID id) {
        return service.listFiles(id);
    }

    /** Returns one Skill file for text preview without exposing unrelated archive content. */
    @GetMapping("/{id}/file")
    public SkillFileContentResponse getFile(@PathVariable UUID id, @RequestParam String path) {
        return service.getFile(id, path);
    }

    /** Saves one UTF-8 Skill text file and synchronizes SKILL.md metadata when applicable. */
    @PutMapping("/{id}/file")
    public SkillFileContentResponse updateFile(
            @PathVariable UUID id, @Valid @RequestBody SkillFileUpdateRequest request) {
        return service.updateFile(id, request);
    }

    /** Enables or disables a skill asset for new Agent configuration references. */
    @PatchMapping("/{id}/enabled")
    public SkillAssetResponse enabled(@PathVariable UUID id, @RequestParam boolean value) {
        return service.enabled(id, value);
    }

    /** Deletes a skill asset that is no longer managed by the platform. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    /** Returns a safe, actionable validation response for a rejected Skill archive. */
    @ExceptionHandler(SkillArchiveValidationException.class)
    public ResponseEntity<SkillImportErrorResponse> handleArchiveValidation(SkillArchiveValidationException exception) {
        return ResponseEntity.badRequest()
                .body(new SkillImportErrorResponse(exception.getCode(), exception.getMessage()));
    }
}
