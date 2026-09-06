package io.okagent.module.release.application;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
class FrozenIntentTests {
    @Test void readsOnlySnapshotRulesAndDisablesLegacyClassification() {
        var config = ReleaseAgentConfig.fromSnapshot("{\"routingIntents\":[{\"intentKey\":\"sales\",\"name\":\"Sales\",\"description\":\"Frozen\"}]}");
        assertThat(config.getResolvedIntents()).singleElement().satisfies(intent -> {
            assertThat(intent.intentKey()).isEqualTo("sales");
            assertThat(intent.description()).isEqualTo("Frozen");
        });
        assertThat(ReleaseAgentConfig.fromSnapshot("{}").getResolvedIntents()).isEmpty();
    }
}
