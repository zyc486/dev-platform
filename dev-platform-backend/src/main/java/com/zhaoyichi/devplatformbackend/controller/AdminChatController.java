package com.zhaoyichi.devplatformbackend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.zhaoyichi.devplatformbackend.common.Result;
import com.zhaoyichi.devplatformbackend.entity.ChatRoom;
import com.zhaoyichi.devplatformbackend.entity.ChatRoomMember;
import com.zhaoyichi.devplatformbackend.entity.User;
import com.zhaoyichi.devplatformbackend.mapper.ChatRoomMapper;
import com.zhaoyichi.devplatformbackend.mapper.ChatRoomMemberMapper;
import com.zhaoyichi.devplatformbackend.mapper.UserMapper;
import com.zhaoyichi.devplatformbackend.service.AdminLogService;
import com.zhaoyichi.devplatformbackend.service.ChatService;
import com.zhaoyichi.devplatformbackend.service.MessageNoticeService;
import com.zhaoyichi.devplatformbackend.utils.AuthHelper;
import lombok.Data;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/chat")
@CrossOrigin
public class AdminChatController {
    private final JdbcTemplate jdbcTemplate;
    private final ChatRoomMapper chatRoomMapper;
    private final ChatRoomMemberMapper memberMapper;
    private final UserMapper userMapper;
    private final ChatService chatService;
    private final MessageNoticeService messageNoticeService;
    private final AdminLogService adminLogService;

    public AdminChatController(JdbcTemplate jdbcTemplate,
                               ChatRoomMapper chatRoomMapper,
                               ChatRoomMemberMapper memberMapper,
                               UserMapper userMapper,
                               ChatService chatService,
                               MessageNoticeService messageNoticeService,
                               AdminLogService adminLogService) {
        this.jdbcTemplate = jdbcTemplate;
        this.chatRoomMapper = chatRoomMapper;
        this.memberMapper = memberMapper;
        this.userMapper = userMapper;
        this.chatService = chatService;
        this.messageNoticeService = messageNoticeService;
        this.adminLogService = adminLogService;
    }

    @GetMapping("/rooms")
    public Result<Map<String, Object>> listRooms(AdminChatRoomQuery q, HttpServletRequest request) {
        Result<Map<String, Object>> auth = AuthHelper.requireAdmin(request);
        if (auth != null) return auth;

        int page = q == null || q.getPage() == null ? 1 : Math.max(1, q.getPage());
        int size = q == null || q.getSize() == null ? 20 : Math.max(1, Math.min(200, q.getSize()));
        int offset = (page - 1) * size;

        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();

        if (q != null && q.getRoomId() != null) {
            where.append(" AND r.id = ? ");
            args.add(q.getRoomId());
        }
        if (q != null && q.getChatNo() != null && !q.getChatNo().trim().isEmpty()) {
            where.append(" AND r.chat_no = ? ");
            args.add(q.getChatNo().trim());
        }
        if (q != null && q.getNameLike() != null && !q.getNameLike().trim().isEmpty()) {
            where.append(" AND r.name LIKE ? ");
            args.add("%" + q.getNameLike().trim() + "%");
        }
        if (q != null && q.getCreatedFrom() != null && !q.getCreatedFrom().trim().isEmpty()) {
            LocalDate d = LocalDate.parse(q.getCreatedFrom().trim());
            where.append(" AND r.create_time >= ? ");
            args.add(LocalDateTime.of(d, LocalTime.MIN));
        }
        if (q != null && q.getCreatedTo() != null && !q.getCreatedTo().trim().isEmpty()) {
            LocalDate d = LocalDate.parse(q.getCreatedTo().trim());
            where.append(" AND r.create_time <= ? ");
            args.add(LocalDateTime.of(d, LocalTime.MAX));
        }
        if (q != null && q.getUserId() != null) {
            String scope = q.getUserScope() == null ? "" : q.getUserScope().trim().toLowerCase();
            if ("owner".equals(scope)) {
                where.append(" AND r.created_by = ? ");
                args.add(q.getUserId());
            } else {
                where.append(" AND EXISTS (SELECT 1 FROM chat_room_member m WHERE m.room_id = r.id AND m.user_id = ?) ");
                args.add(q.getUserId());
            }
        }
        if (q != null && (q.getMemberCountMin() != null || q.getMemberCountMax() != null)) {
            long min = q.getMemberCountMin() == null ? 0 : Math.max(0, q.getMemberCountMin());
            long max = q.getMemberCountMax() == null ? Long.MAX_VALUE : Math.max(0, q.getMemberCountMax());
            where.append(" AND (SELECT COUNT(1) FROM chat_room_member m2 WHERE m2.room_id = r.id) BETWEEN ? AND ? ");
            args.add(min);
            args.add(max);
        }

        String baseSql = " FROM chat_room r " + where;
        long total = jdbcTemplate.queryForObject("SELECT COUNT(1) " + baseSql, args.toArray(), Long.class);

        String dataSql = "SELECT r.id AS roomId, r.chat_no AS chatNo, r.name AS name, r.created_by AS createdBy, r.create_time AS createTime," +
                " (SELECT COUNT(1) FROM chat_room_member m3 WHERE m3.room_id = r.id) AS memberCount " +
                baseSql + " ORDER BY r.id DESC LIMIT ? OFFSET ? ";
        List<Object> args2 = new ArrayList<>(args);
        args2.add(size);
        args2.add(offset);
        List<Map<String, Object>> list = jdbcTemplate.queryForList(dataSql, args2.toArray());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("list", list == null ? Collections.emptyList() : list);
        out.put("total", total);
        out.put("page", page);
        out.put("size", size);
        return Result.success(out);
    }

