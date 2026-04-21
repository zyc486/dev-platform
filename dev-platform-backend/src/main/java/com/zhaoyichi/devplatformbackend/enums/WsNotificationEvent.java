package com.zhaoyichi.devplatformbackend.enums;

/**
 * WebSocket 推送中的业务事件类型（与 message_notice.type 可不同，用于前端区分展示）。
 */
public enum WsNotificationEvent {
    COMMENT,
    LIKE,
    FOLLOW,
    COLLAB_APPLY_STATUS,
    DM,
    SYSTEM
}
