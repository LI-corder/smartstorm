package com.smartstorm.dto;

import lombok.Data;

/**
 * 便利贴视图对象（返回前端，附带操作者用户名与头像色）。
 */
@Data
public class NoteVO {

    private Long id;
    private Long roomId;
    private Long userId;
    private String userName;
    private String userColor;
    private Double x;
    private Double y;
    private Double width;
    private Double height;
    private String color;
    private String content;
    private Integer zIndex;
    private String createdAt;
}
