package com.smartstorm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartstorm.entity.OpLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OpLogMapper extends BaseMapper<OpLog> {

    /** 获取房间内当前最大 seq（新操作 seq = max + 1） */
    @Select("SELECT COALESCE(MAX(seq), 0) FROM op_log WHERE room_id = #{roomId}")
    Long getMaxSeq(@Param("roomId") Long roomId);

    /** 查询某个 seq 之后的操作（断线重连 / 增量同步 / 全链校验用） */
    @Select("SELECT * FROM op_log WHERE room_id = #{roomId} AND seq > #{seq} ORDER BY seq ASC")
    List<OpLog> listAfterSeq(@Param("roomId") Long roomId, @Param("seq") Long seq);

    /**
     * 房间内最新一条操作。用于取链头哈希 —— 走 uk_room_seq 反向扫描，O(1)，
     * 不能用 listAfterSeq(roomId, 0) 代替（那会随操作数线性变慢）。
     */
    @Select("SELECT * FROM op_log WHERE room_id = #{roomId} ORDER BY seq DESC LIMIT 1")
    OpLog getLatest(@Param("roomId") Long roomId);

    /** 查询 seq 闭区间 [fromSeq, toSeq] 内的操作，升序（Merkle 建树用） */
    @Select("SELECT * FROM op_log WHERE room_id = #{roomId} AND seq BETWEEN #{fromSeq} AND #{toSeq} ORDER BY seq ASC")
    List<OpLog> listRange(@Param("roomId") Long roomId,
                          @Param("fromSeq") Long fromSeq,
                          @Param("toSeq") Long toSeq);

    /** 按房间 + seq 精确取一条（存量回填时取前驱的哈希用） */
    @Select("SELECT * FROM op_log WHERE room_id = #{roomId} AND seq = #{seq}")
    OpLog getBySeq(@Param("roomId") Long roomId, @Param("seq") Long seq);

    /** 某房间的操作总数 */
    @Select("SELECT COUNT(*) FROM op_log WHERE room_id = #{roomId}")
    int countByRoom(@Param("roomId") Long roomId);

    /** 所有有操作记录的房间 id（定时锚定扫描用） */
    @Select("SELECT DISTINCT room_id FROM op_log")
    List<Long> listRoomIds();

    /** 存在未纳入哈希链（hash 为空）操作的房间 id 列表（存量回填用） */
    @Select("SELECT DISTINCT room_id FROM op_log WHERE hash IS NULL")
    List<Long> listRoomsWithNullHash();

    /**
     * 区间内尚未纳入哈希链的行数。锚定前必须为 0 ——
     * 否则会把一批含空哈希的操作做成 Merkle 树，算出一个永远对不上的根。
     */
    @Select("SELECT COUNT(*) FROM op_log WHERE room_id = #{roomId} "
            + "AND seq BETWEEN #{fromSeq} AND #{toSeq} AND hash IS NULL")
    int countUnhashedInRange(@Param("roomId") Long roomId,
                            @Param("fromSeq") Long fromSeq,
                            @Param("toSeq") Long toSeq);

    /** 某房间内未纳入哈希链的操作，按 seq 升序（存量回填用） */
    @Select("SELECT * FROM op_log WHERE room_id = #{roomId} AND hash IS NULL ORDER BY seq ASC")
    List<OpLog> listNullHashByRoom(@Param("roomId") Long roomId);
}
