package io.okagent.service.user;

import io.okagent.domain.user.User;
import io.okagent.domain.user.UserGroup;
import io.okagent.repository.channel.ChannelUserIdentityRepository;
import io.okagent.repository.user.UserGroupRepository;
import io.okagent.repository.user.UserRepository;
import io.okagent.web.user.ChannelIdentityView;
import io.okagent.web.user.CreateUserRequest;
import io.okagent.web.user.UpdateUserRequest;
import io.okagent.web.user.UserDetailResponse;
import io.okagent.web.user.UserResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {
    private static final String DEBUG_USERNAME = "debug";

    private final UserRepository userRepository;
    private final UserGroupRepository groupRepository;
    private final ChannelUserIdentityRepository identityRepository;
    private final JdbcTemplate jdbc;

    public UserServiceImpl(
            UserRepository userRepository,
            UserGroupRepository groupRepository,
            ChannelUserIdentityRepository identityRepository,
            JdbcTemplate jdbc) {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.identityRepository = identityRepository;
        this.jdbc = jdbc;
    }

    @Override
    public List<UserResponse> list(UUID groupIdFilter) {
        List<User> users =
                groupIdFilter != null ? userRepository.findByGroupId(groupIdFilter) : userRepository.findAll();
        Map<UUID, String> groupNames =
                groupRepository.findAll().stream().collect(Collectors.toMap(UserGroup::getId, UserGroup::getName));
        return users.stream()
                .map(user -> UserResponse.from(user, groupName(groupNames, user.getGroupId()), (int)
                        identityRepository.countByLinkedUserId(user.getId())))
                .toList();
    }

    @Override
    public UserResponse create(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new UserConflictException("USERNAME_CONFLICT");
        }
        UUID groupId = request.groupId();
        if (groupId != null && !groupRepository.existsById(groupId)) {
            throw new UserConflictException("GROUP_NOT_FOUND");
        }
        String userId = UUID.randomUUID().toString();
        User user = new User(
                UUID.randomUUID(),
                userId,
                request.username(),
                request.displayName(),
                request.email(),
                request.phone(),
                groupId,
                request.enabled());
        return UserResponse.from(userRepository.save(user), resolveGroupName(groupId));
    }

    @Override
    public UserResponse get(UUID id) {
        User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException("USER_NOT_FOUND"));
        return UserResponse.from(user, resolveGroupName(user.getGroupId()));
    }

    @Override
    public UserDetailResponse detail(UUID id) {
        User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException("USER_NOT_FOUND"));
        String groupName = resolveGroupName(user.getGroupId());
        int channelCount = (int) identityRepository.countByLinkedUserId(id);
        UserResponse profile = UserResponse.from(user, groupName, channelCount);
        List<ChannelIdentityView> channels = identityRepository.findByLinkedUserId(id).stream()
                .map(ChannelIdentityView::from)
                .toList();
        String uid = user.getUserId();
        long sessionCount = countByUserId("dialogue_session", uid);
        long personaCount = countByUserId("user_persona", uid);
        long traceCount = countByUserId("trace_span", uid);
        long messageCount =
                channels.stream().mapToLong(ChannelIdentityView::messageCount).sum();
        return new UserDetailResponse(profile, channels, sessionCount, personaCount, traceCount, messageCount);
    }

    private long countByUserId(String table, String userId) {
        Long c = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE user_id = ?", Long.class, userId);
        return c == null ? 0 : c;
    }

    @Override
    public UserResponse update(UUID id, UpdateUserRequest request) {
        User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException("USER_NOT_FOUND"));
        if (userRepository.existsByUsernameAndIdNot(request.username(), id)) {
            throw new UserConflictException("USERNAME_CONFLICT");
        }
        UUID groupId = request.groupId();
        if (groupId != null && !groupRepository.existsById(groupId)) {
            throw new UserConflictException("GROUP_NOT_FOUND");
        }
        user.update(
                request.username(),
                request.displayName(),
                request.email(),
                request.phone(),
                groupId,
                request.enabled());
        return UserResponse.from(userRepository.save(user), resolveGroupName(groupId));
    }

    @Override
    public void delete(UUID id) {
        User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException("USER_NOT_FOUND"));
        // The built-in DEBUG user backs the agent debug preview; deleting it would break the
        // "debug without a real user" path, so it is protected.
        if (DEBUG_USERNAME.equals(user.getUsername())) {
            throw new UserConflictException("DEBUG_USER_PROTECTED");
        }
        userRepository.deleteById(id);
    }

    private String resolveGroupName(UUID groupId) {
        if (groupId == null) return null;
        return groupRepository.findById(groupId).map(UserGroup::getName).orElse(null);
    }

    private static String groupName(Map<UUID, String> groupNames, UUID groupId) {
        return groupId == null ? null : groupNames.get(groupId);
    }
}
