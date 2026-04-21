package com.zhaoyichi.devplatformbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.zhaoyichi.devplatformbackend.entity.DmMessage;
import com.zhaoyichi.devplatformbackend.entity.User;
import com.zhaoyichi.devplatformbackend.mapper.DmMessageMapper;
import com.zhaoyichi.devplatformbackend.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DmService {
    private final DmMessageMapper dmMessageMapper;
    private final UserMapper userMapper;
    private final MessageNoticeService messageNoticeService;

    public DmService(DmMessageMapper dmMessageMapper,
                     UserMapper userMapper,
                     MessageNoticeService messageNoticeService) {
        this.dmMessageMapper = dmMessageMapper;
        this.userMapper = userMapper;
        this.messageNoticeService = messageNoticeService;
    }

    public DmMessage send(Long fromUserId, Long toUserId, String content) {
        Objects.requireNonNull(fromUserId, "fromUserId");
        Objects.requireNonNull(toUserId, "toUserId");
        String c = content == null ? "" : content.trim();
        if (c.isEmpty()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
        if (c.length() > 2000) {
            throw new IllegalArgumentException("消息内容过长（最多 2000 字）");
        }
        if (fromUserId.equals(toUserId)) {
            throw new IllegalArgumentException("不能给自己发送私信");
        }

        User to = userMapper.selectById(toUserId);
        if (to == null) {
            throw new IllegalArgumentException("对方用户不存在");
        }
        if (to.getPrivacyAllowMessage() != null && to.getPrivacyAllowMessage() == 0) {
            throw new IllegalArgumentException("对方已关闭私信");
        }
        User from = userMapper.selectById(fromUserId);
        String fromName = from == null ? "用户" : (from.getNickname() != null && !from.getNickname().trim().isEmpty()
                ? from.getNickname().trim()
                : from.getUsername());

        DmMessage msg = new DmMessage();
        msg.setFromUserId(fromUserId);
        msg.setToUserId(toUserId);
        msg.setContent(c);
        msg.setIsRead(0);
        dmMessageMapper.insert(msg);

        // 复用站内通知与 WS：relatedId 约定为“发送者 userId”，便于通知中心跳到对应会话。
        String snippet = c.length() <= 60 ? c : c.substring(0, 60) + "…";
        messageNoticeService.createNotice(toUserId, "dm", "收到新私信", "来自 " + fromName + "：" + snippet, fromUserId);
        return msg;
    }

    public List<DmMessage> listWith(Long userId, Long withUserId, int limit) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(withUserId, "withUserId");
        int safeLimit = Math.max(1, Math.min(limit <= 0 ? 200 : limit, 500));

        QueryWrapper<DmMessage> w = new QueryWrapper<>();
        w.and(q -> q.eq("from_user_id", userId).eq("to_user_id", withUserId))
                .or(q -> q.eq("from_user_id", withUserId).eq("to_user_id", userId))
                .orderByAsc("create_time")
                .last("LIMIT " + safeLimit);
        List<DmMessage> list = dmMessageMapper.selectList(w);

        // 标记“对我发送”的消息为已读
        UpdateWrapper<DmMessage> uw = new UpdateWrapper<>();
        uw.eq("from_user_id", withUserId)
                .eq("to_user_id", userId)
                .eq("is_read", 0)
                .set("is_read", 1);
        dmMessageMapper.update(null, uw);
        return list == null ? Collections.emptyList() : list;
    }

    /**
     * 会话列表：返回最近一条消息 + 未读数 + 对方基本信息。
     * 为最小实现，采用“拉取最近 N 条消息后在内存聚合”，避免引入复杂 SQL。
     */
    public List<Map<String, Object>> conversations(Long userId, int limit) {
        Objects.requireNonNull(userId, "userId");
        int safeLimit = Math.max(1, Math.min(limit <= 0 ? 30 : limit, 100));

        QueryWrapper<DmMessage> w = new QueryWrapper<>();
        w.and(q -> q.eq("from_user_id", userId).or().eq("to_user_id", userId))
                .orderByDesc("create_time")
                .orderByDesc("id")
                .last("LIMIT 800");
        List<DmMessage> recent = dmMessageMapper.selectList(w);
        if (recent == null || recent.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, Integer> unreadByPeer = new HashMap<>();
        Map<Long, DmMessage> lastByPeer = new LinkedHashMap<>();
        Set<Long> peerIds = new LinkedHashSet<>();

        for (DmMessage m : recent) {
            if (m == null) continue;
            Long from = m.getFromUserId();
            Long to = m.getToUserId();
            if (from == null || to == null) continue;
            Long peer = userId.equals(from) ? to : from;
            peerIds.add(peer);

            // 未读：对方发给我 && is_read=0
            if (userId.equals(to) && (m.getIsRead() == null || m.getIsRead() == 0)) {
                unreadByPeer.put(peer, (unreadByPeer.getOrDefault(peer, 0) + 1));
            }
            if (!lastByPeer.containsKey(peer)) {
                lastByPeer.put(peer, m);
            }
        }

        if (peerIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<User> peers = userMapper.selectList(new QueryWrapper<User>().in("id", peerIds));
        Map<Long, User> peerById = peers == null ? Collections.emptyMap()
                : peers.stream().filter(Objects::nonNull).collect(Collectors.toMap(User::getId, it -> it, (a, b) -> a));

        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<Long, DmMessage> e : lastByPeer.entrySet()) {
            if (out.size() >= safeLimit) break;
            Long peerId = e.getKey();
            DmMessage last = e.getValue();
            User peer = peerById.get(peerId);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("withUserId", peerId);
            row.put("withUsername", peer == null ? null : peer.getUsername());
            row.put("withNickname", peer == null ? null : peer.getNickname());
            row.put("withAvatar", peer == null ? null : peer.getAvatar());
            row.put("lastContent", last == null ? "" : last.getContent());
            row.put("lastTime", last == null || last.getCreateTime() == null ? "" : last.getCreateTime().toString());
            row.put("unread", unreadByPeer.getOrDefault(peerId, 0));
            out.add(row);
        }
        return out;
    }
}

