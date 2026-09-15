package com.smartstorm.dto;

import lombok.Data;

import java.util.List;

/**
 * 个人中心聚合视图：用户信息 + 统计 + 我创建的 / 我参与的房间。
 * <p>ownedRooms 与 joinedRooms 语义不相交：
 * 我创建的房间只出现在 ownedRooms，我参与（作为成员加入、非房主）的只出现在 joinedRooms，
 * 使两个列表长度分别与 stats.createdRooms / stats.participatedRooms 一致。</p>
 */
@Data
public class ProfileVO {

    /** 用户信息（含注册时间） */
    private UserItem user;

    /** 我的统计 */
    private Stats stats;

    /** 我创建的房间 */
    private List<RoomItemVO> ownedRooms;

    /** 我参与的房间（不含自己创建的） */
    private List<RoomItemVO> joinedRooms;

    @Data
    public static class UserItem {
        private Long id;
        private String email;
        private String nickname;
        private String avatarColor;
        private String createdAt;
    }

    @Data
    public static class Stats {
        private long createdRooms;
        private long participatedRooms;
        private long notes;
        private long analyses;
    }
}
