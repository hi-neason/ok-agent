package io.okagent.web.user;

import io.okagent.service.user.UserService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    /** Returns all users, optionally filtered by group. */
    @GetMapping
    public List<UserResponse> list(@RequestParam(required = false) UUID groupId) {
        return service.list(groupId);
    }

    /** Creates a new user. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@RequestBody CreateUserRequest request) {
        return service.create(request);
    }

    /** Returns one user by id. */
    @GetMapping("/{id}")
    public UserResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    /** Updates an existing user. */
    @PutMapping("/{id}")
    public UserResponse update(@PathVariable UUID id, @RequestBody UpdateUserRequest request) {
        return service.update(id, request);
    }

    /** Deletes a user. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
