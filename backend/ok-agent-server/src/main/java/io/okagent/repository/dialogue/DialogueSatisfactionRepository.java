package io.okagent.repository.dialogue;

import io.okagent.domain.dialogue.DialogueSatisfaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DialogueSatisfactionRepository extends JpaRepository<DialogueSatisfaction, String> {
    @Query("select avg(s.rating) from DialogueSatisfaction s")
    Double averageRating();
}
