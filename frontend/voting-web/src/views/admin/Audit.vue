<template>
  <div class="audit">
    <el-card shadow="never">
      <div class="card-head">
        <span class="title">审计追溯 · 每人每主题投了谁 (仅超级管理员可见)</span>
        <div class="filters">
          <el-select v-model="filterThemeId" placeholder="全部主题" clearable style="width:200px" @change="loadBallots">
            <el-option v-for="t in themes" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
          <el-button size="small" @click="loadBallots">查询</el-button>
        </div>
      </div>
      <el-table :data="ballots" size="small" border max-height="420">
        <el-table-column prop="accountNo" label="账号编号" width="100" />
        <el-table-column prop="themeName" label="主题" width="140" />
        <el-table-column prop="candidateName" label="投给候选人" min-width="140" />
        <el-table-column prop="round" label="轮次" width="70" />
        <el-table-column prop="voteTime" label="投票时间" width="180" />
      </el-table>
    </el-card>

    <el-card shadow="never" class="log-card">
      <div class="card-head">
        <span class="title">操作日志</span>
        <el-button size="small" @click="loadLogs">刷新</el-button>
      </div>
      <el-table :data="logs" size="small" border max-height="360">
        <el-table-column prop="operatorNo" label="操作人" width="100" />
        <el-table-column label="动作" width="130">
          <template #default="{ row }">{{ actionText(row.action) }}</template>
        </el-table-column>
        <el-table-column prop="detail" label="详情" min-width="240" />
        <el-table-column prop="createTime" label="时间" width="180" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { auditBallots, auditLogs, listThemes } from '../../api'

const ballots = ref([])
const logs = ref([])
const themes = ref([])
const filterThemeId = ref(null)

onMounted(async () => {
  themes.value = await listThemes()
  await Promise.all([loadBallots(), loadLogs()])
})

async function loadBallots() {
  ballots.value = await auditBallots(filterThemeId.value || undefined)
}
async function loadLogs() {
  logs.value = await auditLogs(filterThemeId.value || undefined)
}

const ACTION_TEXT = {
  START: '开始投票', TALLY: '统计结果', END: '结束归档', REVOKE: '撤回选票',
  REVOTE: '平票重投', GOOD_GENERATE: '生成良好主题'
}
const actionText = a => ACTION_TEXT[a] || a
</script>

<style scoped>
.card-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.card-head .title { font-size: 1.05rem; font-weight: 800; }
.filters { display: flex; gap: 8px; }
.log-card { margin-top: 16px; }
</style>
