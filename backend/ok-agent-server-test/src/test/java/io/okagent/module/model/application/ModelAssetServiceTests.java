package io.okagent.module.model.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.okagent.module.model.domain.ModelAsset;
import io.okagent.module.model.domain.ModelType;
import io.okagent.module.model.infrastructure.persistence.ModelAssetRepository;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ModelAssetServiceTests {
    private final ModelAssetRepository repository = mock(ModelAssetRepository.class);
    private final ApiKeyCipher cipher = new ApiKeyCipher("unit-test-encryption-key");
    private final ModelAssetService service =
            new ModelAssetServiceImpl(repository, cipher, mock(ModelConnectionTester.class));

    @Test
    void createsDecisionModelWithEncryptedCredentialAndSafeResponse() {
        var stored = new AtomicReference<ModelAsset>();
        when(repository.save(any(ModelAsset.class))).thenAnswer(invocation -> {
            ModelAsset asset = invocation.getArgument(0);
            stored.set(asset);
            return asset;
        });

        var response = service.create(request("test-credential"));

        assertThat(response.type()).isEqualTo(ModelType.DECISION);
        assertThat(response.apiKeyConfigured()).isTrue();
        assertThat(response.toString()).doesNotContain("test-credential");
        assertThat(stored.get().getApiKeyCiphertext()).doesNotContain("test-credential");
        assertThat(cipher.decrypt(stored.get().getApiKeyCiphertext())).isEqualTo("test-credential");
    }

    @Test
    void updatesDecisionModelWhileRetainingSavedCredentialWhenBlank() {
        var id = UUID.randomUUID();
        var encrypted = cipher.encrypt("test-credential");
        var asset = new ModelAsset(
                id, "Original", ModelType.DECISION, "Custom", "original", "https://example.invalid", encrypted, true);
        when(repository.findById(id)).thenReturn(Optional.of(asset));

        var response = service.update(id, request(""));

        assertThat(response.type()).isEqualTo(ModelType.DECISION);
        assertThat(response.name()).isEqualTo("Decision model");
        assertThat(asset.getApiKeyCiphertext()).isEqualTo(encrypted);
    }

    @Test
    void rejectsDecisionModelWithoutCredential() {
        assertThatThrownBy(() -> service.create(request("")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("API key is required");
        verifyNoInteractions(repository);
    }

    private ModelAssetRequest request(String apiKey) {
        return new ModelAssetRequest(
                "Decision model",
                ModelType.DECISION,
                "Custom",
                "decision-model",
                "https://example.invalid",
                apiKey,
                true);
    }
}
