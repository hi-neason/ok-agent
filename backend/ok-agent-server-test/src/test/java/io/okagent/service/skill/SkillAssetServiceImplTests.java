package io.okagent.service.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.okagent.domain.skill.SkillAsset;
import io.okagent.domain.skill.SkillSourceType;
import io.okagent.repository.skill.SkillAssetRepository;
import io.okagent.web.skill.SkillAssetRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class SkillAssetServiceImplTests {
  @Mock private SkillAssetRepository repository;

  private SkillAssetService service;

  @BeforeEach
  void setUp() {
    service = new SkillAssetServiceImpl(repository);
  }

  @Test
  void shouldCreateImportedSkillAsset() {
    when(repository.existsBySkillKey("customer-support")).thenReturn(false);
    when(repository.save(any(SkillAsset.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    var request = request("customer-support");

    var response = service.create(request);

    assertThat(response.skillKey()).isEqualTo("customer-support");
    assertThat(response.sourceType()).isEqualTo(SkillSourceType.FILE_IMPORT);
    assertThat(response.entryFile()).isEqualTo("SKILL.md");
    assertThat(response.content()).contains("# Customer support");
  }

  @Test
  void shouldRejectDuplicateSkillKey() {
    when(repository.existsBySkillKey("customer-support")).thenReturn(true);

    assertThatThrownBy(() -> service.create(request("customer-support")))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Skill key already exists");
  }

  private SkillAssetRequest request(String skillKey) {
    return new SkillAssetRequest(
        skillKey,
        "Customer support",
        "Guides support conversations",
        "v1",
        SkillSourceType.FILE_IMPORT,
        null,
        "SKILL.md",
        "# Customer support\n\nFollow the support workflow.",
        true);
  }
}
