package io.okagent.module.skill.application;

public class SkillArchiveValidationException extends RuntimeException {
    private final String code;

    public SkillArchiveValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
