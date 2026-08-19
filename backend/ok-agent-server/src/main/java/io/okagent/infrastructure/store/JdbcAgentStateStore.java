package io.okagent.infrastructure.store;

import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.State;
import io.agentscope.core.util.JsonUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * MySQL-backed {@link AgentStateStore}. Replaces the in-memory implementation used by the debug
 * runtime so agent working memory (multi-turn context, toolkit state, etc.) survives JVM restarts.
 *
 * <p>All state is serialised with the AgentScope JSON codec and stored as immutable rows keyed by
 * {@code (user_id, session_id, state_key, item_index)}. List state uses one row per element with a
 * monotonically increasing {@code item_index}; single state uses {@code item_index = 0}.
 */
@Repository
public class JdbcAgentStateStore implements AgentStateStore {

    private static final String ANON_USER = "__anon__";

    private final JdbcTemplate jdbc;

    public JdbcAgentStateStore(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    private static String user(String userId) {
        return (userId == null || userId.isBlank()) ? ANON_USER : userId;
    }

    @Override
    public void save(String userId, String sessionId, String key, State value) {
        String json = JsonUtils.getJsonCodec().toJson(value);
        jdbc.update(
                "INSERT INTO agent_state (user_id, session_id, state_key, item_index, state_data, version) "
                        + "VALUES (?, ?, ?, 0, ?, 1) "
                        + "ON DUPLICATE KEY UPDATE state_data = VALUES(state_data), version = version + 1",
                user(userId),
                sessionId,
                key,
                json);
    }

    @Override
    public void save(String userId, String sessionId, String key, List<? extends State> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        String u = user(userId);
        jdbc.update(
                "DELETE FROM agent_state WHERE user_id = ? AND session_id = ? AND state_key = ?", u, sessionId, key);
        List<Object[]> batch = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            batch.add(
                    new Object[] {u, sessionId, key, i, JsonUtils.getJsonCodec().toJson(values.get(i))});
        }
        jdbc.batchUpdate(
                "INSERT INTO agent_state (user_id, session_id, state_key, item_index, state_data,"
                        + " version) VALUES (?, ?, ?, ?, ?, 1)",
                batch);
    }

    @Override
    public <T extends State> Optional<T> get(String userId, String sessionId, String key, Class<T> type) {
        return jdbc
                .query(
                        "SELECT state_data FROM agent_state "
                                + "WHERE user_id = ? AND session_id = ? AND state_key = ? AND item_index = 0",
                        (rs, n) -> JsonUtils.getJsonCodec().fromJson(rs.getString("state_data"), type),
                        user(userId),
                        sessionId,
                        key)
                .stream()
                .findFirst();
    }

    @Override
    public <T extends State> List<T> getList(String userId, String sessionId, String key, Class<T> itemType) {
        return jdbc.query(
                "SELECT state_data FROM agent_state "
                        + "WHERE user_id = ? AND session_id = ? AND state_key = ? ORDER BY item_index",
                (rs, n) -> JsonUtils.getJsonCodec().fromJson(rs.getString("state_data"), itemType),
                user(userId),
                sessionId,
                key);
    }

    @Override
    public boolean exists(String userId, String sessionId) {
        return jdbc.queryForObject(
                        "SELECT 1 FROM agent_state WHERE user_id = ? AND session_id = ? LIMIT 1",
                        (rs, n) -> true,
                        user(userId),
                        sessionId)
                != null;
    }

    @Override
    public void delete(String userId, String sessionId) {
        jdbc.update("DELETE FROM agent_state WHERE user_id = ? AND session_id = ?", user(userId), sessionId);
    }

    @Override
    public Set<String> listSessionIds(String userId) {
        List<String> ids = jdbc.queryForList(
                "SELECT DISTINCT session_id FROM agent_state WHERE user_id = ? ORDER BY session_id",
                String.class,
                user(userId));
        return new HashSet<>(ids);
    }
}
