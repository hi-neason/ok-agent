package io.okagent.domain.skill;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "skill_file")
public class SkillFile {
  @Id private UUID id;

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

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected SkillFile() {}

  public SkillFile(SkillAsset skill, String filePath, String mediaType, byte[] content) {
    this.id = UUID.randomUUID();
    this.skill = skill;
    this.filePath = filePath;
    this.mediaType = mediaType;
    this.fileSize = content.length;
    this.content = content.clone();
    this.createdAt = Instant.now();
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
}
