package com.smartstorm.dto;

import lombok.Data;

import java.util.List;

/**
 * 智能整理返回的"一组便利贴"。
 */
@Data
public class AnalysisGroup {

    /** 组主题名（DeepSeek 生成） */
    private String name;

    /** 组内便利贴 id（必须是房间内真实存在的便利贴 id） */
    private List<Long> noteIds;

    /** 该组的一句话总结（可为空） */
    private String summary;
}
