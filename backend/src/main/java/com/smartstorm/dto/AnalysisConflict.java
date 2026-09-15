package com.smartstorm.dto;

import lombok.Data;

/**
 * 智能整理发现的一对"内容互相矛盾"的便利贴。
 */
@Data
public class AnalysisConflict {

    /** 冲突便利贴 A 的 id */
    private Long noteA;

    /** 冲突便利贴 B 的 id */
    private Long noteB;

    /** 冲突原因说明 */
    private String reason;
}
