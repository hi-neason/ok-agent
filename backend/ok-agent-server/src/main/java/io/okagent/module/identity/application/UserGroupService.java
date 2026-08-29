package io.okagent.module.identity.application;

import io.okagent.module.identity.application.CreateUserGroupRequest;
import io.okagent.module.identity.application.UpdateUserGroupRequest;
import io.okagent.module.identity.application.UserGroupResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

public interface UserGroupService {
    /** Returns user groups with their current member counts, newest first, paged. */
    Page<UserGroupResponse> list(int page, int size);

    /** Creates a new user group with a unique group key. */
    UserGroupResponse create(CreateUserGroupRequest request);

    /** Returns one user group by id. */
    UserGroupResponse get(UUID id);

    /** Updates the editable fields of a user group. */
    UserGroupResponse update(UUID id, UpdateUserGroupRequest request);

    /** Deletes a user group that no longer contains any members. */
    void delete(UUID id);
}
