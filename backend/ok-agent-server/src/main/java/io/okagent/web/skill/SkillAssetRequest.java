package io.okagent.web.skill;

import io.okagent.domain.skill.SkillSourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SkillAssetRequest(
    @NotBlank
        @Size(max = 128)
        @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "must be kebab-case")
        String skillKey,
    @NotBlank @Size(max = 128) String name,
    @NotBlank @Size(max = 1024) String description,
    @NotBlank @Size(max = 64) String assetVersion,
    @NotNull SkillSourceType sourceType,
    @Size(max = 1024) String sourceUri,
    @NotBlank @Size(max = 255) String entryFile,
    @NotBlank String content,
    boolean enabled) {}
