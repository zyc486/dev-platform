package com.zhaoyichi.devplatformbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhaoyichi.devplatformbackend.common.Result;
import com.zhaoyichi.devplatformbackend.entity.*;
import com.zhaoyichi.devplatformbackend.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class CommunityService {

    private final CommunityMapper communityMapper;
    private final CommunityMemberMapper memberMapper;
    private final CommunityApplyMapper applyMapper;
    private final CommunityPostMapper postMapper;
    private final CommunityAttachmentMapper attachmentMapper;
    private final MessageNoticeService noticeService;
    private final UserMapper userMapper;

    public CommunityService(CommunityMapper communityMapper,
                             CommunityMemberMapper memberMapper,
                             CommunityApplyMapper applyMapper,
                             CommunityPostMapper postMapper,
                             CommunityAttachmentMapper attachmentMapper,
                             MessageNoticeService noticeService,
                             UserMapper userMapper) {
        this.communityMapper = communityMapper;
        this.memberMapper = memberMapper;
        this.applyMapper = applyMapper;
        this.postMapper = postMapper;
        this.attachmentMapper = attachmentMapper;
        this.noticeService = noticeService;
        this.userMapper = userMapper;
    }

    @Transactional
    public Result<Long> create(Community community, Long creatorId) {
        community.setCreatorId(creatorId);
        community.setStatus("active");
        community.setMemberCount(1);
        community.setCreateTime(LocalDateTime.now());
        communityMapper.insert(community);

        CommunityMember member = new CommunityMember();
        member.setCommunityId(community.getId());
        member.setUserId(creatorId);
        member.setRole("creator");
        member.setJoinTime(LocalDateTime.now());
        memberMapper.insert(member);
        return Result.success(community.getId());
    }

    public List<Community> list(String tag, int page, int size) {
        QueryWrapper<Community> wrapper = new QueryWrapper<>();
        wrapper.eq("status", "active");
        if (tag != null && !tag.isEmpty()) {
            wrapper.like("tech_tags", tag);
        }
        wrapper.orderByDesc("member_count", "create_time")
                .last("LIMIT " + size + " OFFSET " + (page - 1) * size);
        return communityMapper.selectList(wrapper);
    }

    public Community getById(Long id) {
        return communityMapper.selectById(id);
    }

    public Result<String> apply(Long communityId, Long userId, String reason) {
        Community community = communityMapper.selectById(communityId);
        if (community == null || !"active".equals(community.getStatus())) {
            return Result.error("社群不存在或已解散");
        }
        if (memberMapper.selectCount(new QueryWrapper<CommunityMember>()
                .eq("community_id", communityId).eq("user_id", userId)) > 0) {
            return Result.error("您已是该社群成员");
        }
        if (applyMapper.selectCount(new QueryWrapper<CommunityApply>()
                .eq("community_id", communityId).eq("user_id", userId).eq("status", "pending")) > 0) {
            return Result.error("您已提交申请，等待审核中");
        }

        CommunityApply apply = new CommunityApply();
        apply.setCommunityId(communityId);
        apply.setUserId(userId);
        apply.setApplyReason(reason);
        apply.setStatus("pending");
        apply.setCreateTime(LocalDateTime.now());
        applyMapper.insert(apply);

        noticeService.createNotice(community.getCreatorId(), "community",
                "新的入群申请", "社群「" + community.getName() + "」收到新的入群申请", userId);
        return Result.successMsg("申请已提交，等待审核");
    }

    @Transactional
    public Result<String> reviewApply(Long applyId, Long reviewerId, String action) {
        CommunityApply apply = applyMapper.selectById(applyId);
        if (apply == null) return Result.error("申请不存在");

        Community community = communityMapper.selectById(apply.getCommunityId());
        if (community == null) return Result.error("社群不存在");
        if (!community.getCreatorId().equals(reviewerId)) {
            QueryWrapper<CommunityMember> adminCheck = new QueryWrapper<>();
            adminCheck.eq("community_id", apply.getCommunityId())
                    .eq("user_id", reviewerId).in("role", "creator", "admin");
            if (memberMapper.selectCount(adminCheck) == 0) {
                return Result.error("无权审核");
            }
        }

        apply.setStatus("approved".equals(action) ? "approved" : "rejected");
        apply.setReviewTime(LocalDateTime.now());
        applyMapper.updateById(apply);

        if ("approved".equals(action)) {
            CommunityMember member = new CommunityMember();
            member.setCommunityId(apply.getCommunityId());
            member.setUserId(apply.getUserId());
            member.setRole("member");
            member.setJoinTime(LocalDateTime.now());
            memberMapper.insert(member);

            community.setMemberCount(community.getMemberCount() + 1);
            communityMapper.updateById(community);

            noticeService.createNotice(apply.getUserId(), "community",
                    "入群申请已通过", "您已成功加入社群「" + community.getName() + "」", community.getId());
        } else {
            noticeService.createNotice(apply.getUserId(), "community",
                    "入群申请被拒绝", "您加入社群「" + community.getName() + "」的申请未通过", community.getId());
        }
        return Result.successMsg("审核完成");
    }

    public List<CommunityMember> getMembers(Long communityId) {
        List<CommunityMember> list = memberMapper.selectList(new QueryWrapper<CommunityMember>().eq("community_id", communityId));
        if (list == null || list.isEmpty()) {
            return list;
        }
        List<Long> uids = list.stream().map(CommunityMember::getUserId).filter(Objects::nonNull).collect(Collectors.toList());
        if (uids.isEmpty()) return list;
        List<User> users = userMapper.selectList(new QueryWrapper<User>().in("id", uids));
        Map<Long, User> byId = users == null ? Collections.emptyMap()
                : users.stream().filter(Objects::nonNull).collect(Collectors.toMap(User::getId, it -> it, (a, b) -> a));
        for (CommunityMember m : list) {
            if (m == null) continue;
            User u = byId.get(m.getUserId());
            if (u == null) continue;
            m.setUsername(u.getUsername());
            m.setNickname(u.getNickname());
            m.setAvatar(u.getAvatar());
            m.setTotalScore(u.getTotalScore());
            m.setLevel(u.getLevel());
        }
        return list;
    }

    public List<CommunityApply> getPendingApplies(Long communityId, Long reviewerId) {
        return applyMapper.selectList(new QueryWrapper<CommunityApply>()
                .eq("community_id", communityId).eq("status", "pending"));
    }

    public Result<Long> publishPost(CommunityPost post, Long userId) {
        Community community = communityMapper.selectById(post.getCommunityId());
        if (community == null) return Result.error("社群不存在");

        if (!"announcement".equals(post.getType())) {
            if (memberMapper.selectCount(new QueryWrapper<CommunityMember>()
                    .eq("community_id", post.getCommunityId()).eq("user_id", userId)) == 0) {
                return Result.error("非社群成员不能发帖");
            }
        } else {
            if (!community.getCreatorId().equals(userId)) {
                QueryWrapper<CommunityMember> adminCheck = new QueryWrapper<>();
                adminCheck.eq("community_id", post.getCommunityId())
                        .eq("user_id", userId).in("role", "creator", "admin");
                if (memberMapper.selectCount(adminCheck) == 0) {
                    return Result.error("只有创建者/管理员才能发布公告");
                }
            }
        }
        post.setUserId(userId);
        post.setCreateTime(LocalDateTime.now());
        if (post.getIsSticky() == null) {
            post.setIsSticky(0);
        }
        if (post.getIsEssence() == null) {
            post.setIsEssence(0);
        }
        if (post.getCategory() == null || post.getCategory().trim().isEmpty()) {
            post.setCategory("discussion");
        }
        postMapper.insert(post);
        return Result.success(post.getId());
    }

    public List<CommunityPost> getPosts(Long communityId, String type, String category) {
        QueryWrapper<CommunityPost> wrapper = new QueryWrapper<>();
        wrapper.eq("community_id", communityId);
        if (type != null && !type.isEmpty()) {
            wrapper.eq("type", type);
        }
        if (category != null && !category.isEmpty() && !"all".equalsIgnoreCase(category)) {
            wrapper.eq("category", category);
        }
        wrapper.orderByDesc("is_sticky").orderByDesc("is_essence").orderByDesc("create_time");
        List<CommunityPost> list = postMapper.selectList(wrapper);
        fillAuthorNames(list);
        return list;
    }

    private void fillAuthorNames(List<CommunityPost> posts) {
        if (posts == null || posts.isEmpty()) {
            return;
        }
        for (CommunityPost p : posts) {
            User u = userMapper.selectById(p.getUserId());
            p.setAuthorUsername(u != null && u.getUsername() != null ? u.getUsername() : ("用户" + p.getUserId()));
        }
    }

    public Result<String> setPostSticky(Long postId, Long operatorId, boolean value) {
        CommunityPost post = postMapper.selectById(postId);
        if (post == null) {
            return Result.error("帖子不存在");
        }
        if (!canManageCommunityContent(post.getCommunityId(), operatorId)) {
            return Result.error("无权操作置顶");
        }
        post.setIsSticky(value ? 1 : 0);
        postMapper.updateById(post);
        return Result.successMsg(value ? "已置顶" : "已取消置顶");
    }

    public Result<String> setPostEssence(Long postId, Long operatorId, boolean value) {
        CommunityPost post = postMapper.selectById(postId);
        if (post == null) {
            return Result.error("帖子不存在");
        }
        if (!canManageCommunityContent(post.getCommunityId(), operatorId)) {
            return Result.error("无权操作精华");
        }
        post.setIsEssence(value ? 1 : 0);
        postMapper.updateById(post);
        return Result.successMsg(value ? "已加精" : "已取消精华");
    }

    /**
     * 社群创建者/社群管理员，或平台管理员可操作置顶与精华。
     */
    private boolean canManageCommunityContent(Long communityId, Long userId) {
        if (userId == null) {
            return false;
        }
        if (isPlatformAdmin(userId)) {
            return true;
        }
        Community community = communityMapper.selectById(communityId);
        if (community == null) {
            return false;
        }
        if (community.getCreatorId() != null && community.getCreatorId().equals(userId)) {
            return true;
        }
        return memberMapper.selectCount(new QueryWrapper<CommunityMember>()
                .eq("community_id", communityId)
                .eq("user_id", userId)
                .in("role", "creator", "admin")) > 0;
    }

    private boolean isPlatformAdmin(Long userId) {
        User u = userMapper.selectById(userId);
        return u != null && "admin".equalsIgnoreCase(u.getRole());
    }

    public boolean isMember(Long communityId, Long userId) {
        return memberMapper.selectCount(new QueryWrapper<CommunityMember>()
                .eq("community_id", communityId).eq("user_id", userId)) > 0;
    }

    public CommunityAttachment uploadAttachment(Long postId, Long operatorId, MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalStateException("文件为空");
        if (file.getSize() > 20L * 1024 * 1024) throw new IllegalStateException("文件过大，限制 20MB");

        CommunityPost post = postMapper.selectById(postId);
        if (post == null) throw new IllegalStateException("帖子不存在");

        // 仅作者或社群管理员/创建者可上传
        boolean isAuthor = post.getUserId() != null && post.getUserId().equals(operatorId);
        if (!isAuthor && !canManageCommunityContent(post.getCommunityId(), operatorId)) {
            throw new IllegalStateException("无权限上传附件");
        }

        String originalName = sanitizeFilename(file.getOriginalFilename());
        String ext = "";
        int dot = originalName.lastIndexOf('.');
        if (dot > 0 && dot < originalName.length() - 1) ext = originalName.substring(dot);
        String storedName = "f_" + System.currentTimeMillis() + "_" + Math.abs(originalName.hashCode()) + ext;

        Path dir = Paths.get(System.getProperty("user.dir"), "uploads", "community",
                String.valueOf(post.getCommunityId()), "posts", String.valueOf(postId));
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            throw new IllegalStateException("创建上传目录失败");
        }
        Path target = dir.resolve(storedName);
        try {
            file.transferTo(Objects.requireNonNull(target.toFile(), "targetFile"));
        } catch (Exception e) {
            throw new IllegalStateException("保存文件失败");
        }

        CommunityAttachment a = new CommunityAttachment();
        a.setCommunityId(post.getCommunityId());
        a.setPostId(postId);
        a.setUserId(operatorId);
        a.setOriginalName(originalName);
        a.setStoragePath(toRelativeUploadsPath(target));
        a.setContentType(file.getContentType());
        a.setSizeBytes(file.getSize());
        attachmentMapper.insert(a);
        return a;
    }

    public List<CommunityAttachment> listAttachments(Long postId) {
        return attachmentMapper.selectList(new QueryWrapper<CommunityAttachment>()
                .eq("post_id", postId).orderByDesc("create_time"));
    }

    private static String sanitizeFilename(String name) {
        if (name == null || name.trim().isEmpty()) return "file";
        String s = name.trim().replace("\\", "_").replace("/", "_");
        s = s.replaceAll("[\\p{Cntrl}]", "_");
        if (s.length() > 200) s = s.substring(s.length() - 200);
        return s;
    }

    private static String toRelativeUploadsPath(Path absolutePath) {
        Path root = Paths.get(System.getProperty("user.dir"), "uploads");
        Path rel = root.relativize(absolutePath);
        return rel.toString().replace("\\", "/");
    }
}
