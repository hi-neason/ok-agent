package io.okagent.web.user;

import io.okagent.service.user.UserGroupService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user-groups")
public class UserGroupController {
    private final UserGroupService service;

    public UserGroupController(UserGroupService service) {
        this.service = service;
    }

    /** Returns all user groups with their current member counts. */
    @GetMapping
    public List<UserGroupResponse> list() {
        return service.list();
    }

    /** Creates a new user group. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserGroupResponse create(@RequestBody CreateUserGroupRequest request) {
        return service.create(request);
    }

    /** Returns one user group by id. */
    @GetMapping("/{id}")
    public UserGroupResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    /** Updates an existing user group. */
    @PutMapping("/{id}")
    public UserGroupResponse update(@PathVariable UUID id, @RequestBody UpdateUserGroupRequest request) {
        return service.update(id, request);
    }

    /** Deletes a user group that no longer contains any members. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
