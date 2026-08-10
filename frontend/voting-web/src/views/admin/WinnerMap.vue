<template>
  <el-card shadow="never" class="map-card">
    <div class="card-head">
      <span class="title">获奖地图 · 部门 × 主题交叉表</span>
      <el-button size="small" @click="loadData">刷新</el-button>
    </div>
    <el-table :data="rows" size="small" border class="map-table" height="100%">
      <el-table-column prop="name" label="部门" width="150" fixed="left">
        <template #default="{ row }">
          <b :class="{ 'zero-row': row.sum === 0 && row.name !== '合计' }">{{ row.name }}</b>
        </template>
      </el-table-column>
      <el-table-column v-for="t in themes" :key="t.id" :label="t.name" align="center" width="90">
        <template #default="{ row }">
          <template v-if="row.name === '合计'">
            <b class="sum-num">{{ row['t' + t.id] || 0 }}</b>
          </template>
          <template v-else>
            <span v-if="row['t' + t.id]" class="check">✓</span>
            <span v-else class="dash">—</span>
          </template>
        </template>
      </el-table-column>
      <el-table-column prop="sum" label="合计" align="center" width="80" fixed="right">
        <template #default="{ row }">
          <b :class="{ 'sum-total': row.name === '合计' }">{{ row.sum }}</b>
        </template>
      </el-table-column>
    </el-table>
    <div class="legend">
      <span class="zero-tag">■</span> 挂零部门 (全年无获奖)
    </div>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { winnerMap } from '../../api'

const rows = ref([])
const themes = ref([])

onMounted(loadData)

async function loadData() {
  const data = await winnerMap()
  themes.value = data.themes
  rows.value = data.rows
}
</script>

<style scoped>
/* 撑满页面底部: 卡片占满 main 剩余高度, 表格 flex 填满 */
.map-card {
  height: calc(100vh - 96px);
  display: flex;
  flex-direction: column;
}
.map-card :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.card-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.card-head .title { font-size: 1.05rem; font-weight: 800; }
.map-table { flex: 1; min-height: 0; }
.map-table :deep(.el-table__cell) { padding: 4px 6px; }
.check { color: #1a9c55; font-weight: 800; font-size: 1rem; }
.dash { color: #c9d0dc; }
.zero-row { color: #d63d3d; }
.sum-num { color: #2b5fd9; }
.sum-total { color: #2b5fd9; font-size: 1.05rem; }
.legend { margin-top: 12px; font-size: .82rem; color: #8b93a5; }
.zero-tag { color: #d63d3d; }
</style>
