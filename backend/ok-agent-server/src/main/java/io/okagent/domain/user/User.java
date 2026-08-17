package io.okagent.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class User {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true, length = 128)
    private String userId;

    @Column(name = "username", nullable = false, unique = true, length = 128)
    private String username;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(length = 255)
    private String email;

    @Column(length = 64)
    private String phone;

    @Column(name = "group_id")
    private UUID groupId;

    @Column(nullable = false)
    private boolean enabled = true;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {}

    public User(
            UUID id,
            String userId,
            String username,
            String displayName,
            String email,
            String phone,
            UUID groupId,
            boolean enabled) {
        this.id = id;
        this.userId = userId;
        this.username = username;
        this.displayName = displayName;
        this.email = email;
        this.phone = phone;
        this.groupId = groupId;
        this.enabled = enabled;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void update(
            String username,
            String displayName,
            String email,
            String phone,
            UUID groupId,
            boolean enabled) {
        this.username = username;
        this.displayName = displayName;
        this.email = email;
        this.phone = phone;
        this.groupId = groupId;
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public UUID getGroupId() {
        return groupId;
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
