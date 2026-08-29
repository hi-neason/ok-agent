package io.okagent.module;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Lightweight source-level guardrails for the modular-monolith boundaries. */
class ModuleBoundaryTests {
    private static final Path MODULES = locateMainSources().resolve("io/okagent/module");

    @Test
    void productModulesDoNotReachAcrossOwnedBoundaries() throws IOException {
        assertNoReferences("workbench", List.of(
                "io.okagent.web.",
                "io.okagent.module.agentmanager.",
                "io.okagent.module.agentruntime.",
                "io.okagent.module.customerchat."));
        assertNoReferences("agentmanager", List.of(
                "io.okagent.module.workbench.",
                "io.okagent.module.agentruntime.",
                "io.okagent.module.customerchat."));
        assertNoReferences("agentruntime", List.of(
                "io.okagent.module.agentmanager.",
                "io.okagent.module.workbench.",
                "io.okagent.module.customerchat.api."));
        assertNoReferences("customerchat", List.of(
                "io.okagent.module.agentmanager.",
                "io.okagent.module.workbench.",
                "io.okagent.module.agentruntime."));
        assertNoReferences("conversation", List.of(
                "io.okagent.module.agentmanager.",
                "io.okagent.module.workbench.",
                "io.okagent.module.agentruntime.",
                "io.okagent.module.customerchat."));
        assertNoReferences("identity", List.of(
                "io.okagent.module.agentmanager.",
                "io.okagent.module.agentruntime.",
                "io.okagent.module.conversation.",
                "io.okagent.module.customerchat.",
                "io.okagent.module.workbench."));
    }

    @Test
    void customerChatPortStaysFrameworkAndPersistenceIndependent() throws IOException {
        Path application = MODULES.resolve("customerchat/application");
        List<String> forbidden = List.of(
                "org.springframework.",
                "jakarta.",
                "io.okagent.repository.",
                "io.okagent.infrastructure.",
                "io.okagent.web.");
        assertFilesDoNotReference(application, forbidden);
    }

    @Test
    void migratedDomainModelsDoNotDependOnLegacyHorizontalLayers() throws IOException {
        List<String> forbidden = List.of(
                "io.okagent.web.",
                "io.okagent.service.",
                "io.okagent.repository.",
                "io.okagent.domain.");
        assertFilesDoNotReference(MODULES.resolve("conversation/domain"), forbidden);
        assertFilesDoNotReference(MODULES.resolve("identity/domain"), forbidden);
        assertFilesDoNotReference(MODULES.resolve("workbench/domain"), forbidden);
    }

    private static void assertNoReferences(String module, List<String> forbidden) throws IOException {
        assertFilesDoNotReference(MODULES.resolve(module), forbidden);
    }

    private static void assertFilesDoNotReference(Path root, List<String> forbidden) throws IOException {
        List<String> violations = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                for (String reference : forbidden) {
                    if (source.contains(reference)) {
                        violations.add(MODULES.relativize(path) + " -> " + reference);
                    }
                }
            }
        }
        assertThat(violations).as("module boundary violations").isEmpty();
    }

    private static Path locateMainSources() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            for (Path candidate : List.of(
                    current.resolve("ok-agent-server/src/main/java"),
                    current.resolve("backend/ok-agent-server/src/main/java"))) {
                if (Files.isDirectory(candidate)) {
                    return candidate;
                }
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate ok-agent-server main sources");
    }
}
