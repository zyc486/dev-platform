package com.zhaoyichi.devplatformbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhaoyichi.devplatformbackend.entity.User;
import com.zhaoyichi.devplatformbackend.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析动态 / 评论正文中的 {@code @用户名} 片段，并为被提及的用户写站内消息。
 *
 * <p>匹配规则：{@code @} 后 2~30 位字母 / 数字 / 下划线 / 中文；
 * 排除自己提及自己的噪音；每条内容同一被提及者只通知一次。</p>
 */
@Service
public class MentionService {
    private static final Pattern PAT = Pattern.compile("@([A-Za-z0-9_\\u4e00-\\u9fa5]{2,30})");

    private final UserMapper userMapper;
    private final MessageNoticeService messageNoticeService;

    public MentionService(UserMapper userMapper, MessageNoticeService messageNoticeService) {
        this.userMapper = userMapper;
        this.messageNoticeService = messageNoticeService;
    }

    /**
     * 解析并通知。
     *
     * @param content      内容（帖子正文或评论文本）
     * @param relatedId    关联 ID（一般为 postId），用于点击跳转
     * @param fromUserId   操作者 userId，避免自我提及形成噪音
     */
    public void parseAndNotify(String content, Long relatedId, Long fromUserId) {
        if (content == null || content.isEmpty()) {
            return;
        }
        Set<String> seen = new HashSet<>();
        Matcher m = PAT.matcher(content);
        while (m.find()) {
            String uname = m.group(1);
            if (!seen.add(uname)) {
                continue;
            }
            User u = userMapper.selectOne(new QueryWrapper<User>().eq("username", uname));
            if (u == null || u.getId() == null) {
                continue;
            }
            if (fromUserId != null && u.getId().equals(fromUserId)) {
                continue;
            }
            String snippet = content.length() > 80 ? content.substring(0, 80) + "…" : content;
            messageNoticeService.createNotice(
                    u.getId(),
                    "mention",
                    "有人在动态中提到了你",
                    snippet,
                    relatedId);
        }
    }
}