    @GetMapping("/room/{roomId}")
    public Result<Map<String, Object>> roomDetail(@PathVariable Long roomId, HttpServletRequest request) {
        Result<Map<String, Object>> auth = AuthHelper.requireAdmin(request);
        if (auth != null) return auth;
        ChatRoom room = chatRoomMapper.selectById(roomId);
        if (room == null) return Result.error("群聊不存在");

        List<ChatRoomMember> mems = memberMapper.selectList(new QueryWrapper<ChatRoomMember>()
                .eq("room_id", roomId)
                .orderByAsc("id")
                .last("LIMIT 2000"));
        List<Long> uids = mems == null ? Collections.emptyList() : mems.stream().map(ChatRoomMember::getUserId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, User> userById = Collections.emptyMap();
        if (!uids.isEmpty()) {
            List<User> us = userMapper.selectList(new QueryWrapper<User>().in("id", uids));
            userById = us == null ? Collections.emptyMap() : us.stream().filter(Objects::nonNull)
                    .collect(Collectors.toMap(User::getId, it -> it, (a, b) -> a));
        }
        List<Map<String, Object>> members = new ArrayList<>();
        if (mems != null) {
            for (ChatRoomMember m : mems) {
                if (m == null) continue;
                User u = userById.get(m.getUserId());
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("userId", m.getUserId());
                row.put("username", u == null ? null : u.getUsername());
                row.put("nickname", u == null ? null : u.getNickname());
                row.put("avatar", u == null ? null : u.getAvatar());
                row.put("role", m.getRole());
                row.put("joinTime", m.getJoinTime());
                members.add(row);
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("roomId", room.getId());
        out.put("chatNo", room.getChatNo());
        out.put("name", room.getName());
        out.put("createdBy", room.getCreatedBy());
        out.put("createTime", room.getCreateTime());
        out.put("members", members);
        return Result.success(out);
    }

    @PostMapping("/room/{roomId}/close")
    public Result<String> forceClose(@PathVariable Long roomId, HttpServletRequest request) {
        Result<String> auth = AuthHelper.requireAdmin(request);
        if (auth != null) return auth;
        Long adminId = AuthHelper.currentUserId(request);
        String adminName = AuthHelper.currentUsername(request);
        try {
            chatService.adminCloseRoomHardDelete(roomId, adminId);
            adminLogService.log(adminId, adminName, "chat_force_close", "chat_room", roomId, "强制关闭群聊");
            return Result.successMsg("已强制关闭");
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.systemError("操作失败");
        }
    }

    @PostMapping("/room/{roomId}/members/{userId}/kick")
    public Result<String> kick(@PathVariable Long roomId, @PathVariable Long userId, HttpServletRequest request) {
        Result<String> auth = AuthHelper.requireAdmin(request);
        if (auth != null) return auth;
        Long adminId = AuthHelper.currentUserId(request);
        String adminName = AuthHelper.currentUsername(request);
        try {
            chatService.adminKickMember(roomId, userId);
            adminLogService.log(adminId, adminName, "chat_kick_member", "chat_room_member", userId, "roomId=" + roomId);
            return Result.successMsg("已踢出");
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.systemError("操作失败");
        }
    }

    @PostMapping("/room/{roomId}/members/{userId}/role")
    public Result<String> setRole(@PathVariable Long roomId, @PathVariable Long userId, @RequestBody SetRoleBody body, HttpServletRequest request) {
        Result<String> auth = AuthHelper.requireAdmin(request);
        if (auth != null) return auth;
        Long adminId = AuthHelper.currentUserId(request);
        String adminName = AuthHelper.currentUsername(request);
        try {
            String role = body == null ? null : body.getRole();
            // admin can only set admin/member (not owner)
            UpdateWrapper<ChatRoomMember> uw = new UpdateWrapper<>();
            uw.eq("room_id", roomId).eq("user_id", userId);
            ChatRoomMember exist = memberMapper.selectOne(new QueryWrapper<ChatRoomMember>()
                    .eq("room_id", roomId).eq("user_id", userId).last("LIMIT 1"));
            if (exist == null) return Result.error("目标用户不在群内");
            if ("owner".equalsIgnoreCase(String.valueOf(exist.getRole()))) return Result.error("不可修改群主角色");
            String r = role == null ? "" : role.trim().toLowerCase();
            if (!"admin".equals(r) && !"member".equals(r)) return Result.error("role 仅支持 admin/member");
            uw.set("role", r);
            memberMapper.update(null, uw);
            adminLogService.log(adminId, adminName, "chat_set_role", "chat_room_member", userId, "roomId=" + roomId + ", role=" + r);
            return Result.successMsg("已更新");
        } catch (Exception e) {
            return Result.systemError("操作失败");
        }
    }

    @Data
    public static class AdminChatRoomQuery {
        private Long userId;
        private String userScope; // member|owner
        private String createdFrom; // YYYY-MM-DD
        private String createdTo;   // YYYY-MM-DD
        private Long memberCountMin;
        private Long memberCountMax;
        private String chatNo;
        private Long roomId;
        private String nameLike;
        private Integer page;
        private Integer size;
    }

    @Data
    public static class SetRoleBody {
        private String role;
    }
}

