package com.zhaoyichi.devplatformbackend.controller;

import com.zhaoyichi.devplatformbackend.common.Result;
import com.zhaoyichi.devplatformbackend.entity.ChatMessage;
import com.zhaoyichi.devplatformbackend.entity.ChatRoom;
import com.zhaoyichi.devplatformbackend.service.ChatService;
import com.zhaoyichi.devplatformbackend.utils.AuthHelper;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/rooms")
    public Result<List<Map<String, Object>>> rooms(@RequestParam(required = false, defaultValue = "50") int limit,
                                                   HttpServletRequest request) {
        Result<List<Map<String, Object>>> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        Long uid = AuthHelper.currentUserId(request);
        try {
            return Result.success(chatService.listRooms(uid, limit));
        } catch (Exception e) {
            return Result.systemError("加载失败");
        }
    }

    @GetMapping("/search")
    public Result<Map<String, Object>> search(@RequestParam String chatNo, HttpServletRequest request) {
        Result<Map<String, Object>> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        Long uid = AuthHelper.currentUserId(request);
        try {
            return Result.success(chatService.searchByChatNo(chatNo, uid));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.systemError("查询失败");
        }
    }

    @PostMapping("/room/create")
    public Result<Map<String, Object>> createRoom(@RequestBody CreateRoomBody body, HttpServletRequest request) {
        Result<Map<String, Object>> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        Long uid = AuthHelper.currentUserId(request);
        try {
            List<Long> members = body == null ? Collections.emptyList() : body.getMemberUserIds();
            ChatRoom r = chatService.createRoom(uid,
                    body == null ? null : body.getName(),
                    body == null ? null : body.getCollabProjectId(),
                    members);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("roomId", r.getId());
            out.put("chatNo", r.getChatNo());
            out.put("name", r.getName());
            return Result.success(out);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.systemError("创建失败");
        }
    }

    @PostMapping("/room/{roomId}/apply")
    public Result<Map<String, Object>> apply(@PathVariable Long roomId, @RequestBody ApplyBody body, HttpServletRequest request) {
        Result<Map<String, Object>> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        Long uid = AuthHelper.currentUserId(request);
        try {
            com.zhaoyichi.devplatformbackend.entity.ChatJoinRequest req =
                    chatService.createJoinRequest(roomId, uid, body == null ? null : body.getReason());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("applyId", req.getId());
            out.put("status", req.getStatus());
            return Result.success(out);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.systemError("操作失败");
        }
    }

    @GetMapping("/room/{roomId}/applies")
    public Result<List<Map<String, Object>>> listApplies(@PathVariable Long roomId,
                                                         @RequestParam(required = false) String status,
                                                         @RequestParam(required = false, defaultValue = "200") int limit,
                                                         HttpServletRequest request) {
        Result<List<Map<String, Object>>> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        Long uid = AuthHelper.currentUserId(request);
        try {
            return Result.success(chatService.listJoinRequests(roomId, uid, status, limit));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.systemError("加载失败");
        }
    }

    @PostMapping("/apply/{applyId}/review")
    public Result<String> reviewApply(@PathVariable Long applyId,
                                      @RequestParam String action,
                                      @RequestParam(required = false) String reason,
                                      HttpServletRequest request) {
        Result<String> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        Long uid = AuthHelper.currentUserId(request);
        try {
            chatService.reviewJoinRequest(applyId, uid, action, reason);
            return Result.successMsg("已处理");
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.systemError("操作失败");
        }
    }

    @PostMapping("/room/{roomId}/invite")
    public Result<Map<String, Object>> invite(@PathVariable Long roomId, @RequestBody InviteBody body, HttpServletRequest request) {
        Result<Map<String, Object>> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        Long uid = AuthHelper.currentUserId(request);
        try {
            Long inviteeUserId = body == null ? null : body.getInviteeUserId();
            com.zhaoyichi.devplatformbackend.entity.ChatInvite inv =
                    chatService.createInvite(roomId, uid, inviteeUserId);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("inviteId", inv.getId());
            out.put("status", inv.getStatus());
            return Result.success(out);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.systemError("操作失败");
        }
    }

    @GetMapping("/invites/my")
    public Result<List<Map<String, Object>>> myInvites(@RequestParam(required = false) String status,
                                                       @RequestParam(required = false, defaultValue = "200") int limit,
                                                       HttpServletRequest request) {
        Result<List<Map<String, Object>>> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        Long uid = AuthHelper.currentUserId(request);
        try {
            return Result.success(chatService.listMyInvites(uid, status, limit));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.systemError("加载失败");
        }
    }

    @PostMapping("/invite/{inviteId}/respond")
    public Result<String> respondInvite(@PathVariable Long inviteId,
                                        @RequestParam String action,
                                        HttpServletRequest request) {
        Result<String> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        Long uid = AuthHelper.currentUserId(request);
        try {
            chatService.respondInvite(inviteId, uid, action);
            return Result.successMsg("已处理");
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.systemError("操作失败");
        }
    }

    @PostMapping("/room/{roomId}/leave")
    public Result<String> leave(@PathVariable Long roomId, HttpServletRequest request) {
        Result<String> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        Long uid = AuthHelper.currentUserId(request);
        try {
            chatService.leaveRoom(roomId, uid);
            return Result.successMsg("已退出群聊");
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.systemError("操作失败");
        }
    }

    @PostMapping("/room/{roomId}/close")
    public Result<String> close(@PathVariable Long roomId, HttpServletRequest request) {
        Result<String> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        Long uid = AuthHelper.currentUserId(request);
        try {
            chatService.closeRoomHardDelete(roomId, uid);
            return Result.successMsg("群聊已取消");
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.systemError("操作失败");
        }
    }

    @GetMapping("/room/{roomId}/members")
    public Result<List<Map<String, Object>>> members(@PathVariable Long roomId, HttpServletRequest request) {
        Result<List<Map<String, Object>>> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        Long uid = AuthHelper.currentUserId(request);
        try {
            return Result.success(chatService.listMembers(roomId, uid));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.systemError("加载失败");
        }
    }

    @GetMapping("/room/{roomId}/messages")
    public Result<List<Map<String, Object>>> messages(@PathVariable Long roomId,
                                                      @RequestParam(required = false) Long afterId,
                                                      @RequestParam(required = false, defaultValue = "200") int limit,
                                                      HttpServletRequest request) {
        Result<List<Map<String, Object>>> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        Long uid = AuthHelper.currentUserId(request);
        try {
            return Result.success(chatService.listMessages(roomId, uid, afterId, limit));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.systemError("加载失败");
        }
    }

    @PostMapping("/room/{roomId}/send")
    public Result<ChatMessage> send(@PathVariable Long roomId, @RequestBody SendBody body, HttpServletRequest request) {
        Result<ChatMessage> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        Long uid = AuthHelper.currentUserId(request);
        try {
            return Result.success(chatService.send(roomId, uid, body == null ? null : body.getContent()));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.systemError("发送失败");
        }
    }

    @PostMapping("/room/{roomId}/members/{userId}/role")
    public Result<String> setMemberRole(@PathVariable Long roomId,
                                        @PathVariable Long userId,
                                        @RequestBody SetRoleBody body,
                                        HttpServletRequest request) {
        Result<String> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        Long uid = AuthHelper.currentUserId(request);
        try {
            chatService.setMemberRole(roomId, uid, userId, body == null ? null : body.getRole());
            return Result.successMsg("已更新");
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.systemError("操作失败");
        }
    }

    @Data
    public static class SendBody {
        private String content;
    }

    @Data
    public static class SetRoleBody {
        private String role; // admin/member
    }

    @Data
    public static class ApplyBody {
        private String reason;
    }

    @Data
    public static class InviteBody {
        private Long inviteeUserId;
    }

    @Data
    public static class CreateRoomBody {
        private String name;
        private Integer collabProjectId;
        private List<Long> memberUserIds;
    }
}

