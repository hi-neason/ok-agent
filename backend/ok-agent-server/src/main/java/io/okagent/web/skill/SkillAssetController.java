package io.okagent.web.skill;

import io.okagent.service.skill.SkillAssetService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/skills")
public class SkillAssetController {
  private final SkillAssetService service;

  public SkillAssetController(SkillAssetService service) {
    this.service = service;
  }

  /** Returns all reusable skill assets in the current management scope. */
  @GetMapping
  public List<SkillAssetResponse> list() {
    return service.list();
  }

  /** Creates a skill asset from manually entered or imported Skill content. */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public SkillAssetResponse create(@Valid @RequestBody SkillAssetRequest request) {
    return service.create(request);
  }

  /** Replaces the editable metadata and content of an existing skill asset. */
  @PutMapping("/{id}")
  public SkillAssetResponse update(
      @PathVariable UUID id, @Valid @RequestBody SkillAssetRequest request) {
    return service.update(id, request);
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
}
