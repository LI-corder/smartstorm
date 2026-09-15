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

    public ProfileService(UserMapper userMapper,
                          RoomMapper roomMapper,
                          MemberMapper memberMapper,
                          NoteMapper noteMapper,
                          BoardAnalysisMapper boardAnalysisMapper,
                          PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.roomMapper = roomMapper;
        this.memberMapper = memberMapper;
        this.noteMapper = noteMapper;
        this.boardAnalysisMapper = boardAnalysisMapper;
        this.passwordEncoder = passwordEncoder;
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
