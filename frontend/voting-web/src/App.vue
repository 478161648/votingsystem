<template>
  <router-view />
</template>

<script setup>
import { onMounted } from 'vue'
import { connectWs } from './utils/ws'

// 应用启动时自动建立 WS 连接(若已登录)
// 修复: 刷新页面后 WS 不会重建的问题(原来只在登录页 connectWs)
onMounted(() => {
  connectWs()
})
</script>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
html, body, #app { height: 100%; }
body {
  font-family: "Microsoft YaHei", "PingFang SC", "Segoe UI", system-ui, sans-serif;
  -webkit-font-smoothing: antialiased;
  background: #eef1f6;
  color: #1c2333;
}
</style>
