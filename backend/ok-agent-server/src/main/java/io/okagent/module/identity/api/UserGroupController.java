package io.okagent.module.identity.api;

import io.okagent.module.identity.application.*;
import io.okagent.module.identity.application.UserGroupService;
import io.okagent.shared.api.Response;
import io.okagent.shared.api.PageResponse;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user-groups")
public class UserGroupController {
    private final UserGroupService service;

    public UserGroupController(UserGroupService service) {
        this.service = service;
    }

    /** Returns user groups with their current member counts, newest first, paged. */
    @GetMapping
    public Response<PageResponse<UserGroupResponse>> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return Response.success(PageResponse.of(service.list(page, size)));
    }

    /** Creates a new user group. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response<UserGroupResponse> create(@RequestBody CreateUserGroupRequest request) {
        return Response.success(service.create(request));
    }

    /** Returns one user group by id. */
    @GetMapping("/{id}")
    public Response<UserGroupResponse> get(@PathVariable UUID id) {
        return Response.success(service.get(id));
    }

    /** Updates an existing user group. */
    @PutMapping("/{id}")
    public Response<UserGroupResponse> update(@PathVariable UUID id, @RequestBody UpdateUserGroupRequest request) {
        return Response.success(service.update(id, request));
    }

    /** Deletes a user group that no longer contains any members. */
    @DeleteMapping("/{id}")
    public Response<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return Response.success(null);
    }
}
