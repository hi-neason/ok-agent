package io.okagent.service.skill;

import io.okagent.domain.skill.ArchivedSkillFile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Component;

@Component
public class ZipSkillArchiveParser implements SkillArchiveParser {
  private static final long MAX_ARCHIVE_SIZE = 20L * 1024 * 1024;
  private static final long MAX_EXPANDED_SIZE = 80L * 1024 * 1024;
  private static final int MAX_FILES = 500;

  @Override
  public ParsedSkillArchive parse(String archiveName, byte[] archive) {
    if (archiveName == null || !archiveName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
      reject("Only ZIP skill archives are supported");
    }
    if (archive.length == 0 || archive.length > MAX_ARCHIVE_SIZE) {
      reject("Skill archive must be between 1 byte and 20 MB");
    }
    var files = readEntries(archiveName, archive);
    var entry = files.stream().filter(file -> file.path().equals("SKILL.md")).findFirst();
    if (entry.isEmpty()) {
      var nestedEntry =
          files.stream()
              .map(ArchivedSkillFile::path)
              .filter(path -> path.endsWith("/SKILL.md"))
              .findFirst();
      if (nestedEntry.isPresent()) {
        throw new SkillArchiveValidationException(
            "SKILL_MD_NOT_AT_ROOT",
            "SKILL.md was found below an outer directory: " + nestedEntry.orElseThrow());
      }
      reject("SKILL.md must exist at the archive root");
    }
    var markdown = new String(entry.orElseThrow().content(), StandardCharsets.UTF_8);
    var metadata = parseMetadata(markdown);
    var name = metadata.name();
    var description = metadata.description();
    return new ParsedSkillArchive(
        metadata.skillKey(), name, description, markdown, sha256(archive), List.copyOf(files));
  }

  @Override
  public ParsedSkillMetadata parseMetadata(String markdown) {
    var values = parseFrontMatter(markdown);
    var name = values.getOrDefault("name", "").trim();
    var description = values.getOrDefault("description", "").trim();
    if (name.isBlank() || description.isBlank()) {
      reject("SKILL.md front matter must contain name and description");
    }
    if (name.length() > 128 || description.length() > 1024) {
      reject("SKILL.md name or description exceeds the supported length");
    }
    return new ParsedSkillMetadata(toKey(name), name, description);
  }

  private List<ArchivedSkillFile> readEntries(String archiveName, byte[] archive) {
    var files = new ArrayList<ArchivedSkillFile>();
    long expandedSize = 0;
    try (var input = new ZipInputStream(new ByteArrayInputStream(archive))) {
      ZipEntry entry;
      while ((entry = input.getNextEntry()) != null) {
        var path = normalizePath(entry.getName());
        if (entry.isDirectory()) continue;
        if (isPackagingNoise(path, archiveName)) continue;
        if (files.size() >= MAX_FILES) {
          reject("Skill archive expands beyond the allowed limits");
        }
        var output = new ByteArrayOutputStream();
        input.transferTo(output);
        var content = output.toByteArray();
        expandedSize += content.length;
        if (expandedSize > MAX_EXPANDED_SIZE) reject("Skill archive is too large after extraction");
        files.add(new ArchivedSkillFile(path, mediaType(path), content));
      }
    } catch (IOException exception) {
      throw new SkillArchiveValidationException("INVALID_SKILL_ARCHIVE", "Invalid ZIP archive");
    }
    return files;
  }

  private boolean isPackagingNoise(String path, String archiveName) {
    var components = path.split("/");
    for (var component : components) {
      if (component.equals("__MACOSX")
          || component.equals("__pycache__")
          || component.equals(".DS_Store")
          || component.startsWith("._")) {
        return true;
      }
    }
    var lower = path.toLowerCase(Locale.ROOT);
    return lower.endsWith(".pyc") || path.equals(archiveName);
  }

  private String normalizePath(String rawPath) {
    var path = rawPath.replace('\\', '/');
    if (path.startsWith("/") || path.contains("../") || path.equals("..")) {
      reject("Archive contains an unsafe file path");
    }
    if (path.length() > 700) reject("Archive contains a file path longer than 700 characters");
    return path;
  }

  private Map<String, String> parseFrontMatter(String markdown) {
    if (!markdown.startsWith("---")) reject("SKILL.md must start with YAML front matter");
    var end = markdown.indexOf("\n---", 3);
    if (end < 0) reject("SKILL.md front matter is not closed");
    var values = new java.util.HashMap<String, String>();
    for (var line : markdown.substring(3, end).split("\\R")) {
      var separator = line.indexOf(':');
      if (separator > 0) {
        values.put(
            line.substring(0, separator).trim().toLowerCase(Locale.ROOT),
            stripQuotes(line.substring(separator + 1).trim()));
      }
    }
    return values;
  }

  private String stripQuotes(String value) {
    if (value.length() >= 2
        && ((value.startsWith("\"") && value.endsWith("\""))
            || (value.startsWith("'") && value.endsWith("'")))) {
      return value.substring(1, value.length() - 1);
    }
    return value;
  }

  private String toKey(String name) {
    var key = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    if (key.isBlank()) reject("Skill name must contain characters usable as an identifier");
    return key;
  }

  private String mediaType(String path) {
    var lower = path.toLowerCase(Locale.ROOT);
    if (lower.endsWith(".md")) return "text/markdown";
    if (lower.endsWith(".json")) return "application/json";
    if (lower.endsWith(".yaml") || lower.endsWith(".yml")) return "application/yaml";
    if (lower.endsWith(".txt")
        || lower.endsWith(".py")
        || lower.endsWith(".js")
        || lower.endsWith(".ts")
        || lower.endsWith(".java")
        || lower.endsWith(".sh")) return "text/plain";
    return "application/octet-stream";
  }

  private String sha256(byte[] archive) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(archive));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private void reject(String reason) {
    throw new SkillArchiveValidationException("INVALID_SKILL_ARCHIVE", reason);
  }
}
