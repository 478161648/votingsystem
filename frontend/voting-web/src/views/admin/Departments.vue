<template>
  <el-card shadow="never">
    <div class="card-head">
      <span class="title">部门管理 · 全系统共用一份</span>
      <div>
        <el-button type="primary" size="small" @click="addDept"><el-icon><Plus /></el-icon>新增部门</el-button>
      </div>
    </div>
    <el-table :data="departments" size="small" border>
      <el-table-column prop="sort" label="排序" width="70" />
      <el-table-column prop="name" label="部门名称" min-width="200" />
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="editDept(row)">编辑</el-button>
          <el-button size="small" link type="danger" @click="removeDept(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="form.id ? '编辑部门' : '新增部门'" width="420px">
      <el-form label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="部门名称" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" />
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
import { listDepartments, saveDepartment, updateDepartment, deleteDepartment } from '../../api'

const departments = ref([])
const dialog = ref(false)
const form = reactive({ id: null, name: '', sort: 0 })

onMounted(loadData)

async function loadData() {
  departments.value = await listDepartments()
}

function addDept() {
  Object.assign(form, { id: null, name: '', sort: departments.value.length })
  dialog.value = true
}
function editDept(row) {
  Object.assign(form, { id: row.id, name: row.name, sort: row.sort })
  dialog.value = true
}
async function submit() {
  if (!form.name) { ElMessage.warning('请输入部门名称'); return }
  if (form.id) {
    await updateDepartment(form)
  } else {
    await saveDepartment(form)
  }
  dialog.value = false
  await loadData()
  ElMessage.success('已保存')
}
async function removeDept(row) {
  await ElMessageBox.confirm(`删除部门「${row.name}」?`, '提示', { type: 'warning' })
  await deleteDepartment(row.id)
  await loadData()
  ElMessage.success('已删除')
}
</script>

<style scoped>
.card-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.card-head .title { font-size: 1.05rem; font-weight: 800; }
</style>
