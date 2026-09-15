package com.smartstorm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartstorm.entity.Member;
import com.smartstorm.mapper.MemberMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MemberService {

    private final MemberMapper memberMapper;

    public MemberService(MemberMapper memberMapper) {
        this.memberMapper = memberMapper;
    }

    public List<Member> listByRoom(Long roomId) {
        return memberMapper.selectList(new LambdaQueryWrapper<Member>()
                .eq(Member::getRoomId, roomId));
    }
}
