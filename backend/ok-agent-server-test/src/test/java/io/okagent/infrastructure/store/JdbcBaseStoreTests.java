package io.okagent.infrastructure.store;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcBaseStoreTests {

    @Test
    void rejectsCorruptPersistedValuesInsteadOfTreatingThemAsEmpty() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq("agent/42"), eq("MEMORY.md")))
                .thenReturn(List.of(Map.of(
                        "item_key", "MEMORY.md",
                        "value_json", "not-json",
                        "version", 3L)));
        JdbcBaseStore store = new JdbcBaseStore(jdbc, new ObjectMapper());

        assertThatThrownBy(() -> store.get(List.of("agent", "42"), "MEMORY.md"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("namespace=agent/42")
                .hasMessageContaining("key=MEMORY.md");
    }
}
