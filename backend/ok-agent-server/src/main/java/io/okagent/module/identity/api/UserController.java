package io.okagent.module.identity.api;

import io.okagent.module.channel.infrastructure.persistence.ChannelUserIdentityRepository;
import io.okagent.module.identity.application.*;
import io.okagent.module.identity.application.UserMergeService;
import io.okagent.module.identity.application.UserService;
import io.okagent.shared.api.Response;
import io.okagent.shared.api.PageResponse;
import jakarta.validation.Valid;
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

    /** Returns users, optionally filtered by group, newest first, paged. */
    @GetMapping
    public Response<PageResponse<UserResponse>> list(
            @RequestParam(required = false) UUID groupId,
            @RequestParam(defaultValue = "0") @jakarta.validation.constraints.Min(0) int page,
            @RequestParam(defaultValue = "20") @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(100) int size) {
        return Response.success(PageResponse.of(service.list(groupId, page, size)));
    }

    /** Creates a new user. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return Response.success(service.create(request));
    }

    /** Returns one user by id. */
    @GetMapping("/{id}")
    public Response<UserResponse> get(@PathVariable UUID id) {
        return Response.success(service.get(id));
    }

    /** Returns the aggregated detail view (profile + channels + life-cycle counts) for a user. */
    @GetMapping("/{id}/detail")
    public Response<UserDetailResponse> detail(@PathVariable UUID id) {
        return Response.success(service.detail(id));
    }

    /** Updates an existing user. */
    @PutMapping("/{id}")
    public Response<UserResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return Response.success(service.update(id, request));
    }

    /** Deletes a user. */
    @DeleteMapping("/{id}")
    public Response<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return Response.success(null);
    }

    /** Lists the provider identities (Feishu open_id, etc.) bound to a user. */
    @GetMapping("/{id}/channels")
    public Response<List<ChannelIdentityView>> channels(@PathVariable UUID id) {
        return Response.success(identityRepository.findByLinkedUserId(id).stream()
                .map(ChannelIdentityView::from)
                .toList());
    }

    /** Merges another user (secondary) into this user (primary). */
    @PostMapping("/{id}/merge")
    public Response<Void> merge(@PathVariable UUID id, @RequestBody MergeRequest body) {
        mergeService.merge(id, body.secondaryId());
        return Response.success(null);
    }

    /** Preview of what a merge would reassign. */
    @GetMapping("/{id}/merge-preview")
    public Response<UserMergeService.MergePreview> mergePreview(
            @PathVariable UUID id, @RequestParam UUID secondaryId) {
        return Response.success(mergeService.preview(id, secondaryId));
    }

    record MergeRequest(UUID secondaryId) {}
}
