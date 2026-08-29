package io.okagent.module.identity.application;

import io.okagent.module.identity.domain.UserGroup;
import io.okagent.module.identity.infrastructure.persistence.UserGroupRepository;
import io.okagent.module.identity.infrastructure.persistence.UserRepository;
import io.okagent.module.identity.api.CreateUserGroupRequest;
import io.okagent.module.identity.api.UpdateUserGroupRequest;
import io.okagent.module.identity.api.UserGroupResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class UserGroupServiceImpl implements UserGroupService {
    private final UserGroupRepository groupRepository;
    private final UserRepository userRepository;

    public UserGroupServiceImpl(UserGroupRepository groupRepository, UserRepository userRepository) {
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
    }

    @Override
    public Page<UserGroupResponse> list(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        return groupRepository.findAll(pageable)
                .map(group -> UserGroupResponse.from(group, userRepository.countByGroupId(group.getId())));
    }

    @Override
    public UserGroupResponse create(CreateUserGroupRequest request) {
        if (groupRepository.existsByGroupKey(request.groupKey())) {
            throw new UserConflictException("GROUP_KEY_CONFLICT");
        }
        UserGroup group = new UserGroup(
                UUID.randomUUID(), request.groupKey(), request.name(), request.description(), request.enabled());
        return UserGroupResponse.from(groupRepository.save(group), 0L);
    }

    @Override
    public UserGroupResponse get(UUID id) {
        UserGroup group =
                groupRepository.findById(id).orElseThrow(() -> new UserNotFoundException("USER_GROUP_NOT_FOUND"));
        return UserGroupResponse.from(group, userRepository.countByGroupId(id));
    }

    @Override
    public UserGroupResponse update(UUID id, UpdateUserGroupRequest request) {
        UserGroup group =
                groupRepository.findById(id).orElseThrow(() -> new UserNotFoundException("USER_GROUP_NOT_FOUND"));
        if (groupRepository.existsByGroupKeyAndIdNot(request.groupKey(), id)) {
            throw new UserConflictException("GROUP_KEY_CONFLICT");
        }
        group.update(request.groupKey(), request.name(), request.description(), request.enabled());
        return UserGroupResponse.from(groupRepository.save(group), userRepository.countByGroupId(id));
    }

    @Override
    public void delete(UUID id) {
        if (!groupRepository.existsById(id)) {
            throw new UserNotFoundException("USER_GROUP_NOT_FOUND");
        }
        if (userRepository.countByGroupId(id) > 0) {
            throw new UserConflictException("GROUP_NOT_EMPTY");
        }
        groupRepository.deleteById(id);
    }
}
