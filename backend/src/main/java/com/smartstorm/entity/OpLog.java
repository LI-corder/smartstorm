package com.smartstorm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志：每个操作按房间内递增 seq 编号，既是实时同步的顺序保证，
 * 也是第三阶段"操作回放"的数据源。
 */
@Data
@TableName("op_log")
public class OpLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roomId;

    private Long userId;

    /** 房间内单调递增的操作序号 */
    private Long seq;

    /** 操作类型：add_note / edit_note / move_note / delete_note / color_note */
    private String type;

    /** 操作数据 JSON 字符串 */
    private String payload;

    private LocalDateTime createdAt;
}
