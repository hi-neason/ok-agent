package io.okagent.service.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
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

    private byte[] zip(Map<String, String> files) throws Exception {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            for (var file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
