package com.smartstorm.dto;

import com.smartstorm.entity.Room;
import lombok.Data;

import java.time.format.DateTimeFormatter;

/**
 * 个人中心里一条房间记录（附成员数 / 便利贴数，创建时间已格式化）。
 */
@Data
public class RoomItemVO {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Long id;
    private String roomCode;
    private String name;
    private Long ownerId;
    private Long memberCount;
    private Long noteCount;
    private String createdAt;

    public static RoomItemVO from(Room room, long memberCount, long noteCount) {
        RoomItemVO vo = new RoomItemVO();
        vo.setId(room.getId());
        vo.setRoomCode(room.getRoomCode());
        vo.setName(room.getName());
        vo.setOwnerId(room.getOwnerId());
        vo.setMemberCount(memberCount);
        vo.setNoteCount(noteCount);
        vo.setCreatedAt(room.getCreatedAt() == null ? "" : room.getCreatedAt().format(FMT));
        return vo;
    }
}
