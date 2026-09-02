package io.okagent.infrastructure.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.harness.agent.filesystem.remote.store.BaseStore;
import io.agentscope.harness.agent.filesystem.remote.store.StoreItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * MySQL-backed {@link BaseStore} implementation.
 *
 * <p>Backs the harness workspace filesystem so that routed workspace paths (e.g. the agent's
 * long-term memory: {@code MEMORY.md} and {@code memory/YYYY-MM-DD.md}) are persisted in MySQL
 * instead of local disk. Items are addressed by a hierarchical {@code namespace} tuple plus an
 * {@code item_key} (the file path within that namespace).
 *
 * <p>Value payloads are stored as JSON (the harness serialises each file into a small map with
 * {@code content}/{@code encoding}/{@code created_at}/{@code modified_at} fields). A {@code version}
 * column provides the compare-and-swap semantics the harness relies on for concurrent edits.
 */
@Repository
public class JdbcBaseStore implements BaseStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcBaseStore(JdbcTemplate jdbcTemplate, ObjectMapper json) {
        this.jdbc = jdbcTemplate;
        this.json = json;
    }

    private static String namespace(List<String> namespace) {
        return String.join("/", namespace);
    }

    @Override
    public StoreItem get(List<String> namespace, String key) {
        String ns = namespace(namespace);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT item_key, value_json, version FROM workspace_kv " + "WHERE namespace = ? AND item_key = ?",
                ns,
                key);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        String valueJson = (String) row.get("value_json");
        long version = ((Number) row.get("version")).longValue();
        return new StoreItem(key, parse(ns, key, valueJson), version);
    }

    @Override
    public void put(List<String> namespace, String key, Map<String, Object> value) {
        String ns = namespace(namespace);
        String valueJson = write(value);
        jdbc.update(
                "INSERT INTO workspace_kv (namespace, item_key, value_json, version, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 1, NOW(6), NOW(6)) "
                        + "ON DUPLICATE KEY UPDATE value_json = VALUES(value_json), "
                        + "version = version + 1, updated_at = NOW(6)",
                ns,
                key,
                valueJson);
    }

    @Override
    public boolean putIfVersion(List<String> namespace, String key, Map<String, Object> value, long expectedVersion) {
        String ns = namespace(namespace);
        String valueJson = write(value);
        if (expectedVersion == 0L) {
            // create-if-absent: succeed only when the key does not yet exist
            int rows = jdbc.update(
                    "INSERT IGNORE INTO workspace_kv "
                            + "(namespace, item_key, value_json, version, created_at, updated_at) "
                            + "VALUES (?, ?, ?, 1, NOW(6), NOW(6))",
                    ns,
                    key,
                    valueJson);
            return rows == 1;
        }
        int rows = jdbc.update(
                "UPDATE workspace_kv SET value_json = ?, version = version + 1, updated_at = NOW(6) "
                        + "WHERE namespace = ? AND item_key = ? AND version = ?",
                valueJson,
                ns,
                key,
                expectedVersion);
        return rows == 1;
    }

    @Override
    public List<StoreItem> search(List<String> namespace, int limit, int offset) {
        String ns = namespace(namespace);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT item_key, value_json, version FROM workspace_kv "
                        + "WHERE namespace = ? ORDER BY item_key LIMIT ? OFFSET ?",
                ns,
                limit,
                offset);
        List<StoreItem> items = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            String k = (String) row.get("item_key");
            String valueJson = (String) row.get("value_json");
            long version = ((Number) row.get("version")).longValue();
            items.add(new StoreItem(k, parse(ns, k, valueJson), version));
        }
        return items;
    }

    @Override
    public void delete(List<String> namespace, String key) {
        String ns = namespace(namespace);
        jdbc.update("DELETE FROM workspace_kv WHERE namespace = ? AND item_key = ?", ns, key);
    }

    private Map<String, Object> parse(String namespace, String key, String valueJson) {
        try {
            return json.readValue(valueJson, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to deserialize BaseStore value namespace=" + namespace + ", key=" + key,
                    e);
        }
    }

    private String write(Map<String, Object> value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize BaseStore value", e);
        }
    }
}
