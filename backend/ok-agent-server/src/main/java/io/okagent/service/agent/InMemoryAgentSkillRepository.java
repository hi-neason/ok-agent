package io.okagent.service.agent;

import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.skill.repository.AgentSkillRepositoryInfo;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only, in-memory {@link AgentSkillRepository} that exposes the skills an agent is bound to.
 * Used by the debug runtime so the HarnessAgent sees the same skill set the console configured.
 */
final class InMemoryAgentSkillRepository implements AgentSkillRepository {
    private final Map<String, AgentSkill> skills = new LinkedHashMap<>();

    InMemoryAgentSkillRepository(List<AgentSkill> skills) {
        for (AgentSkill skill : skills) {
            String name = String.valueOf(skill.getMetadata().get("name"));
            this.skills.put(name, skill);
        }
    }

    @Override
    public AgentSkill getSkill(String name) {
        return skills.get(name);
    }

    @Override
    public List<String> getAllSkillNames() {
        return List.copyOf(skills.keySet());
    }

    @Override
    public List<AgentSkill> getAllSkills() {
        return List.copyOf(skills.values());
    }

    @Override
    public boolean save(List<AgentSkill> skillsToSave, boolean force) {
        throw new UnsupportedOperationException("Debug skill repository is read-only");
    }

    @Override
    public boolean delete(String skillName) {
        throw new UnsupportedOperationException("Debug skill repository is read-only");
    }

    @Override
    public boolean skillExists(String name) {
        return skills.containsKey(name);
    }

    @Override
    public AgentSkillRepositoryInfo getRepositoryInfo() {
        return new AgentSkillRepositoryInfo("ok-agent-debug", "in-memory", false);
    }

    @Override
    public String getSource() {
        return "ok-agent-debug";
    }

    @Override
    public void setWriteable(boolean writeable) {
        // read-only by design
    }

    @Override
    public boolean isWriteable() {
        return false;
    }
}
