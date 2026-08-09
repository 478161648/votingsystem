<template>
  <div class="login-wrap">
    <div class="login-card">
      <div class="login-logo">
        <el-icon :size="28"><UserFilled /></el-icon>
      </div>
      <div class="login-title">年度投票系统</div>
      <div class="login-sub">账号编号由管理员预置 · 初始密码统一为 <b>123</b></div>

      <el-form :model="form" :rules="rules" ref="formRef" @keyup.enter="doLogin">
        <el-form-item prop="accountNo">
          <el-input v-model="form.accountNo" placeholder="账号编号 (如 001 / 000 / 999)"
                    size="large" :prefix-icon="User" clearable />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码"
                    size="large" :prefix-icon="Lock" show-password />
        </el-form-item>
        <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="doLogin">
          进入系统
        </el-button>
      </el-form>

      <div class="login-hint">
        领导请用 001…020 登录投票<br />
        管理员 000 · 超级管理员 999
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, UserFilled } from '@element-plus/icons-vue'
import { login } from '../../api'
import { useUserStore } from '../../store/user'
import { connectWs } from '../../utils/ws'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)

const form = reactive({ accountNo: '', password: '' })
const rules = {
  accountNo: [{ required: true, message: '请输入账号编号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function doLogin() {
  await formRef.value.validate()
  loading.value = true
  try {
    const data = await login(form)
    userStore.setLogin(data.token, {
      userId: data.userId,
      accountNo: data.accountNo,
      role: data.role
    })
    ElMessage.success('登录成功')
    connectWs()
    if (data.role === 'leader') {
      router.push('/vote')
    } else {
      router.push('/admin/themes')
    }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrap {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: linear-gradient(135deg, #e8effc 0%, #eef1f6 60%, #f5f7fb 100%);
}
.login-card {
  width: 100%;
  max-width: 430px;
  padding: 40px 40px 30px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 6px 30px -12px rgba(28, 35, 51, 0.25);
  text-align: center;
  border: 1px solid #e6eaf2;
}
.login-logo {
  width: 60px; height: 60px; border-radius: 16px; margin: 0 auto 16px;
  background: linear-gradient(135deg, #2b5fd9, #1d46b8);
  display: flex; align-items: center; justify-content: center;
  color: #fff; box-shadow: 0 6px 18px -6px rgba(43, 95, 217, 0.6);
}
.login-title { font-size: 1.5rem; font-weight: 800; margin-bottom: 6px; color: #1c2333; }
.login-sub { font-size: 0.88rem; color: #5b6474; margin-bottom: 28px; }
.login-btn { width: 100%; margin-top: 6px; height: 46px; font-size: 1.05rem; font-weight: 700; }
.login-hint { font-size: 0.8rem; color: #8b93a5; margin-top: 20px; line-height: 1.8; }
</style>
