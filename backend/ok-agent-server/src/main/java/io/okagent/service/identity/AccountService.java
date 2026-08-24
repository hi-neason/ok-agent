package io.okagent.service.identity;

import io.okagent.web.identity.AccountCreateRequest;
import io.okagent.web.identity.AccountPasswordRequest;
import io.okagent.web.identity.AccountResponse;
import io.okagent.web.identity.AccountUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Page;

public interface AccountService {
    /** Lists initialized interactive console accounts ordered by recent changes. */
    Page<AccountResponse> list(int page, int size);

    /** Creates an interactive console account with an encoded password and assigned role. */
    AccountResponse create(AuthenticatedActor actor, AccountCreateRequest request);

    /** Updates an account role and enabled state while preserving the last administrator. */
    AccountResponse update(UUID id, AuthenticatedActor actor, AccountUpdateRequest request);

    /** Replaces an interactive account password with a newly encoded value. */
    void changePassword(UUID id, AuthenticatedActor actor, AccountPasswordRequest request);
}
