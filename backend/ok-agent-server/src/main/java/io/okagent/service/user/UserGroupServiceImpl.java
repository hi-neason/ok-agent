package io.okagent.service.user;

import io.okagent.domain.user.UserGroup;
import io.okagent.repository.user.UserGroupRepository;
import io.okagent.repository.user.UserRepository;
import io.okagent.web.user.CreateUserGroupRequest;
import io.okagent.web.user.UpdateUserGroupRequest;
import io.okagent.web.user.UserGroupResponse;
import java.util.List;
import java.util.UUID;
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
    public List<UserGroupResponse> list() {
        return groupRepository.findAll().stream()
                .map(group -> UserGroupResponse.from(group, userRepository.countByGroupId(group.getId())))
                .toList();
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
