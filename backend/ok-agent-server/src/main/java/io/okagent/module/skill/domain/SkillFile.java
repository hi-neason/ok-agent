package io.okagent.module.skill.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Entity
@Table(name = "skill_file")
public class SkillFile {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private SkillAsset skill;

    @Column(name = "file_path", nullable = false, length = 700)
    private String filePath;

    @Column(name = "media_type", nullable = false, length = 128)
    private String mediaType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGBLOB")
    private byte[] content;

    @Column(name = "content_sha256", nullable = false, length = 64)
    private String contentSha256;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SkillFile() {}

    public SkillFile(SkillAsset skill, String filePath, String mediaType, byte[] content) {
        this.id = UUID.randomUUID();
        this.skill = skill;
        this.filePath = filePath;
        this.mediaType = mediaType;
        this.fileSize = content.length;
        this.content = content.clone();
        this.contentSha256 = sha256(content);
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void updateContent(byte[] content) {
        this.content = content.clone();
        this.fileSize = content.length;
        this.contentSha256 = sha256(content);
        this.updatedAt = Instant.now();
    }

    public String getFilePath() {
        return filePath;
    }

    public String getMediaType() {
        return mediaType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public byte[] getContent() {
        return content.clone();
    }

    public long getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
