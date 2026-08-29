package io.okagent.module;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Lightweight source-level guardrails for the modular-monolith boundaries. */
class ModuleBoundaryTests {
    private static final Path MAIN_SOURCES = locateMainSources();
    private static final Path MODULES = MAIN_SOURCES.resolve("io/okagent/module");

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
        for (String module : List.of(
                "agent",
                "channel",
                "conversation",
                "identity",
                "intent",
                "knowledge",
                "mcp",
                "model",
                "observe",
                "persona",
                "product",
                "release",
                "skill",
                "workbench",
                "workflow")) {
            assertFilesDoNotReference(MODULES.resolve(module + "/domain"), forbidden);
        }
    }

    @Test
    void businessCodeDoesNotReturnToLegacyHorizontalPackages() throws IOException {
        List<String> violations = new ArrayList<>();
        for (String legacy : List.of("domain", "repository", "service", "web")) {
            Path root = MAIN_SOURCES.resolve("io/okagent").resolve(legacy);
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (var paths = Files.walk(root)) {
                paths.filter(file -> file.toString().endsWith(".java"))
                        .forEach(file -> violations.add(MAIN_SOURCES.relativize(file).toString()));
            }
        }
        assertThat(violations).as("Java files in legacy horizontal packages").isEmpty();
    }

    @Test
    void applicationLayersDoNotDependOnHttpAdapters() throws IOException {
        List<String> violations = new ArrayList<>();
        try (var modulePaths = Files.list(MODULES)) {
            for (Path module : modulePaths.filter(Files::isDirectory).toList()) {
                Path application = module.resolve("application");
                if (!Files.isDirectory(application)) {
                    continue;
                }
                try (var sources = Files.walk(application)) {
                    for (Path source : sources.filter(file -> file.toString().endsWith(".java")).toList()) {
                        String content = Files.readString(source);
                        if (content.contains(".api.")) {
                            violations.add(MODULES.relativize(source).toString());
                        }
                    }
                }
            }
        }
        assertThat(violations).as("application classes depending on HTTP adapters").isEmpty();
    }

    @Test
    void controllerEndpointsDeclareTheResponseEnvelope() throws IOException {
        Pattern endpoint = Pattern.compile(
                "@(Get|Post|Put|Patch|Delete)Mapping\\b.*?\\bpublic\\s+([^\\s]+)",
                Pattern.DOTALL);
        List<String> violations = new ArrayList<>();
        try (var sources = Files.walk(MODULES)) {
            for (Path source : sources
                    .filter(file -> file.getFileName().toString().endsWith("Controller.java"))
                    .toList()) {
                var matcher = endpoint.matcher(Files.readString(source));
                while (matcher.find()) {
                    if (!matcher.group(2).startsWith("Response<")) {
                        violations.add(MODULES.relativize(source) + " -> " + matcher.group(2));
                    }
                }
            }
        }
        assertThat(violations).as("controller endpoints returning an unwrapped response").isEmpty();
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
