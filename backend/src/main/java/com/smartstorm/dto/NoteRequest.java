package com.smartstorm.dto;

import lombok.Data;

@Data
public class NoteRequest {

    private Long roomId;

    private Long userId;

    private Double x;

    private Double y;

    private Double width;

    private Double height;

    private String color;

    private String content;
}
