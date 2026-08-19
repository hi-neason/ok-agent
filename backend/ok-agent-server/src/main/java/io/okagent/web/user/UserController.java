package io.okagent.web.user;

import io.okagent.repository.channel.ChannelUserIdentityRepository;
import io.okagent.service.user.UserMergeService;
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
    private final UserMergeService mergeService;
    private final ChannelUserIdentityRepository identityRepository;

    public UserController(
            UserService service, UserMergeService mergeService, ChannelUserIdentityRepository identityRepository) {
        this.service = service;
        this.mergeService = mergeService;
        this.identityRepository = identityRepository;
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

    /** Lists the provider identities (Feishu open_id, etc.) bound to a user. */
    @GetMapping("/{id}/channels")
    public List<ChannelIdentityView> channels(@PathVariable UUID id) {
        return identityRepository.findByLinkedUserId(id).stream()
                .map(ChannelIdentityView::from)
                .toList();
    }

    /** Merges another user (secondary) into this user (primary). */
    @PostMapping("/{id}/merge")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void merge(@PathVariable UUID id, @RequestBody MergeRequest body) {
        mergeService.merge(id, body.secondaryId());
    }

    /** Preview of what a merge would reassign. */
    @GetMapping("/{id}/merge-preview")
    public UserMergeService.MergePreview mergePreview(@PathVariable UUID id, @RequestParam UUID secondaryId) {
        return mergeService.preview(id, secondaryId);
    }

    record MergeRequest(UUID secondaryId) {}
}
