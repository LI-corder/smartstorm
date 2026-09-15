package com.smartstorm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartstorm.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
