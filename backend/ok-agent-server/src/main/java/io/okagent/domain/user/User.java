package io.okagent.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    @Column(nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private UserSource source = UserSource.CONSOLE;

    @Column(name = "username", nullable = false, unique = true, length = 128)
    private String username;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private AccountRole role = AccountRole.VIEWER;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    @Column(length = 255)
    private String email;

    @Column(length = 64)
    private String phone;

    @Column(name = "group_id")
    private UUID groupId;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

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
        this(id, userId, UserSource.CONSOLE, username, displayName, null, email, phone, groupId, enabled);
    }

    public User(
            UUID id,
            String userId,
            UserSource source,
            String username,
            String displayName,
            String avatarUrl,
            String email,
            String phone,
            UUID groupId,
            boolean enabled) {
        this.id = id;
        this.userId = userId;
        this.source = source;
        this.username = username;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
        this.email = email;
        this.phone = phone;
        this.groupId = groupId;
        this.enabled = enabled;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /** Creates a one-user-id principal auto-provisioned from a channel identity. */
    public static User forChannel(UUID id, String userId, String username, String displayName, String avatarUrl) {
        return new User(
                id,
                userId,
                UserSource.CHANNEL,
                username,
                displayName == null || displayName.isBlank() ? username : displayName,
                avatarUrl,
                null,
                null,
                null,
                true);
    }

    public void update(String username, String displayName, String email, String phone, UUID groupId, boolean enabled) {
        this.username = username;
        this.displayName = displayName;
        this.email = email;
        this.phone = phone;
        this.groupId = groupId;
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }

    /** Initializes credentials for a console account exactly once. */
    public void initializeCredentials(String passwordHash, AccountRole role) {
        if (source != UserSource.CONSOLE) {
            throw new IllegalStateException("CHANNEL_USER_CANNOT_SIGN_IN");
        }
        if (this.passwordHash != null) {
            throw new IllegalStateException("ACCOUNT_CREDENTIALS_ALREADY_INITIALIZED");
        }
        this.passwordHash = passwordHash;
        this.role = role;
        this.updatedAt = Instant.now();
    }

    public boolean hasCredentials() {
        return passwordHash != null;
    }

    /** Records successful interactive authentication without exposing credential state. */
    public void recordSuccessfulLogin() {
        this.lastLoginAt = Instant.now();
        this.updatedAt = this.lastLoginAt;
    }

    public UUID getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public UserSource getSource() {
        return source;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public AccountRole getRole() {
        return role;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
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

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public long getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
