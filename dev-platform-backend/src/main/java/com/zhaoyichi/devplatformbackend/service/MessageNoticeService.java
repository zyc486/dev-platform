package com.zhaoyichi.devplatformbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhaoyichi.devplatformbackend.entity.MessageNotice;
import com.zhaoyichi.devplatformbackend.entity.User;
import com.zhaoyichi.devplatformbackend.enums.WsNotificationEvent;
import com.zhaoyichi.devplatformbackend.mapper.MessageNoticeMapper;
import com.zhaoyichi.devplatformbackend.mapper.UserMapper;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class MessageNoticeService {
    private final MessageNoticeMapper messageNoticeMapper;
    private final UserMapper userMapper;
    private final SimpMessagingTemplate simpMessagingTemplate;

    public MessageNoticeService(MessageNoticeMapper messageNoticeMapper,
                                UserMapper userMapper,
                                SimpMessagingTemplate simpMessagingTemplate) {
        this.messageNoticeMapper = messageNoticeMapper;
        this.userMapper = userMapper;
        this.simpMessagingTemplate = simpMessagingTemplate;
    }

    public void createNotice(Long userId, String type, String title, String content, Long relatedId) {
        if (userId == null) {
            return;
        }
        MessageNotice notice = new MessageNotice();
        notice.setUserId(userId);
        notice.setType(type);
        notice.setTitle(title);
        notice.setContent(content);
        notice.setRelatedId(relatedId);
        notice.setIsRead(0);
        notice.setCreateTime(LocalDateTime.now());
        messageNoticeMapper.insert(notice);
        pushWebSocket(notice);
    }

    private void pushWebSocket(MessageNotice notice) {
        try {
            if (notice == null || notice.getUserId() == null) {
                return;
            }
            User user = userMapper.selectById(notice.getUserId());
            if (user == null || user.getUsername() == null) {
                return;
            }
            String username = Objects.requireNonNull(user.getUsername(), "username");
            WsNotificationEvent event = mapWsEvent(notice.getType(), notice.getTitle());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", notice.getId());
            payload.put("dbType", notice.getType());
            payload.put("event", event.name());
            payload.put("title", notice.getTitle());
            payload.put("content", notice.getContent());
            payload.put("relatedId", notice.getRelatedId());
            payload.put("createTime", notice.getCreateTime() != null ? notice.getCreateTime().toString() : "");
            simpMessagingTemplate.convertAndSendToUser(username, "/queue/notifications", payload);
        } catch (Exception ignored) {
        }
    }

    private WsNotificationEvent mapWsEvent(String dbType, String title) {
        if ("dm".equals(dbType)) {
            return WsNotificationEvent.DM;
        }
        if ("follow".equals(dbType)) {
            return WsNotificationEvent.FOLLOW;
        }
        if ("like".equals(dbType)) {
            return WsNotificationEvent.LIKE;
        }
        if ("comment".equals(dbType)) {
            return WsNotificationEvent.COMMENT;
        }
        if ("collab".equals(dbType)) {
            if (title != null && title.contains("互评")) {
                return WsNotificationEvent.COMMENT;
            }
            if (title != null && title.contains("标记为已完成")) {
                return WsNotificationEvent.SYSTEM;
            }
            return WsNotificationEvent.COLLAB_APPLY_STATUS;
        }
        return WsNotificationEvent.SYSTEM;
    }

    public List<MessageNotice> listByUserId(Long userId) {
        QueryWrapper<MessageNotice> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).orderByDesc("create_time");
        return messageNoticeMapper.selectList(wrapper);
    }

    public void markRead(Long userId, Long id) {
        QueryWrapper<MessageNotice> wrapper = new QueryWrapper<>();
        wrapper.eq("id", id).eq("user_id", userId);
        MessageNotice notice = messageNoticeMapper.selectOne(wrapper);
        if (notice != null && (notice.getIsRead() == null || notice.getIsRead() == 0)) {
            notice.setIsRead(1);
            messageNoticeMapper.updateById(notice);
        }
    }

    public void markAllRead(Long userId) {
        List<MessageNotice> list = listByUserId(userId);
        for (MessageNotice notice : list) {
            if (notice.getIsRead() == null || notice.getIsRead() == 0) {
                notice.setIsRead(1);
                messageNoticeMapper.updateById(notice);
            }
        }
    }

    public void deleteOne(Long userId, Long id) {
        QueryWrapper<MessageNotice> wrapper = new QueryWrapper<>();
        wrapper.eq("id", id).eq("user_id", userId);
        messageNoticeMapper.delete(wrapper);
    }

    public void deleteAll(Long userId) {
        QueryWrapper<MessageNotice> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        messageNoticeMapper.delete(wrapper);
    }
}
