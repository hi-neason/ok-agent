package io.okagent.web.skill;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record SkillFileUpdateRequest(
        @NotBlank @Size(max = 700) String path,
        @NotNull @Size(max = 1048576) String content,
        @PositiveOrZero long version) {}
