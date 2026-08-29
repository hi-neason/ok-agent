package io.okagent.module.conversation.infrastructure.persistence;

import io.okagent.module.conversation.domain.DialogueOutcome;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DialogueOutcomeRepository extends JpaRepository<DialogueOutcome, String> {}
