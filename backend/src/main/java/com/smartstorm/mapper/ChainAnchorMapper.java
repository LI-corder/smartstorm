package com.smartstorm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartstorm.entity.ChainAnchor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChainAnchorMapper extends BaseMapper<ChainAnchor> {

    /**
     * 房间内已占用到的最大 seq（不论状态）。
     *
     * <p>用「已占用的上界」而不是「已确认的上界」来划下一批的起点 —— 否则一个失败的批次
     * 会被反复重建，而 {@code uk_room_range} 会一直挡着它。失败的批次交给重试逻辑补发。</p>
     */
    @Select("SELECT COALESCE(MAX(to_seq), 0) FROM chain_anchor WHERE room_id = #{roomId}")
    Long getMaxAnchoredSeq(@Param("roomId") Long roomId);

    /** 房间内的锚点列表，最近的在前 */
    @Select("SELECT * FROM chain_anchor WHERE room_id = #{roomId} ORDER BY from_seq DESC")
    List<ChainAnchor> listByRoom(@Param("roomId") Long roomId);

    /** 已上链确认的锚点数量 */
    @Select("SELECT COUNT(*) FROM chain_anchor WHERE room_id = #{roomId} AND status = 1")
    int countConfirmed(@Param("roomId") Long roomId);

    /** 待补发的批次（未上链或失败且未超重试上限），跨房间按房间与区间排序 */
    @Select("SELECT * FROM chain_anchor WHERE status IN (0, 2) AND retry_count < #{maxRetry} "
            + "ORDER BY room_id ASC, from_seq ASC")
    List<ChainAnchor> listRetryable(@Param("maxRetry") int maxRetry);

    /** 包含指定 seq 的那个批次（可能尚未上链） */
    @Select("SELECT * FROM chain_anchor WHERE room_id = #{roomId} "
            + "AND from_seq <= #{seq} AND to_seq >= #{seq} ORDER BY from_seq DESC LIMIT 1")
    ChainAnchor findBySeq(@Param("roomId") Long roomId, @Param("seq") Long seq);
}
