package io.okagent.web.skill;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SkillMetadataRequest(
        @NotBlank @Size(max = 128) String name,
        @NotBlank @Size(max = 1024) String description,
        @NotBlank @Size(max = 64) String businessDomain) {}
