import { ref } from 'vue'

/**
 * 通知的全局轻量状态。
 * - unreadCount：用于 AppShell 角标
 * - changedTick：用于通知页监听“有新消息”，触发 reload
 */
export const notificationsStore = {
  unreadCount: ref(0),
  changedTick: ref(0),
  setUnread(n: number) {
    notificationsStore.unreadCount.value = Math.max(0, Number(n) || 0)
  },
  bump() {
    notificationsStore.changedTick.value++
  },
}

