package io.okagent.module.skill.application;

public interface SkillArchiveParser {
    /** Validates and parses one ZIP archive whose root contains a SKILL.md file. */
    ParsedSkillArchive parse(String archiveName, byte[] archive);

    /** Validates and extracts reusable metadata from SKILL.md YAML front matter. */
    ParsedSkillMetadata parseMetadata(String markdown);
}
