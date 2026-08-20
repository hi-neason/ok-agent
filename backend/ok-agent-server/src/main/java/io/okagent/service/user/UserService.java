package io.okagent.service.user;

import io.okagent.web.user.CreateUserRequest;
import io.okagent.web.user.UpdateUserRequest;
import io.okagent.web.user.UserDetailResponse;
import io.okagent.web.user.UserResponse;
import java.util.List;
import java.util.UUID;

public interface UserService {
    /** Returns all users, optionally filtered by group; each carries its group's display name. */
    List<UserResponse> list(UUID groupIdFilter);

    /** Creates a new user with a unique username. */
    UserResponse create(CreateUserRequest request);

    /** Returns one user by id. */
    UserResponse get(UUID id);

    /** Returns the aggregated detail view (profile + channels + life-cycle counts) for a user. */
    UserDetailResponse detail(UUID id);

    /** Updates the editable fields of a user. */
    UserResponse update(UUID id, UpdateUserRequest request);

    /** Permanently removes a user. */
    void delete(UUID id);
}
