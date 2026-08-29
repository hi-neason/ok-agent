package io.okagent.module.identity.application;

import io.okagent.module.identity.application.CreateUserRequest;
import io.okagent.module.identity.application.UpdateUserRequest;
import io.okagent.module.identity.application.UserDetailResponse;
import io.okagent.module.identity.application.UserResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

public interface UserService {
    /** Returns users, optionally filtered by group, newest first, paged. */
    Page<UserResponse> list(UUID groupIdFilter, int page, int size);

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
