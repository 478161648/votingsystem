<template>
  <el-card shadow="never">
    <div class="card-head">
      <span class="title">账号管理 · 预置编号, 初始密码 123 (不允许修改)</span>
      <div>
        <el-button type="primary" size="small" @click="addUser"><el-icon><Plus /></el-icon>新增账号</el-button>
      </div>
    </div>
    <el-table :data="users" size="small" border>
      <el-table-column prop="accountNo" label="账号编号" width="110" />
      <el-table-column label="角色" width="130">
        <template #default="{ row }">
          <el-tag size="small" :type="row.role === 'super_admin' ? 'danger' : row.role === 'admin' ? 'warning' : 'success'">
            {{ roleText(row.role) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="realName" label="预留姓名" width="140" />
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="editUser(row)">编辑</el-button>
          <el-button size="small" link type="danger" @click="removeUser(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="form.id ? '编辑账号' : '新增账号'" width="420px">
      <el-form label-width="90px">
        <el-form-item label="账号编号" required>
          <el-input v-model="form.accountNo" placeholder="如: 001 / 021" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.role" style="width:100%">
            <el-option label="领导 (leader)" value="leader" />
            <el-option label="管理员 (admin)" value="admin" />
            <el-option label="超级管理员 (super_admin)" value="super_admin" />
          </el-select>
        </el-form-item>
        <el-form-item label="预留姓名">
          <el-input v-model="form.realName" placeholder="可选, 界面不显示" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listUsers, saveUser, updateUser, deleteUser } from '../../api'

const users = ref([])
const dialog = ref(false)
const form = reactive({ id: null, accountNo: '', role: 'leader', realName: '' })

const roleText = r => ({ super_admin: '超级管理员', admin: '管理员', leader: '领导' }[r] || r)

onMounted(loadData)

async function loadData() {
  users.value = await listUsers()
}

function addUser() {
  Object.assign(form, { id: null, accountNo: '', role: 'leader', realName: '' })
  dialog.value = true
}
function editUser(row) {
  Object.assign(form, { id: row.id, accountNo: row.accountNo, role: row.role, realName: row.realName })
  dialog.value = true
}
async function submit() {
  if (!form.accountNo) { ElMessage.warning('请输入账号编号'); return }
  if (form.id) {
    await updateUser(form)
  } else {
    await saveUser(form)
  }
  dialog.value = false
  await loadData()
  ElMessage.success('已保存, 初始密码 123')
}
async function removeUser(row) {
  await ElMessageBox.confirm(`删除账号「${row.accountNo}」?`, '提示', { type: 'warning' })
  await deleteUser(row.id)
  await loadData()
  ElMessage.success('已删除')
}
</script>

<style scoped>
.card-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.card-head .title { font-size: 1.05rem; font-weight: 800; }
</style>
