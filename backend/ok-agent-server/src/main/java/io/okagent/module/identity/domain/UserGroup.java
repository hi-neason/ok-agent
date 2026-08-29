package io.okagent.module.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_group")
public class UserGroup {
    @Id
    private UUID id;

    @Column(name = "group_key", nullable = false, unique = true, length = 128)
    private String groupKey;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 1024)
    private String description;

    @Column(nullable = false)
    private boolean enabled = true;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserGroup() {}

    public UserGroup(UUID id, String groupKey, String name, String description, boolean enabled) {
        this.id = id;
        this.groupKey = groupKey;
        this.name = name;
        this.description = description;
        this.enabled = enabled;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void update(String groupKey, String name, String description, boolean enabled) {
        this.groupKey = groupKey;
        this.name = name;
        this.description = description;
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getGroupKey() {
        return groupKey;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
