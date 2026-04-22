package com.zhaoyichi.devplatformbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.zhaoyichi.devplatformbackend.entity.ChatMessage;
import com.zhaoyichi.devplatformbackend.entity.ChatInvite;
import com.zhaoyichi.devplatformbackend.entity.ChatJoinRequest;
import com.zhaoyichi.devplatformbackend.entity.ChatRoom;
import com.zhaoyichi.devplatformbackend.entity.ChatRoomMember;
import com.zhaoyichi.devplatformbackend.entity.User;
import com.zhaoyichi.devplatformbackend.mapper.ChatInviteMapper;
import com.zhaoyichi.devplatformbackend.mapper.ChatJoinRequestMapper;
import com.zhaoyichi.devplatformbackend.mapper.ChatMessageMapper;
import com.zhaoyichi.devplatformbackend.mapper.ChatRoomMapper;
import com.zhaoyichi.devplatformbackend.mapper.ChatRoomMemberMapper;
import com.zhaoyichi.devplatformbackend.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ChatService {
    private final ChatRoomMapper chatRoomMapper;
    private final ChatRoomMemberMapper memberMapper;
    private final ChatMessageMapper messageMapper;
    private final ChatJoinRequestMapper joinRequestMapper;
    private final ChatInviteMapper inviteMapper;
    private final UserMapper userMapper;
    private final MessageNoticeService messageNoticeService;

    public ChatService(ChatRoomMapper chatRoomMapper,
                       ChatRoomMemberMapper memberMapper,
                       ChatMessageMapper messageMapper,
                       ChatJoinRequestMapper joinRequestMapper,
                       ChatInviteMapper inviteMapper,
                       UserMapper userMapper,
                       MessageNoticeService messageNoticeService) {
        this.chatRoomMapper = chatRoomMapper;
        this.memberMapper = memberMapper;
        this.messageMapper = messageMapper;
        this.joinRequestMapper = joinRequestMapper;
        this.inviteMapper = inviteMapper;
        this.userMapper = userMapper;
        this.messageNoticeService = messageNoticeService;
    }

    public ChatRoom createRoom(Long creatorUserId, String name, Integer collabProjectId, List<Long> initialMembers) {
        Objects.requireNonNull(creatorUserId, "creatorUserId");
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) {
            n = "群聊";
        }
        if (n.length() > 100) {
            n = n.substring(0, 100);
        }

        ChatRoom room = new ChatRoom();
        room.setName(n);
        room.setCreatedBy(creatorUserId);
        room.setCollabProjectId(collabProjectId);
        chatRoomMapper.insert(room);

        // chatNo: derive from room id to ensure uniqueness and pure digits
        if (room.getId() != null) {
            String chatNo = generateChatNo(room.getId());
            room.setChatNo(chatNo);
            chatRoomMapper.update(room, new UpdateWrapper<ChatRoom>()
                    .eq("id", room.getId())
                    .set("chat_no", chatNo));
        }

        // creator is owner
        ensureMember(room.getId(), creatorUserId, "owner");
        if (initialMembers != null) {
            for (Long uid : initialMembers) {
                if (uid == null) continue;
                if (creatorUserId.equals(uid)) continue;
                ensureMember(room.getId(), uid, "member");
            }
        }
        return room;
    }

    private String generateChatNo(Long roomId) {
        // 6+ digits, monotonically increasing, stable across restarts
        long base = 100000L;
        long v = base + Math.max(0L, roomId == null ? 0L : roomId);
        return String.valueOf(v);
    }

    public ChatRoom createRoomIfAbsentForCollab(Integer collabProjectId, Long creatorUserId, String nameIfCreate) {
        if (collabProjectId == null) return null;
        ChatRoom exist = chatRoomMapper.selectOne(new QueryWrapper<ChatRoom>()
                .eq("collab_project_id", collabProjectId)
                .orderByDesc("id")
                .last("LIMIT 1"));
        if (exist != null) return exist;
        return createRoom(creatorUserId, nameIfCreate, collabProjectId, null);
    }

    public void ensureMember(Long roomId, Long userId, String role) {
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(userId, "userId");
        ChatRoomMember exist = memberMapper.selectOne(new QueryWrapper<ChatRoomMember>()
                .eq("room_id", roomId)
                .eq("user_id", userId)
                .last("LIMIT 1"));
        if (exist != null) return;
        ChatRoomMember m = new ChatRoomMember();
        m.setRoomId(roomId);
        m.setUserId(userId);
        m.setRole(role == null || role.trim().isEmpty() ? "member" : role.trim());
        memberMapper.insert(m);
    }

    public boolean isMember(Long roomId, Long userId) {
        if (roomId == null || userId == null) return false;
        return memberMapper.selectCount(new QueryWrapper<ChatRoomMember>()
                .eq("room_id", roomId)
                .eq("user_id", userId)) > 0;
    }

    public List<Map<String, Object>> listRooms(Long userId, int limit) {
        Objects.requireNonNull(userId, "userId");
        int safeLimit = Math.max(1, Math.min(limit <= 0 ? 50 : limit, 200));
        List<ChatRoomMember> memberships = memberMapper.selectList(new QueryWrapper<ChatRoomMember>()
                .eq("user_id", userId)
                .orderByDesc("id")
                .last("LIMIT " + safeLimit));
        if (memberships == null || memberships.isEmpty()) return Collections.emptyList();

        List<Long> roomIds = memberships.stream().map(ChatRoomMember::getRoomId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (roomIds.isEmpty()) return Collections.emptyList();

        List<ChatRoom> rooms = chatRoomMapper.selectList(new QueryWrapper<ChatRoom>().in("id", roomIds));
        Map<Long, ChatRoom> roomById = rooms == null ? Collections.emptyMap() :
                rooms.stream().filter(Objects::nonNull).collect(Collectors.toMap(ChatRoom::getId, it -> it, (a, b) -> a));

        // last message per room (simple N+1 optimized by scanning latest messages)
        List<ChatMessage> latestMsgs = messageMapper.selectList(new QueryWrapper<ChatMessage>()
                .in("room_id", roomIds)
                .orderByDesc("id")
                .last("LIMIT " + Math.min(roomIds.size() * 5, 500)));
        Map<Long, ChatMessage> lastByRoom = new LinkedHashMap<>();
        if (latestMsgs != null) {
            for (ChatMessage m : latestMsgs) {
                if (m == null || m.getRoomId() == null) continue;
                if (!lastByRoom.containsKey(m.getRoomId())) lastByRoom.put(m.getRoomId(), m);
            }
        }

        // preload users for last message author
        Set<Long> userIds = new LinkedHashSet<>();
        if (latestMsgs != null) {
            for (ChatMessage m : latestMsgs) {
                if (m != null && m.getFromUserId() != null) userIds.add(m.getFromUserId());
            }
        }
        Map<Long, User> userById = Collections.emptyMap();
        if (!userIds.isEmpty()) {
            List<User> us = userMapper.selectList(new QueryWrapper<User>().in("id", userIds));
            userById = us == null ? Collections.emptyMap() : us.stream().filter(Objects::nonNull)
                    .collect(Collectors.toMap(User::getId, it -> it, (a, b) -> a));
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (ChatRoomMember mem : memberships) {
            if (mem == null) continue;
            Long rid = mem.getRoomId();
            ChatRoom r = roomById.get(rid);
            if (r == null) continue;
            ChatMessage last = lastByRoom.get(rid);
            long lastId = last == null || last.getId() == null ? 0L : last.getId();
            long lastRead = mem.getLastReadMessageId() == null ? 0L : mem.getLastReadMessageId();
            int unread = lastId > lastRead ? 1 : 0; // 最小实现：只展示“是否有新消息”（避免 count(*) 成本）

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("roomId", rid);
            row.put("chatNo", r.getChatNo());
            row.put("name", r.getName());
            row.put("collabProjectId", r.getCollabProjectId());
            row.put("role", mem.getRole());
            row.put("unread", unread);
            if (last != null) {
                row.put("lastMessageId", last.getId());
                row.put("lastContent", last.getContent());
                row.put("lastTime", last.getCreateTime());
                User u = userById.get(last.getFromUserId());
                row.put("lastFromUserId", last.getFromUserId());
                row.put("lastFromName", u == null ? null : (u.getNickname() != null && !u.getNickname().trim().isEmpty() ? u.getNickname() : u.getUsername()));
            }
            out.add(row);
        }
        return out;
    }

    public boolean isOwner(Long roomId, Long userId) {
        return hasRole(roomId, userId, "owner");
    }

    public boolean isManager(Long roomId, Long userId) {
        if (roomId == null || userId == null) return false;
        ChatRoomMember mem = memberMapper.selectOne(new QueryWrapper<ChatRoomMember>()
                .eq("room_id", roomId)
                .eq("user_id", userId)
                .last("LIMIT 1"));
        if (mem == null || mem.getRole() == null) return false;
        String r = normalizeRole(mem.getRole());
        return "owner".equals(r) || "admin".equals(r);
    }

    private boolean hasRole(Long roomId, Long userId, String role) {
        if (roomId == null || userId == null) return false;
        String want = normalizeRole(role);
        if (want == null) return false;
        ChatRoomMember mem = memberMapper.selectOne(new QueryWrapper<ChatRoomMember>()
                .eq("room_id", roomId)
                .eq("user_id", userId)
                .last("LIMIT 1"));
        if (mem == null || mem.getRole() == null) return false;
        return want.equals(normalizeRole(mem.getRole()));
    }

    private String normalizeRole(String role) {
        if (role == null) return null;
        String r = role.trim().toLowerCase();
        if (r.isEmpty()) return null;
        if ("owner".equals(r) || "admin".equals(r) || "member".equals(r)) return r;
        return r;
    }

    public void setMemberRole(Long roomId, Long requesterUserId, Long targetUserId, String role) {
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(requesterUserId, "requesterUserId");
        Objects.requireNonNull(targetUserId, "targetUserId");
        if (!isOwner(roomId, requesterUserId)) {
            throw new IllegalArgumentException("仅群主可设置管理员");
        }
        if (requesterUserId.equals(targetUserId)) {
            throw new IllegalArgumentException("不可修改自己的角色");
        }
        String r = normalizeRole(role);
        if (!"admin".equals(r) && !"member".equals(r)) {
            throw new IllegalArgumentException("role 仅支持 admin/member");
        }
        ChatRoomMember target = memberMapper.selectOne(new QueryWrapper<ChatRoomMember>()
                .eq("room_id", roomId)
                .eq("user_id", targetUserId)
                .last("LIMIT 1"));
        if (target == null) {
            throw new IllegalArgumentException("目标用户不在群内");
        }
        if ("owner".equals(normalizeRole(target.getRole()))) {
            throw new IllegalArgumentException("不可修改群主角色");
        }
        UpdateWrapper<ChatRoomMember> uw = new UpdateWrapper<>();
        uw.eq("room_id", roomId).eq("user_id", targetUserId)
                .set("role", r);
        memberMapper.update(null, uw);
    }

    public List<Map<String, Object>> listMembers(Long roomId, Long requesterUserId) {
        if (!isMember(roomId, requesterUserId)) {
            throw new IllegalArgumentException("非成员不可查看群成员");
        }
        List<ChatRoomMember> mems = memberMapper.selectList(new QueryWrapper<ChatRoomMember>()
                .eq("room_id", roomId)
                .orderByAsc("id")
                .last("LIMIT 500"));
        if (mems == null || mems.isEmpty()) return Collections.emptyList();
        List<Long> uids = mems.stream().map(ChatRoomMember::getUserId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<User> us = userMapper.selectList(new QueryWrapper<User>().in("id", uids));
        Map<Long, User> userById = us == null ? Collections.emptyMap() : us.stream().filter(Objects::nonNull)
                .collect(Collectors.toMap(User::getId, it -> it, (a, b) -> a));

        List<Map<String, Object>> out = new ArrayList<>();
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
            out.add(row);
        }
        return out;
    }

    public ChatMessage send(Long roomId, Long fromUserId, String content) {
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(fromUserId, "fromUserId");
        if (!isMember(roomId, fromUserId)) {
            throw new IllegalArgumentException("非成员不可发言");
        }
        String c = content == null ? "" : content.trim();
        if (c.isEmpty()) throw new IllegalArgumentException("消息内容不能为空");
        if (c.length() > 2000) throw new IllegalArgumentException("消息内容过长（最多 2000 字）");

        ChatMessage m = new ChatMessage();
        m.setRoomId(roomId);
        m.setFromUserId(fromUserId);
        m.setContent(c);
        messageMapper.insert(m);
        return m;
    }

    public List<Map<String, Object>> listMessages(Long roomId, Long requesterUserId, Long afterId, int limit) {
        if (!isMember(roomId, requesterUserId)) {
            throw new IllegalArgumentException("非成员不可查看消息");
        }
        int safeLimit = Math.max(1, Math.min(limit <= 0 ? 200 : limit, 500));
        QueryWrapper<ChatMessage> w = new QueryWrapper<>();
        w.eq("room_id", roomId);
        if (afterId != null && afterId > 0) {
            w.gt("id", afterId);
        }
        w.orderByAsc("id").last("LIMIT " + safeLimit);
        List<ChatMessage> list = messageMapper.selectList(w);
        if (list == null) list = Collections.emptyList();

        long maxId = 0L;
        Set<Long> uids = new LinkedHashSet<>();
        for (ChatMessage m : list) {
            if (m == null) continue;
            if (m.getId() != null) maxId = Math.max(maxId, m.getId());
            if (m.getFromUserId() != null) uids.add(m.getFromUserId());
        }
        Map<Long, User> userById = Collections.emptyMap();
        if (!uids.isEmpty()) {
            List<User> us = userMapper.selectList(new QueryWrapper<User>().in("id", uids));
            userById = us == null ? Collections.emptyMap() : us.stream().filter(Objects::nonNull)
                    .collect(Collectors.toMap(User::getId, it -> it, (a, b) -> a));
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (ChatMessage m : list) {
            if (m == null) continue;
            User u = userById.get(m.getFromUserId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", m.getId());
            row.put("roomId", m.getRoomId());
            row.put("fromUserId", m.getFromUserId());
            row.put("fromName", u == null ? null : (u.getNickname() != null && !u.getNickname().trim().isEmpty() ? u.getNickname() : u.getUsername()));
            row.put("fromAvatar", u == null ? null : u.getAvatar());
            row.put("content", m.getContent());
            row.put("createTime", m.getCreateTime());
            out.add(row);
        }

        // mark as read up to maxId
        if (maxId > 0) {
            UpdateWrapper<ChatRoomMember> uw = new UpdateWrapper<>();
            uw.eq("room_id", roomId).eq("user_id", requesterUserId)
                    .set("last_read_message_id", maxId);
            memberMapper.update(null, uw);
        }
        return out;
    }

    public void leaveRoom(Long roomId, Long userId) {
        if (roomId == null || userId == null) return;
        ChatRoomMember mem = memberMapper.selectOne(new QueryWrapper<ChatRoomMember>()
                .eq("room_id", roomId)
                .eq("user_id", userId)
                .last("LIMIT 1"));
        if (mem == null) return;
        if ("owner".equals(normalizeRole(mem.getRole()))) {
            throw new IllegalArgumentException("群主不能直接退出，请先关闭群聊");
        }
        memberMapper.delete(new QueryWrapper<ChatRoomMember>().eq("room_id", roomId).eq("user_id", userId));

        // notify owner/admins
        if (messageNoticeService != null) {
            User leaver = userMapper.selectById(userId);
            String who = leaver == null ? ("用户#" + userId) :
                    (leaver.getNickname() != null && !leaver.getNickname().trim().isEmpty() ? leaver.getNickname() : leaver.getUsername());
            ChatRoom room = chatRoomMapper.selectById(roomId);
            String roomName = room == null ? ("群聊#" + roomId) : room.getName();

            List<ChatRoomMember> managers = memberMapper.selectList(new QueryWrapper<ChatRoomMember>()
                    .eq("room_id", roomId)
                    .in("role", "owner", "admin")
                    .last("LIMIT 50"));
            if (managers != null) {
                for (ChatRoomMember m : managers) {
                    if (m == null || m.getUserId() == null) continue;
                    if (userId.equals(m.getUserId())) continue;
                    messageNoticeService.createNotice(m.getUserId(),
                            "chat_member_left",
                            "群成员退出",
                            "「" + roomName + "」中成员 " + who + " 已退出群聊。",
                            roomId);
                }
            }
        }
    }

    public void closeRoomHardDelete(Long roomId, Long ownerUserId) {
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(ownerUserId, "ownerUserId");
        if (!isOwner(roomId, ownerUserId)) {
            throw new IllegalArgumentException("仅群主可关闭群聊");
        }

        ChatRoom room = chatRoomMapper.selectById(roomId);
        String roomName = room == null ? ("群聊#" + roomId) : room.getName();

        List<ChatRoomMember> mems = memberMapper.selectList(new QueryWrapper<ChatRoomMember>()
                .eq("room_id", roomId)
                .last("LIMIT 2000"));
        List<Long> memberUserIds = new ArrayList<>();
        if (mems != null) {
            for (ChatRoomMember m : mems) {
                if (m != null && m.getUserId() != null) memberUserIds.add(m.getUserId());
            }
        }

        // delete in a safe order
        joinRequestMapper.delete(new QueryWrapper<ChatJoinRequest>().eq("room_id", roomId));
        inviteMapper.delete(new QueryWrapper<ChatInvite>().eq("room_id", roomId));
        messageMapper.delete(new QueryWrapper<ChatMessage>().eq("room_id", roomId));
        memberMapper.delete(new QueryWrapper<ChatRoomMember>().eq("room_id", roomId));
        chatRoomMapper.deleteById(roomId);

        if (messageNoticeService != null && memberUserIds != null) {
            for (Long uid : memberUserIds) {
                if (uid == null) continue;
                if (ownerUserId.equals(uid)) continue;
                messageNoticeService.createNotice(uid,
                        "chat_room_closed",
                        "群聊已取消",
                        "群主已取消群聊「" + roomName + "」。",
                        roomId);
            }
        }
    }

    public void adminCloseRoomHardDelete(Long roomId, Long adminUserId) {
        Objects.requireNonNull(roomId, "roomId");
        ChatRoom room = chatRoomMapper.selectById(roomId);
        if (room == null) throw new IllegalArgumentException("群聊不存在");

        String roomName = room.getName() == null ? ("群聊#" + roomId) : room.getName();
        List<ChatRoomMember> mems = memberMapper.selectList(new QueryWrapper<ChatRoomMember>()
                .eq("room_id", roomId)
                .last("LIMIT 2000"));
        List<Long> memberUserIds = new ArrayList<>();
        if (mems != null) {
            for (ChatRoomMember m : mems) {
                if (m != null && m.getUserId() != null) memberUserIds.add(m.getUserId());
            }
        }

        joinRequestMapper.delete(new QueryWrapper<ChatJoinRequest>().eq("room_id", roomId));
        inviteMapper.delete(new QueryWrapper<ChatInvite>().eq("room_id", roomId));
        messageMapper.delete(new QueryWrapper<ChatMessage>().eq("room_id", roomId));
        memberMapper.delete(new QueryWrapper<ChatRoomMember>().eq("room_id", roomId));
        chatRoomMapper.deleteById(roomId);

        if (messageNoticeService != null && memberUserIds != null) {
            for (Long uid : memberUserIds) {
                if (uid == null) continue;
                messageNoticeService.createNotice(uid,
                        "chat_room_closed",
                        "群聊已取消",
                        "群聊「" + roomName + "」已被管理员强制关闭。",
                        roomId);
            }
        }
    }

    public void adminKickMember(Long roomId, Long targetUserId) {
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(targetUserId, "targetUserId");
        ChatRoomMember exist = memberMapper.selectOne(new QueryWrapper<ChatRoomMember>()
                .eq("room_id", roomId)
                .eq("user_id", targetUserId)
                .last("LIMIT 1"));
        if (exist == null) throw new IllegalArgumentException("目标用户不在群内");
        if ("owner".equals(normalizeRole(exist.getRole()))) {
            throw new IllegalArgumentException("不可踢出群主");
        }
        memberMapper.delete(new QueryWrapper<ChatRoomMember>().eq("room_id", roomId).eq("user_id", targetUserId));
        if (messageNoticeService != null) {
            ChatRoom room = chatRoomMapper.selectById(roomId);
            String roomName = room == null ? ("群聊#" + roomId) : room.getName();
            messageNoticeService.createNotice(targetUserId,
                    "chat_kicked",
                    "已被移出群聊",
                    "你已被管理员移出群聊「" + roomName + "」。",
                    roomId);
        }
    }

    public Map<String, Object> searchByChatNo(String chatNo, Long requesterUserId) {
        String no = chatNo == null ? "" : chatNo.trim();
        if (no.isEmpty()) {
            throw new IllegalArgumentException("请输入群聊号");
        }
        if (!no.matches("^\\d{3,20}$")) {
            throw new IllegalArgumentException("群聊号格式不正确");
        }
        ChatRoom room = chatRoomMapper.selectOne(new QueryWrapper<ChatRoom>()
                .eq("chat_no", no)
                .orderByDesc("id")
                .last("LIMIT 1"));
        if (room == null) {
            throw new IllegalArgumentException("群聊不存在");
        }
        long memberCount = memberMapper.selectCount(new QueryWrapper<ChatRoomMember>().eq("room_id", room.getId()));
        ChatRoomMember my = requesterUserId == null ? null : memberMapper.selectOne(new QueryWrapper<ChatRoomMember>()
                .eq("room_id", room.getId())
                .eq("user_id", requesterUserId)
                .last("LIMIT 1"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("roomId", room.getId());
        out.put("chatNo", room.getChatNo());
        out.put("name", room.getName());
        out.put("memberCount", memberCount);
        out.put("isMember", my != null);
        out.put("myRole", my == null ? null : normalizeRole(my.getRole()));
        return out;
    }

    public ChatJoinRequest createJoinRequest(Long roomId, Long applicantUserId, String reason) {
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(applicantUserId, "applicantUserId");
        if (isMember(roomId, applicantUserId)) {
            throw new IllegalArgumentException("你已在群内");
        }
        ChatRoom room = chatRoomMapper.selectById(roomId);
        if (room == null) throw new IllegalArgumentException("群聊不存在");

        ChatJoinRequest exist = joinRequestMapper.selectOne(new QueryWrapper<ChatJoinRequest>()
                .eq("room_id", roomId)
                .eq("applicant_user_id", applicantUserId)
                .eq("status", "pending")
                .orderByDesc("id")
                .last("LIMIT 1"));
        if (exist != null) return exist;

        String r = reason == null ? "" : reason.trim();
        if (r.length() > 500) r = r.substring(0, 500);

        ChatJoinRequest req = new ChatJoinRequest();
        req.setRoomId(roomId);
        req.setApplicantUserId(applicantUserId);
        req.setStatus("pending");
        req.setReason(r.isEmpty() ? null : r);
        joinRequestMapper.insert(req);

        // notify managers
        if (messageNoticeService != null) {
            User who = userMapper.selectById(applicantUserId);
            String whoName = who == null ? ("用户#" + applicantUserId) :
                    (who.getNickname() != null && !who.getNickname().trim().isEmpty() ? who.getNickname() : who.getUsername());
            List<ChatRoomMember> managers = memberMapper.selectList(new QueryWrapper<ChatRoomMember>()
                    .eq("room_id", roomId)
                    .in("role", "owner", "admin")
                    .last("LIMIT 50"));
            if (managers != null) {
                for (ChatRoomMember m : managers) {
                    if (m == null || m.getUserId() == null) continue;
                    messageNoticeService.createNotice(m.getUserId(),
                            "chat_apply",
                            "入群申请",
                            "用户 " + whoName + " 申请加入群聊「" + room.getName() + "」。",
                            roomId);
                }
            }
        }
        return req;
    }

    public List<Map<String, Object>> listJoinRequests(Long roomId, Long requesterUserId, String status, int limit) {
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(requesterUserId, "requesterUserId");
        if (!isManager(roomId, requesterUserId)) {
            throw new IllegalArgumentException("无权限");
        }
        int safeLimit = Math.max(1, Math.min(limit <= 0 ? 200 : limit, 500));
        QueryWrapper<ChatJoinRequest> w = new QueryWrapper<>();
        w.eq("room_id", roomId);
        if (status != null && !status.trim().isEmpty()) w.eq("status", status.trim());
        w.orderByDesc("id").last("LIMIT " + safeLimit);
        List<ChatJoinRequest> list = joinRequestMapper.selectList(w);
        if (list == null || list.isEmpty()) return Collections.emptyList();

        Set<Long> uids = new LinkedHashSet<>();
        for (ChatJoinRequest r : list) {
            if (r != null && r.getApplicantUserId() != null) uids.add(r.getApplicantUserId());
        }
        Map<Long, User> userById = Collections.emptyMap();
        if (!uids.isEmpty()) {
            List<User> us = userMapper.selectList(new QueryWrapper<User>().in("id", uids));
            userById = us == null ? Collections.emptyMap() : us.stream().filter(Objects::nonNull)
                    .collect(Collectors.toMap(User::getId, it -> it, (a, b) -> a));
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (ChatJoinRequest r : list) {
            if (r == null) continue;
            User u = userById.get(r.getApplicantUserId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", r.getId());
            row.put("roomId", r.getRoomId());
            row.put("applicantUserId", r.getApplicantUserId());
            row.put("applicantUsername", u == null ? null : u.getUsername());
            row.put("applicantNickname", u == null ? null : u.getNickname());
            row.put("applicantAvatar", u == null ? null : u.getAvatar());
            row.put("status", r.getStatus());
            row.put("reason", r.getReason());
            row.put("handledBy", r.getHandledBy());
            row.put("handleReason", r.getHandleReason());
            row.put("createTime", r.getCreateTime());
            row.put("handleTime", r.getHandleTime());
            out.add(row);
        }
        return out;
    }

    public void reviewJoinRequest(Long applyId, Long reviewerUserId, String action, String reason) {
        Objects.requireNonNull(applyId, "applyId");
        Objects.requireNonNull(reviewerUserId, "reviewerUserId");
        ChatJoinRequest req = joinRequestMapper.selectById(applyId);
        if (req == null) throw new IllegalArgumentException("申请不存在");
        Long roomId = req.getRoomId();
        if (!isManager(roomId, reviewerUserId)) throw new IllegalArgumentException("无权限");
        if (!"pending".equalsIgnoreCase(String.valueOf(req.getStatus()))) {
            throw new IllegalArgumentException("该申请已处理");
        }
        String act = action == null ? "" : action.trim().toLowerCase();
        if (!"approved".equals(act) && !"rejected".equals(act)) {
            throw new IllegalArgumentException("action 仅支持 approved/rejected");
        }
        String rr = reason == null ? "" : reason.trim();
        if (rr.length() > 500) rr = rr.substring(0, 500);

        UpdateWrapper<ChatJoinRequest> uw = new UpdateWrapper<>();
        uw.eq("id", applyId)
                .set("status", act)
                .set("handled_by", reviewerUserId)
                .set("handle_reason", rr.isEmpty() ? null : rr)
                .set("handle_time", java.time.LocalDateTime.now());
        joinRequestMapper.update(null, uw);

        if ("approved".equals(act)) {
            ensureMember(roomId, req.getApplicantUserId(), "member");
        }

        if (messageNoticeService != null) {
            ChatRoom room = chatRoomMapper.selectById(roomId);
            String roomName = room == null ? ("群聊#" + roomId) : room.getName();
            String resText = "approved".equals(act) ? "已通过" : "已拒绝";
            messageNoticeService.createNotice(req.getApplicantUserId(),
                    "chat_apply_result",
                    "入群申请结果",
                    "你申请加入群聊「" + roomName + "」的请求" + resText + (rr.isEmpty() ? "。" : ("，原因：" + rr)),
                    roomId);
        }
    }

    public ChatInvite createInvite(Long roomId, Long inviterUserId, Long inviteeUserId) {
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(inviterUserId, "inviterUserId");
        Objects.requireNonNull(inviteeUserId, "inviteeUserId");
        if (!isManager(roomId, inviterUserId)) {
            throw new IllegalArgumentException("仅群主/管理员可邀请");
        }
        if (inviterUserId.equals(inviteeUserId)) {
            throw new IllegalArgumentException("不可邀请自己");
        }
        ChatRoom room = chatRoomMapper.selectById(roomId);
        if (room == null) throw new IllegalArgumentException("群聊不存在");
        if (isMember(roomId, inviteeUserId)) {
            throw new IllegalArgumentException("对方已在群内");
        }

        ChatInvite exist = inviteMapper.selectOne(new QueryWrapper<ChatInvite>()
                .eq("room_id", roomId)
                .eq("invitee_user_id", inviteeUserId)
                .eq("status", "pending")
                .orderByDesc("id")
                .last("LIMIT 1"));
        if (exist != null) return exist;

        ChatInvite inv = new ChatInvite();
        inv.setRoomId(roomId);
        inv.setInviterUserId(inviterUserId);
        inv.setInviteeUserId(inviteeUserId);
        inv.setStatus("pending");
        inviteMapper.insert(inv);

        if (messageNoticeService != null) {
            User inviter = userMapper.selectById(inviterUserId);
            String inviterName = inviter == null ? ("用户#" + inviterUserId) :
                    (inviter.getNickname() != null && !inviter.getNickname().trim().isEmpty() ? inviter.getNickname() : inviter.getUsername());
            messageNoticeService.createNotice(inviteeUserId,
                    "chat_invite",
                    "群聊邀请",
                    inviterName + " 邀请你加入群聊「" + room.getName() + "」。",
                    roomId);
        }
        return inv;
    }

    public List<Map<String, Object>> listMyInvites(Long inviteeUserId, String status, int limit) {
        Objects.requireNonNull(inviteeUserId, "inviteeUserId");
        int safeLimit = Math.max(1, Math.min(limit <= 0 ? 200 : limit, 500));
        QueryWrapper<ChatInvite> w = new QueryWrapper<>();
        w.eq("invitee_user_id", inviteeUserId);
        if (status != null && !status.trim().isEmpty()) w.eq("status", status.trim());
        w.orderByDesc("id").last("LIMIT " + safeLimit);
        List<ChatInvite> list = inviteMapper.selectList(w);
        if (list == null || list.isEmpty()) return Collections.emptyList();

        Set<Long> roomIds = new LinkedHashSet<>();
        Set<Long> inviterIds = new LinkedHashSet<>();
        for (ChatInvite it : list) {
            if (it == null) continue;
            if (it.getRoomId() != null) roomIds.add(it.getRoomId());
            if (it.getInviterUserId() != null) inviterIds.add(it.getInviterUserId());
        }
        Map<Long, ChatRoom> roomById = Collections.emptyMap();
        if (!roomIds.isEmpty()) {
            List<ChatRoom> rooms = chatRoomMapper.selectList(new QueryWrapper<ChatRoom>().in("id", roomIds));
            roomById = rooms == null ? Collections.emptyMap() : rooms.stream().filter(Objects::nonNull)
                    .collect(Collectors.toMap(ChatRoom::getId, it -> it, (a, b) -> a));
        }
        Map<Long, User> userById = Collections.emptyMap();
        if (!inviterIds.isEmpty()) {
            List<User> us = userMapper.selectList(new QueryWrapper<User>().in("id", inviterIds));
            userById = us == null ? Collections.emptyMap() : us.stream().filter(Objects::nonNull)
                    .collect(Collectors.toMap(User::getId, it -> it, (a, b) -> a));
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (ChatInvite it : list) {
            if (it == null) continue;
            ChatRoom room = roomById.get(it.getRoomId());
            User inviter = userById.get(it.getInviterUserId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", it.getId());
            row.put("roomId", it.getRoomId());
            row.put("roomName", room == null ? null : room.getName());
            row.put("chatNo", room == null ? null : room.getChatNo());
            row.put("inviterUserId", it.getInviterUserId());
            row.put("inviterUsername", inviter == null ? null : inviter.getUsername());
            row.put("inviterNickname", inviter == null ? null : inviter.getNickname());
            row.put("inviterAvatar", inviter == null ? null : inviter.getAvatar());
            row.put("status", it.getStatus());
            row.put("createTime", it.getCreateTime());
            row.put("handleTime", it.getHandleTime());
            out.add(row);
        }
        return out;
    }

    public void respondInvite(Long inviteId, Long inviteeUserId, String action) {
        Objects.requireNonNull(inviteId, "inviteId");
        Objects.requireNonNull(inviteeUserId, "inviteeUserId");
        ChatInvite inv = inviteMapper.selectById(inviteId);
        if (inv == null) throw new IllegalArgumentException("邀请不存在");
        if (!inviteeUserId.equals(inv.getInviteeUserId())) throw new IllegalArgumentException("无权限");
        if (!"pending".equalsIgnoreCase(String.valueOf(inv.getStatus()))) throw new IllegalArgumentException("该邀请已处理");

        String act = action == null ? "" : action.trim().toLowerCase();
        if (!"accepted".equals(act) && !"rejected".equals(act)) {
            throw new IllegalArgumentException("action 仅支持 accepted/rejected");
        }

        UpdateWrapper<ChatInvite> uw = new UpdateWrapper<>();
        uw.eq("id", inviteId)
                .set("status", act)
                .set("handle_time", java.time.LocalDateTime.now());
        inviteMapper.update(null, uw);

        if ("accepted".equals(act)) {
            ensureMember(inv.getRoomId(), inviteeUserId, "member");
        }

        if (messageNoticeService != null && inv.getInviterUserId() != null) {
            ChatRoom room = chatRoomMapper.selectById(inv.getRoomId());
            String roomName = room == null ? ("群聊#" + inv.getRoomId()) : room.getName();
            User who = userMapper.selectById(inviteeUserId);
            String whoName = who == null ? ("用户#" + inviteeUserId) :
                    (who.getNickname() != null && !who.getNickname().trim().isEmpty() ? who.getNickname() : who.getUsername());
            String resText = "accepted".equals(act) ? "已接受" : "已拒绝";
            messageNoticeService.createNotice(inv.getInviterUserId(),
                    "chat_invite_result",
                    "群聊邀请结果",
                    whoName + " " + resText + "加入群聊「" + roomName + "」的邀请。",
                    inv.getRoomId());
        }
    }
}

