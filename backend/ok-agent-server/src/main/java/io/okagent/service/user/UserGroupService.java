package io.okagent.service.user;

import io.okagent.web.user.CreateUserGroupRequest;
import io.okagent.web.user.UpdateUserGroupRequest;
import io.okagent.web.user.UserGroupResponse;
import java.util.List;
import java.util.UUID;

public interface UserGroupService {
    /** Returns all user groups with their current member counts. */
    List<UserGroupResponse> list();

    /** Creates a new user group with a unique group key. */
    UserGroupResponse create(CreateUserGroupRequest request);

    /** Returns one user group by id. */
    UserGroupResponse get(UUID id);

    /** Updates the editable fields of a user group. */
    UserGroupResponse update(UUID id, UpdateUserGroupRequest request);

    /** Deletes a user group that no longer contains any members. */
    void delete(UUID id);
}
