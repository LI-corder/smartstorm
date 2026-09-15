package com.smartstorm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能整理结果：一次"智能整理"把房间便利贴分组 + 冲突检测的结果存这里，
 * 既用于房间成员实时查看，也是第四阶段"操作回放"中恢复整理视图的数据源。
 */
@Data
@TableName("board_analysis")
public class BoardAnalysis {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roomId;

    /** 发起人用户 id（游客不会被允许触发） */
    private Long userId;

    /** 参与分析的便利贴数量 */
    private Integer noteCount;

    /** 使用的模型 id，如 deepseek-v4-flash */
    private String model;

    /** 分析结果 JSON（AnalysisResult：summary / groups / conflicts） */
    private String resultJson;

    private LocalDateTime createdAt;
}
