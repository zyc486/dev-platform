<template>
  <div class="page">
    <el-card class="card" shadow="never">
      <template #header>
        <div class="hd">帮助中心</div>
      </template>
      <div class="muted">这里将放置常见问题、使用指引与平台规则。</div>
    </el-card>
  </div>
</template>

<script setup lang="ts"></script>

<style scoped>
.page { max-width: 960px; margin: 0 auto; padding: 12px; }
.card { border-radius: 12px; }
.hd { font-weight: 900; }
.muted { color: #6b7280; font-size: 13px; line-height: 1.7; }
</style>
