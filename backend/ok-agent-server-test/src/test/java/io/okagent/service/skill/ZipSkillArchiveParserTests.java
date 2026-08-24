package io.okagent.service.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class ZipSkillArchiveParserTests {
    private final SkillArchiveParser parser = new ZipSkillArchiveParser();

    @Test
    void shouldParseRootMetadataAndNestedFiles() throws Exception {
        var archive = zip(Map.of(
                "SKILL.md",
                "---\nname: customer-support\ndescription: Guides support work\n---\n# Skill",
                "references/policy.md",
                "# Policy"));

        var parsed = parser.parse("customer-support.zip", archive);

        assertThat(parsed.name()).isEqualTo("customer-support");
        assertThat(parsed.description()).isEqualTo("Guides support work");
        assertThat(parsed.files())
                .extracting(file -> file.path())
                .containsExactlyInAnyOrder("SKILL.md", "references/policy.md");
    }

    @Test
    void shouldRejectArchiveWithoutRootSkillMarkdown() throws Exception {
        var archive = zip(Map.of("nested/SKILL.md", "---\nname: nested\ndescription: Invalid root\n---"));

        assertThatThrownBy(() -> parser.parse("nested.zip", archive))
                .isInstanceOf(SkillArchiveValidationException.class)
                .hasMessageContaining("SKILL.md was found below an outer directory");
    }

    @Test
    void shouldIgnoreMacMetadataPythonCacheAndNestedArchive() throws Exception {
        var archive = zip(Map.of(
                "SKILL.md",
                "---\nname: clean-skill\ndescription: Clean package\n---",
                "references/guide.md",
                "# Guide",
                "__MACOSX/._SKILL.md",
                "binary metadata",
                "scripts/__pycache__/cli.cpython-312.pyc",
                "bytecode",
                ".DS_Store",
                "metadata",
                "clean-skill.zip",
                "nested archive"));

        var parsed = parser.parse("clean-skill.zip", archive);

        assertThat(parsed.files())
                .extracting(file -> file.path())
                .containsExactlyInAnyOrder("SKILL.md", "references/guide.md");
    }

    @Test
    void shouldRejectTraversalDriveAndDuplicatePaths() throws Exception {
        assertThatThrownBy(() -> parser.parse("unsafe.zip", zip(Map.of("folder/..", "unsafe"))))
                .isInstanceOf(SkillArchiveValidationException.class)
                .hasMessageContaining("unsafe file path");
        assertThatThrownBy(() -> parser.parse("unsafe.zip", zip(Map.of("C:/secret.txt", "unsafe"))))
                .isInstanceOf(SkillArchiveValidationException.class)
                .hasMessageContaining("unsafe file path");

        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            add(zip, "folder\\file.txt", "first");
            add(zip, "folder/file.txt", "second");
        }
        assertThatThrownBy(() -> parser.parse("duplicate.zip", output.toByteArray()))
                .isInstanceOf(SkillArchiveValidationException.class)
                .hasMessageContaining("duplicate file paths");
    }

    @Test
    void shouldRejectAnOversizedExpandedFile() throws Exception {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("large.bin"));
            byte[] chunk = new byte[1024 * 1024];
            for (int i = 0; i < 17; i++) zip.write(chunk);
            zip.closeEntry();
        }

        assertThat(output.size()).isLessThan(100_000);
        assertThatThrownBy(() -> parser.parse("large.zip", output.toByteArray()))
                .isInstanceOf(SkillArchiveValidationException.class)
                .hasMessageContaining("larger than 16 MB");
    }

    @Test
    void shouldCountDirectoryAndIgnoredEntriesAgainstTheEntryLimit() throws Exception {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            for (int i = 0; i <= 1_000; i++) {
                zip.putNextEntry(new ZipEntry("directory-" + i + "/"));
                zip.closeEntry();
            }
        }

        assertThatThrownBy(() -> parser.parse("entries.zip", output.toByteArray()))
                .isInstanceOf(SkillArchiveValidationException.class)
                .hasMessageContaining("too many entries");
    }

    private byte[] zip(Map<String, String> files) throws Exception {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            for (var file : files.entrySet()) {
                add(zip, file.getKey(), file.getValue());
            }
        }
        return output.toByteArray();
    }

    private void add(ZipOutputStream zip, String path, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
