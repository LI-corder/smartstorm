package com.smartstorm.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 智能整理的完整结果：整板概括 + 分组 + 冲突。
 * 前端画布覆盖层与数据库 result_json 都使用此结构。
 */
@Data
public class AnalysisResult {

    /** 整面白板的一句话概括（可为空字符串） */
    private String summary;

    private List<AnalysisGroup> groups = new ArrayList<>();

    private List<AnalysisConflict> conflicts = new ArrayList<>();
}
