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

    /** 查询某个 seq 之后的操作（断线重连 / 增量同步用） */
    @Select("SELECT * FROM op_log WHERE room_id = #{roomId} AND seq > #{seq} ORDER BY seq ASC")
    List<OpLog> listAfterSeq(@Param("roomId") Long roomId, @Param("seq") Long seq);
}
