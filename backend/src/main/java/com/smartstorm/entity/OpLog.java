package com.smartstorm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志：每个操作按房间内递增 seq 编号，既是实时同步的顺序保证，
 * 也是第三阶段"操作回放"的数据源。
 *
 * <p>存证：每条操作带 {@code prevHash} / {@code hash} 两列，按房间串成哈希链。
 * 链只能检测"事后偷改某一条"，防不住有库权限的人整条重算 —— 那要靠
 * {@code chain_anchor} 把链头锚定到链上。两者是一套，缺一不可。</p>
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

    /** 前一条操作的哈希（房间内链式，首条为 64 个 0） */
    private String prevHash;

    /** 本条操作哈希 SHA-256（hex） */
    private String hash;

    private LocalDateTime createdAt;
}
