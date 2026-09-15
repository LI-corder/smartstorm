package com.smartstorm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartstorm.dto.NoteRequest;
import com.smartstorm.dto.NoteVO;
import com.smartstorm.entity.Member;
import com.smartstorm.entity.Note;
import com.smartstorm.mapper.MemberMapper;
import com.smartstorm.mapper.NoteMapper;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class NoteService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final NoteMapper noteMapper;
    private final MemberMapper memberMapper;

    public NoteService(NoteMapper noteMapper, MemberMapper memberMapper) {
        this.noteMapper = noteMapper;
        this.memberMapper = memberMapper;
    }

    public List<Note> listByRoom(Long roomId) {
        return noteMapper.selectList(new LambdaQueryWrapper<Note>()
                .eq(Note::getRoomId, roomId)
                .eq(Note::getDeleted, 0)
                .orderByAsc(Note::getZIndex));
    }

    /** 快照转 VO，附带操作者姓名与头像色 */
    public List<NoteVO> toVO(List<Note> notes) {
        if (notes == null || notes.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> userIds = notes.stream().map(Note::getUserId).distinct().toList();
        Map<Long, Member> memberMap = userIds.isEmpty() ? Map.of()
                : memberMapper.selectList(new LambdaQueryWrapper<Member>()
                        .in(Member::getUserId, userIds))
                .stream()
                .collect(Collectors.toMap(Member::getUserId, Function.identity(), (a, b) -> a));
        return notes.stream().map(n -> toVO(n, memberMap.get(n.getUserId()))).toList();
    }

    public NoteVO toVO(Note note, Member member) {
        NoteVO vo = new NoteVO();
        vo.setId(note.getId());
        vo.setRoomId(note.getRoomId());
        vo.setUserId(note.getUserId());
        vo.setUserName(member == null ? "未知" : member.getUserName());
        vo.setUserColor(member == null ? "blue" : member.getColor());
        vo.setX(note.getX());
        vo.setY(note.getY());
        vo.setWidth(note.getWidth());
        vo.setHeight(note.getHeight());
        vo.setColor(note.getColor());
        vo.setContent(note.getContent());
        vo.setZIndex(note.getZIndex());
        vo.setCreatedAt(note.getCreatedAt() == null ? "" : note.getCreatedAt().format(FMT));
        return vo;
    }

    public Note create(NoteRequest req) {
        Note note = new Note();
        note.setRoomId(req.getRoomId());
        note.setUserId(req.getUserId());
        note.setX(req.getX() == null ? 0 : req.getX());
        note.setY(req.getY() == null ? 0 : req.getY());
        note.setWidth(req.getWidth() == null ? 160 : req.getWidth());
        note.setHeight(req.getHeight() == null ? 100 : req.getHeight());
        note.setColor(req.getColor() == null ? "yellow" : req.getColor());
        note.setContent(req.getContent() == null ? "" : req.getContent());
        note.setZIndex(0);
        note.setDeleted(0);
        noteMapper.insert(note);
        return note;
    }

    public Note getById(Long id) {
        return noteMapper.selectById(id);
    }

    public void update(Note note) {
        noteMapper.updateById(note);
    }

    /** 逻辑删除 */
    public void delete(Long id) {
        noteMapper.deleteById(id);
    }
}
