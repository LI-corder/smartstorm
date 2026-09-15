package com.smartstorm.dto;

import lombok.Data;

import java.util.List;

/**
 * 智能整理结果视图对象（返回前端 / WebSocket 广播）。
 * 结构 = AnalysisResult + 元信息（id/房间/时间/模型）。
 */
@Data
public class AnalysisVO {

    private Long id;

    private Long roomId;

    /** 参与分析的便利贴数量 */
    private Integer noteCount;

    private String model;

    private String createdAt;

    /** 整板概括 */
    private String summary;

    private List<AnalysisGroup> groups;

    private List<AnalysisConflict> conflicts;
}
