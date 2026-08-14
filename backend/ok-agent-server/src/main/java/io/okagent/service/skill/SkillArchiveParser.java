package io.okagent.service.skill;

public interface SkillArchiveParser {
  /** Validates and parses one ZIP archive whose root contains a SKILL.md file. */
  ParsedSkillArchive parse(String archiveName, byte[] archive);
}
