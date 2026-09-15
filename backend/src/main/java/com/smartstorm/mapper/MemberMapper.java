package com.smartstorm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartstorm.entity.Member;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MemberMapper extends BaseMapper<Member> {
}
