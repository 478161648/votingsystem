<template>
  <el-container class="admin-layout">
    <el-aside width="210px" class="aside">
      <div class="brand">
        <div class="logo"><el-icon :size="18"><DataAnalysis /></el-icon></div>
        <span>年度投票系统</span>
      </div>
      <el-menu :default-active="activeMenu" router class="menu">
        <el-menu-item index="/admin/themes">
          <el-icon><EditPen /></el-icon><span>主题编辑</span>
        </el-menu-item>
        <el-menu-item index="/admin/control">
          <el-icon><VideoPlay /></el-icon><span>现场控场</span>
        </el-menu-item>
        <el-menu-item index="/admin/map">
          <el-icon><Grid /></el-icon><span>获奖地图</span>
        </el-menu-item>
        <el-menu-item index="/admin/departments">
          <el-icon><OfficeBuilding /></el-icon><span>部门管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/users">
          <el-icon><User /></el-icon><span>账号管理</span>
        </el-menu-item>
        <el-menu-item v-if="userStore.isSuperAdmin" index="/admin/audit">
          <el-icon><View /></el-icon><span>审计追溯</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="page-title">{{ pageTitle }}</div>
        <div class="header-right">
          <el-tag v-if="userStore.isSuperAdmin" type="danger" effect="dark" size="small">超级管理员</el-tag>
          <el-tag v-else type="warning" effect="dark" size="small">管理员</el-tag>
          <span class="account">{{ userStore.user?.accountNo }}</span>
          <el-button size="small" @click="logout">退出</el-button>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../../store/user'
import { disconnectWs } from '../../utils/ws'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeMenu = computed(() => route.path)
const pageTitle = computed(() => route.meta.title || '')

function logout() {
  disconnectWs()
  userStore.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<style scoped>
.admin-layout { height: 100vh; }
.aside {
  background: #1c2333;
  display: flex; flex-direction: column;
}
.brand {
  height: 60px; display: flex; align-items: center; gap: 10px;
  padding: 0 18px; color: #fff; font-weight: 800; font-size: 1.05rem;
  border-bottom: 1px solid rgba(255,255,255,.08);
}
.brand .logo {
  width: 32px; height: 32px; border-radius: 8px;
  background: linear-gradient(135deg, #2b5fd9, #4f8cff);
  display: flex; align-items: center; justify-content: center;
}
.menu { border-right: none; background: transparent; flex: 1; }
.menu :deep(.el-menu-item) { color: #aab4c8; }
.menu :deep(.el-menu-item:hover) { background: rgba(255,255,255,.06); color: #fff; }
.menu :deep(.el-menu-item.is-active) {
  background: #2b5fd9; color: #fff;
}
.header {
  background: #fff; border-bottom: 1px solid #e6eaf2;
  display: flex; align-items: center; justify-content: space-between;
  height: 60px;
}
.page-title { font-size: 1.15rem; font-weight: 800; color: #1c2333; }
.header-right { display: flex; align-items: center; gap: 12px; }
.account { font-weight: 700; color: #5b6474; }
.main { background: #eef1f6; padding: 18px; overflow: auto; }
</style>
