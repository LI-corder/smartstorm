package com.smartstorm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartstorm.entity.Member;
import com.smartstorm.entity.Room;
import com.smartstorm.mapper.MemberMapper;
import com.smartstorm.mapper.RoomMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

@Service
public class RoomService {

    private final RoomMapper roomMapper;
    private final MemberMapper memberMapper;

    public RoomService(RoomMapper roomMapper, MemberMapper memberMapper) {
        this.roomMapper = roomMapper;
        this.memberMapper = memberMapper;
    }

    /** 创建房间，生成唯一 6 位房间号（ownerId 为创建者） */
    public Room createRoom(String name, Long ownerId) {
        Room room = new Room();
        room.setName(name == null || name.isBlank() ? "头脑风暴" : name);
        room.setRoomCode(generateRoomCode());
        room.setOwnerId(ownerId);
        roomMapper.insert(room);
        return room;
    }

    public Room getByCode(String roomCode) {
        return roomMapper.selectOne(new LambdaQueryWrapper<Room>()
                .eq(Room::getRoomCode, roomCode));
    }

    public Room getById(Long roomId) {
        return roomMapper.selectById(roomId);
    }

    public boolean exists(Long roomId) {
        return roomMapper.selectById(roomId) != null;
    }

    /** 加入房间（幂等：已加入则复用成员记录） */
    public Member joinRoom(Long roomId, Long userId, String userName) {
        Member exist = memberMapper.selectOne(new LambdaQueryWrapper<Member>()
                .eq(Member::getRoomId, roomId)
                .eq(Member::getUserId, userId));
        if (exist != null) {
            return exist;
        }
        Member member = new Member();
        member.setRoomId(roomId);
        member.setUserId(userId);
        member.setUserName(userName == null || userName.isBlank() ? "访客" : userName);
        member.setColor(pickColor());
        memberMapper.insert(member);
        return member;
    }

    public List<Member> listMembers(Long roomId) {
        return memberMapper.selectList(new LambdaQueryWrapper<Member>()
                .eq(Member::getRoomId, roomId));
    }

    private static final String[] COLORS = {"blue", "purple", "green", "orange"};

    private String pickColor() {
        return COLORS[new Random().nextInt(COLORS.length)];
    }

    private String generateRoomCode() {
        // 6 位数字+大写字母（去除易混淆的 0/O/1/I）
        final String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        Random r = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(r.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
