package io.okagent.service.user;

import io.okagent.web.user.CreateUserRequest;
import io.okagent.web.user.UpdateUserRequest;
import io.okagent.web.user.UserDetailResponse;
import io.okagent.web.user.UserResponse;
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
