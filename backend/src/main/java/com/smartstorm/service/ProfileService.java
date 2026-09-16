package com.smartstorm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.smartstorm.common.UnauthorizedException;
import com.smartstorm.dto.ChangePasswordRequest;
import com.smartstorm.dto.ProfileVO;
import com.smartstorm.dto.RoomItemVO;
import com.smartstorm.dto.UpdateProfileRequest;
import com.smartstorm.dto.UserVO;
import com.smartstorm.entity.BoardAnalysis;
import com.smartstorm.entity.Member;
import com.smartstorm.entity.Note;
import com.smartstorm.entity.Room;
import com.smartstorm.entity.User;
import com.smartstorm.mapper.BoardAnalysisMapper;
import com.smartstorm.mapper.MemberMapper;
import com.smartstorm.mapper.NoteMapper;
import com.smartstorm.mapper.RoomMapper;
import com.smartstorm.mapper.UserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 个人中心服务：资料聚合 / 资料编辑 / 修改密码。
 */
@Service
public class ProfileService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> AVATAR_COLORS = Set.of("blue", "purple", "green", "orange");

    private final UserMapper userMapper;
    private final RoomMapper roomMapper;
    private final MemberMapper memberMapper;
    private final NoteMapper noteMapper;
    private final BoardAnalysisMapper boardAnalysisMapper;
    private final PasswordEncoder passwordEncoder;
    private final AvatarStorageService avatarStorage;

    public ProfileService(UserMapper userMapper,
                          RoomMapper roomMapper,
                          MemberMapper memberMapper,
                          NoteMapper noteMapper,
                          BoardAnalysisMapper boardAnalysisMapper,
                          PasswordEncoder passwordEncoder,
                          AvatarStorageService avatarStorage) {
        this.userMapper = userMapper;
        this.roomMapper = roomMapper;
        this.memberMapper = memberMapper;
        this.noteMapper = noteMapper;
        this.boardAnalysisMapper = boardAnalysisMapper;
        this.passwordEncoder = passwordEncoder;
        this.avatarStorage = avatarStorage;
    }

    /** 个人中心聚合：用户信息 + 统计 + 我创建的 / 我参与的房间 */
    public ProfileVO overview(Long userId) {
        User user = requireUser(userId);

        List<Room> ownedRooms = roomMapper.selectList(new LambdaQueryWrapper<Room>()
                .eq(Room::getOwnerId, userId)
                .orderByDesc(Room::getCreatedAt));
        List<Room> joinedRooms = joinedRoomsOf(userId);

        ProfileVO vo = new ProfileVO();
        vo.setUser(toUserItem(user));

        ProfileVO.Stats stats = new ProfileVO.Stats();
        stats.setCreatedRooms(ownedRooms.size());
        stats.setParticipatedRooms(joinedRooms.size());
        stats.setNotes(noteMapper.selectCount(new LambdaQueryWrapper<Note>()
                .eq(Note::getUserId, userId)));
        stats.setAnalyses(boardAnalysisMapper.selectCount(new LambdaQueryWrapper<BoardAnalysis>()
                .eq(BoardAnalysis::getUserId, userId)));
        vo.setStats(stats);

        vo.setOwnedRooms(ownedRooms.stream().map(r -> toItem(r)).toList());
        vo.setJoinedRooms(joinedRooms.stream().map(r -> toItem(r)).toList());
        return vo;
    }

    /** 资料编辑：昵称 + 头像颜色；同步该用户在所有房间 member 快照的名字/颜色 */
    @Transactional
    public UserVO updateProfile(Long userId, UpdateProfileRequest req) {
        String nickname = req.getNickname() == null ? "" : req.getNickname().trim();
        String color = req.getAvatarColor() == null ? "" : req.getAvatarColor().trim();
        if (nickname.isBlank()) {
            throw new IllegalArgumentException("昵称不能为空");
        }
        if (!AVATAR_COLORS.contains(color)) {
            throw new IllegalArgumentException("头像颜色不合法");
        }

        User user = requireUser(userId);
        user.setNickname(nickname);
        user.setAvatarColor(color);
        userMapper.updateById(user);

        // 房间画布的作者名/色取自 member 快照且加入房间后不再刷新，需在此同步
        memberMapper.update(null, new LambdaUpdateWrapper<Member>()
                .eq(Member::getUserId, userId)
                .set(Member::getUserName, nickname)
                .set(Member::getColor, color));
        return UserVO.from(user);
    }

    /**
     * 上传自定义头像：存文件 → 写库 → 删除旧文件。
     *
     * <p>顺序是<b>先存新的、再删旧的</b>：万一写库失败，用户至少还持有旧头像（新文件成为
     * 孤儿，只占一点磁盘）。反过来先删的话，写库一旦失败，用户就新旧头像都没有了。</p>
     *
     * @throws IllegalArgumentException 文件校验不通过（空、超 2MB、非白名单格式、尺寸过大）
     */
    @Transactional
    public UserVO uploadAvatar(Long userId, MultipartFile file) {
        User user = requireUser(userId);
        String oldUrl = user.getAvatarUrl();

        String newUrl = avatarStorage.store(file);
        user.setAvatarUrl(newUrl);
        userMapper.updateById(user);

        // 库里已指向新文件，旧文件可以安全删掉了
        avatarStorage.delete(oldUrl);
        return UserVO.from(user);
    }

    /** 移除自定义头像，回退到颜色块 */
    @Transactional
    public UserVO removeAvatar(Long userId) {
        User user = requireUser(userId);
        String oldUrl = user.getAvatarUrl();

        // 注意：不能用 updateById —— MyBatis-Plus 默认更新策略会跳过 null 字段，
        // 那样 avatar_url 根本不会被置空。必须用 UpdateWrapper 显式 set null。
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getAvatarUrl, null));
        user.setAvatarUrl(null);

        avatarStorage.delete(oldUrl);
        return UserVO.from(user);
    }

    /** 登录状态下改密：校验原密码（失败返回 400，勿抛 401，避免前端被登出） */
    public void changePassword(Long userId, ChangePasswordRequest req) {
        User user = requireUser(userId);
        if (!passwordEncoder.matches(req.getOldPassword(), user.getPassword())) {
            throw new IllegalArgumentException("原密码错误");
        }
        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userMapper.updateById(user);
    }

    // ---------------- 内部工具 ----------------

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new UnauthorizedException("用户不存在");
        }
        return user;
    }

    /** 我参与的房间：有 member 行且非我创建（与 owned 不相交） */
    private List<Room> joinedRoomsOf(Long userId) {
        List<Member> members = memberMapper.selectList(new LambdaQueryWrapper<Member>()
                .eq(Member::getUserId, userId));
        if (members.isEmpty()) {
            return List.of();
        }
        Set<Long> roomIds = members.stream().map(Member::getRoomId).collect(Collectors.toSet());
        return roomMapper.selectList(new LambdaQueryWrapper<Room>()
                        .in(Room::getId, roomIds)
                        .orderByDesc(Room::getCreatedAt))
                .stream()
                .filter(r -> !userId.equals(r.getOwnerId()))
                .toList();
    }

    private ProfileVO.UserItem toUserItem(User user) {
        ProfileVO.UserItem item = new ProfileVO.UserItem();
        item.setId(user.getId());
        item.setEmail(user.getEmail());
        item.setNickname(user.getNickname());
        item.setAvatarColor(user.getAvatarColor());
        item.setAvatarUrl(user.getAvatarUrl());
        item.setCreatedAt(user.getCreatedAt() == null ? "" : user.getCreatedAt().format(FMT));
        return item;
    }

    private RoomItemVO toItem(Room room) {
        long memberCount = memberMapper.selectCount(new LambdaQueryWrapper<Member>()
                .eq(Member::getRoomId, room.getId()));
        long noteCount = noteMapper.selectCount(new LambdaQueryWrapper<Note>()
                .eq(Note::getRoomId, room.getId()));
        return RoomItemVO.from(room, memberCount, noteCount);
    }
}
