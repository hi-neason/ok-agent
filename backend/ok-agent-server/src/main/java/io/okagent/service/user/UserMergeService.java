package io.okagent.service.user;

import io.okagent.domain.user.User;
import io.okagent.repository.channel.ChannelUserIdentityRepository;
import io.okagent.repository.user.UserRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Merges two one-user-id principals into one.
 *
 * <p>Every runtime datum is keyed by {@code app_user.user_id} (a string UUID): dialogue sessions,
 * agent working state, user personas, traces, workflow audits, and the persona MEMORY.md rows in
 * {@code workspace_kv}. A merge rewrites all rows belonging to the "secondary" user so they point
 * at the "primary" user, then deletes the secondary. Provider identities
 * ({@code channel_user_identity}) are reassigned to the primary.
 *
 * <p>Where a primary and secondary row collide on a unique key (same persona for the same agent,
 * same agent-state slot), the primary wins and the secondary duplicate is dropped — these are
 * derived/working data, not source of truth.
 */
@Service
public class UserMergeService {

    private static final Logger log = LoggerFactory.getLogger(UserMergeService.class);

    private final UserRepository users;
    private final ChannelUserIdentityRepository identities;
    private final JdbcTemplate jdbc;

    public UserMergeService(UserRepository users, ChannelUserIdentityRepository identities, JdbcTemplate jdbc) {
        this.users = users;
        this.identities = identities;
        this.jdbc = jdbc;
    }

    @Transactional
    public void merge(UUID primaryId, UUID secondaryId) {
        if (primaryId.equals(secondaryId)) {
            throw new IllegalArgumentException("无法合并同一个用户");
        }
        User primary = users.findById(primaryId).orElseThrow(() -> new UserNotFoundException("PRIMARY_USER_NOT_FOUND"));
        User secondary =
                users.findById(secondaryId).orElseThrow(() -> new UserNotFoundException("SECONDARY_USER_NOT_FOUND"));

        String keep = primary.getUserId();
        String drop = secondary.getUserId();

        // 1) Provider identities -> primary.
        identities.reassignLinkedUser(secondaryId, primaryId);

        // 2) Dialogue sessions.
        jdbc.update("UPDATE dialogue_session SET user_id = ? WHERE user_id = ?", keep, drop);

        // 3) Traces & workflow audit (observability history; no unique constraints).
        jdbc.update("UPDATE trace_span SET user_id = ? WHERE user_id = ?", keep, drop);
        jdbc.update("UPDATE workflow_execution_audit SET user_id = ? WHERE user_id = ?", keep, drop);

        // 4) Agent working state: reassign, dropping rows that collide with the primary.
        jdbc.update(
                "DELETE FROM agent_state WHERE user_id = ? AND (session_id, state_key, item_index) IN "
                        + "(SELECT session_id, state_key, item_index FROM "
                        + "(SELECT session_id, state_key, item_index FROM agent_state WHERE user_id = ?) AS k)",
                drop,
                keep);
        jdbc.update("UPDATE agent_state SET user_id = ? WHERE user_id = ?", keep, drop);

        // 5) Persona rows keyed (user_id, agent_id): drop secondary duplicates, then reassign.
        jdbc.update(
                "DELETE FROM user_persona WHERE user_id = ? AND agent_id IN "
                        + "(SELECT agent_id FROM (SELECT agent_id FROM user_persona WHERE user_id = ?) AS k)",
                drop,
                keep);
        jdbc.update("UPDATE user_persona SET user_id = ? WHERE user_id = ?", keep, drop);

        // 6) Persona MEMORY.md in workspace_kv, addressed by namespace 'users/{userId}/persona'.
        mergePersonaMemory(keep, drop);

        // 7) Remove the secondary principal.
        users.delete(secondary);

        log.info("Merged user '{}' into '{}' ({} <- {})", keep, primary.getUsername(), primaryId, secondaryId);
    }

    private void mergePersonaMemory(String keep, String drop) {
        String oldNs = "users/" + drop + "/persona";
        String newNs = "users/" + keep + "/persona";
        // Drop secondary keys that already exist under the primary, then move the rest.
        jdbc.update(
                "DELETE FROM workspace_kv WHERE namespace = ? AND item_key IN "
                        + "(SELECT item_key FROM (SELECT item_key FROM workspace_kv WHERE namespace = ?) AS k)",
                oldNs,
                newNs);
        jdbc.update("UPDATE workspace_kv SET namespace = ? WHERE namespace = ?", newNs, oldNs);
    }

    /** Preview of what would be reassigned, for a confirmation dialog. */
    public MergePreview preview(UUID primaryId, UUID secondaryId) {
        User secondary =
                users.findById(secondaryId).orElseThrow(() -> new UserNotFoundException("SECONDARY_USER_NOT_FOUND"));
        List<UUID> identityIds = identities.findByLinkedUserId(secondaryId).stream()
                .map(id -> id.getId())
                .toList();
        long sessions = count("dialogue_session", secondary.getUserId());
        long personas = count("user_persona", secondary.getUserId());
        return new MergePreview(secondary.getId(), secondary.getDisplayName(), identityIds.size(), sessions, personas);
    }

    private long count(String table, String userId) {
        Long c = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE user_id = ?", Long.class, userId);
        return c == null ? 0 : c;
    }

    public record MergePreview(
            UUID secondaryId, String displayName, int identityCount, long sessionCount, long personaCount) {}
}
