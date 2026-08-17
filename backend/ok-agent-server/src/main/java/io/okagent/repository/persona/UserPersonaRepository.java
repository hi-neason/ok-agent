package io.okagent.repository.persona;

import io.okagent.domain.persona.UserPersona;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserPersonaRepository extends JpaRepository<UserPersona, String> {
}
