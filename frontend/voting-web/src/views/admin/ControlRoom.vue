<template>
  <div class="control-room">
    <el-card shadow="never" class="theme-bar">
      <div class="bar-inner">
        <div class="sel">
          <span class="label">当前主题</span>
          <el-select v-model="themeId" style="width: 260px" @change="loadAll">
            <el-option v-for="t in themes" :key="t.id" :label="t.name" :value="t.id">
              <span>{{ t.name }}</span>
              <el-tag size="small" :type="statusTag(t.status)" style="margin-left:8px">{{ statusText(t.status) }}</el-tag>
            </el-option>
          </el-select>
          <el-button size="small" :loading="refreshing" @click="refreshPage" style="margin-left:8px">
            <el-icon><Refresh /></el-icon>刷新
          </el-button>
        </div>
        <div class="status" v-if="theme">
          <el-tag size="large" :type="statusTag(theme.status)">{{ statusText(theme.status) }}</el-tag>
          <span class="tip" v-if="theme.hasTie">· 存在平票待重投</span>
        </div>
        <div class="actions">
          <el-button type="primary" :disabled="!btnEnabled('start')" @click="act('start')">开始投票</el-button>
          <el-button type="success" :disabled="!btnEnabled('tally')" @click="act('tally')">统计结果</el-button>
          <el-button type="warning" :disabled="!btnEnabled('revote')" @click="act('revote')">平票重投</el-button>
          <el-button type="danger" plain :disabled="!btnEnabled('end')" @click="act('end')">结束归档</el-button>
          <el-button :disabled="!canExportStats" @click="exportStats">
            <el-icon><Download /></el-icon>导出统计
          </el-button>
          <el-button type="danger" @click="clearData">
            <el-icon><Delete /></el-icon>清除数据
          </el-button>
          <el-button @click="openScreen">
            <el-icon><FullScreen /></el-icon>投屏
          </el-button>
        </div>
      </div>
    </el-card>

    <el-row :gutter="14">
      <!-- 左: 实时排名 -->
      <el-col :span="15">
        <el-card shadow="never">
          <div class="card-head">
            <span class="title">实时排名</span>
            <span class="secret-tip" v-if="isSecret">🔒 投票中 · 结果保密</span>
            <span class="secret-tip open" v-else>📊 已开票</span>
          </div>
          <div v-if="ranking.length === 0" class="empty">暂无候选</div>
          <!-- 按分类分区展示, 与投票端布局一致 -->
          <div v-for="(sec, si) in rankSections" :key="si" class="cat-section"
               :class="{ 'pick-revote': sec.isRevote }">
            <div class="cat-head" :class="{ 'revote-head': sec.isRevote }">
              <span class="cat-no">{{ cn[si] }}</span>
              <span class="cat-name">{{ sec.name }}</span>
              <span class="cat-quota-tag">{{ sec.quota != null ? '名额 ' + sec.quota + ' 名' : '候选 ' + sec.list.length + ' 名' }}</span>
            </div>
            <div class="rank-grid">
              <div v-for="(c, i) in sec.list" :key="c.id" class="rank-card"
                   :class="{ win: c.isWinner, tie: c.tie }">
                <div class="rc-no" :class="{ gold: c.isWinner }">
                  {{ c.isWinner ? '✓' : (c.rank || '—') }}
                </div>
                <div class="rc-info">
                  <div class="rc-name">
                    {{ c.name }}
                    <el-tag v-if="c.isWinner" size="small" type="success">当选</el-tag>
                    <el-tag v-else-if="c.tie" size="small" type="danger">平票</el-tag>
                  </div>
                  <div class="rc-sub">{{ c.categoryName || '' }} {{ c.departmentName ? '· ' + c.departmentName : '' }}</div>
                </div>
                <div class="rc-votes">
                  <template v-if="!isSecret">
                    <div class="v">{{ c.votes ?? 0 }}</div>
                    <div class="u">票</div>
                  </template>
                  <template v-else>
                    <div class="v secret">—</div>
                    <div class="u">保密</div>
                  </template>
                </div>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 右: 参与统计 + 日志 -->
      <el-col :span="9">
        <el-card shadow="never" class="part-card">
          <div class="card-head">
            <span class="title">参与统计 (在线领导)</span>
            <span class="tip">{{ participation.length }} 人在线</span>
          </div>
          <div v-if="participation.length === 0" class="empty">暂无在线领导 (领导登录投票端后显示)</div>
          <div class="part-list">
            <div v-for="p in participation" :key="p.userId" class="part-item">
              <span class="no">{{ p.accountNo }}</span>
              <el-tag v-if="p.voted" type="success" size="small">已投</el-tag>
              <el-tag v-else type="danger" size="small">未投</el-tag>
              <el-button v-if="!p.voted" size="small" link type="warning" @click="revokeUser(p.userId)">
                撤回
              </el-button>
              <el-button v-if="p.voted" size="small" link type="danger" @click="revokeUser(p.userId)">
                撤回该票
              </el-button>
            </div>
          </div>
        </el-card>

        <el-card shadow="never" class="log-card">
          <div class="card-head">
            <span class="title">操作日志</span>
            <el-button size="small" link @click="loadLogs">刷新</el-button>
          </div>
          <el-timeline class="log-list">
            <el-timeline-item v-for="l in logs" :key="l.id" :timestamp="l.createTime" size="small">
              <span class="log-action">{{ actionText(l.action) }}</span>
              <span class="log-detail">{{ l.detail }}</span>
              <span class="log-op">{{ l.operatorNo }}</span>
            </el-timeline-item>
          </el-timeline>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listThemes, getTheme, controlStart, controlTally, controlEnd,
  controlRevote, controlRevoke, controlParticipation, controlRanking, controlLogs, controlClear
} from '../../api'
import { wsClient } from '../../utils/ws'

const themes = ref([])
const themeId = ref(null)
const theme = ref(null)
const ranking = ref([])
const participation = ref([])
const logs = ref([])
const refreshing = ref(false)
let unsub = null

const STATUS = {
  DRAFT: { text: '未开始', tag: 'info' },
  OPEN: { text: '投票中', tag: 'success' },
  TALLIED: { text: '已统计', tag: 'warning' },
  TIE_REVOTE: { text: '重投中', tag: 'warning' },
  ARCHIVED: { text: '已归档', tag: 'info' }
}
const statusText = s => STATUS[s]?.text || s
const statusTag = s => STATUS[s]?.tag || 'info'

const isSecret = computed(() => theme.value && (theme.value.status === 'OPEN' || theme.value.status === 'TIE_REVOTE'))

/** 得票统计导出: 仅已统计(TALLIED)或已归档(ARCHIVED)可用 */
const canExportStats = computed(() => theme.value && ['TALLIED', 'ARCHIVED'].includes(theme.value.status))

const cn = ['一', '二', '三', '四', '五', '六', '七', '八']

/** 按分类分区的排名区段(与投票端布局一致); 无分类时单池; 重投轮高亮并列分类 */
const rankSections = computed(() => {
  const cats = theme.value?.categories || []
  const isRevote = theme.value?.status === 'TIE_REVOTE'
  if (!cats.length) {
    // 单池: 全部候选一个分区
    return [{
      name: '全部候选项',
      quota: theme.value?.totalQuota ?? null,
      isRevote: false,
      list: ranking.value
    }]
  }
  return cats.map(cat => {
    const list = ranking.value.filter(c => c.categoryId === cat.id)
    const hasTie = isRevote && list.some(c => c.tie)
    return {
      name: cat.name,
      quota: cat.quota ?? null,
      isRevote: hasTie,
      list
    }
  })
})

const ACTIONS = {
  start: ['DRAFT'],
  tally: ['OPEN', 'TIE_REVOTE'],
  revote: ['TALLIED'],
  end: ['TALLIED']
}
function btnEnabled(action) {
  if (!theme.value) return false
  return ACTIONS[action].includes(theme.value.status)
}

onMounted(async () => {
  themes.value = await listThemes()
  if (themes.value.length) {
    themeId.value = themes.value[0].id
    await loadAll()
  }
  // 订阅 WS 主题更新: 归档生成良好主题等动作发生后, 自动刷新主题下拉列表, 无需手动刷新页面
  unsub = wsClient.subscribe(msg => {
    if (msg.type === 'themeUpdate') {
      refreshThemes()
    }
  })
})

onUnmounted(() => { if (unsub) unsub() })

/** 刷新主题列表 + 当前主题数据; 当前主题被删/归档后自动切到第一个可操作主题 */
async function refreshThemes() {
  themes.value = await listThemes()
  if (!themeId.value || !themes.value.some(t => t.id === themeId.value)) {
    if (themes.value.length) {
      themeId.value = themes.value[0].id
    } else {
      theme.value = null
      return
    }
  }
  await loadAll()
}

/** 手动刷新: 强制拉取最新状态(主题列表+当前主题排名/参与/日志), 保持当前主题不变 */
async function refreshPage() {
  refreshing.value = true
  try {
    const curId = themeId.value
    themes.value = await listThemes()
    // 当前主题仍存在则保持; 若被删除则回退第一个
    if (curId && themes.value.some(t => t.id === curId)) {
      themeId.value = curId
    } else if (themes.value.length) {
      themeId.value = themes.value[0].id
    } else {
      theme.value = null
      return
    }
    await loadAll()
    ElMessage.success('已刷新')
  } finally {
    refreshing.value = false
  }
}

async function loadAll() {
  if (!themeId.value) return
  theme.value = await getTheme(themeId.value)
  await Promise.all([loadRanking(), loadParticipation(), loadLogs()])
}

async function loadRanking() { ranking.value = await controlRanking(themeId.value) }
async function loadParticipation() { participation.value = await controlParticipation(themeId.value) }
async function loadLogs() { logs.value = await controlLogs(themeId.value) }

async function act(action) {
  const names = { start: '开始投票', tally: '统计结果', revote: '平票重投', end: '结束归档' }
  if (action === 'tally') {
    const notVoted = participation.value.filter(p => !p.voted).length
    if (notVoted > 0) {
      await ElMessageBox.confirm(
        `当前有 ${notVoted} 位在线领导未投票, 确定统计吗? (可能导致票数不全)`, '确认统计',
        { type: 'warning' }
      )
    }
  }
  if (action === 'end') {
    await ElMessageBox.confirm('结束归档后结果固化, 优秀级主题将自动生成良好主题。确定继续?', '确认归档', { type: 'warning' })
  }
  try {
    await { start: controlStart, tally: controlTally, revote: controlRevote, end: controlEnd }[action](themeId.value)
    ElMessage.success(names[action] + '成功')
    await loadAll()
  } catch (e) { /* 错误已提示 */ }
}

async function clearData() {
  await ElMessageBox.confirm(
    `确定清除主题「${theme.value?.name}」的全部数据吗?\n\n` +
    '将删除: 所有选票、当选状态、轮次、操作日志, 并回到「未开始」状态。\n' +
    '候选名单会保留(可配合 Excel 覆盖导入重新开始)。此操作不可恢复!',
    '清除数据', { type: 'warning', confirmButtonText: '确认清除', confirmButtonClass: 'el-button--danger' }
  )
  await controlClear(themeId.value)
  ElMessage.success('数据已清除, 主题回到未开始状态')
  await loadAll()
}

async function revokeUser(userId) {
  await ElMessageBox.confirm('确定撤回该领导的当前轮选票? (该领导可重新投票)', '撤回选票', { type: 'warning' })
  await controlRevoke(themeId.value, userId)
  ElMessage.success('已撤回')
  await loadAll()
}

function openScreen() {
  const url = `/screen/${themeId.value}`
  window.open(url, '_blank', 'width=1400,height=900')
}

/** 导出得票统计 Excel (每轮每候选得票) */
function exportStats() {
  const token = localStorage.getItem('voting_token')
  const url = `/api/control/${themeId.value}/export-stats`
  const xhr = new XMLHttpRequest()
  xhr.open('GET', url, true)
  xhr.setRequestHeader('Authorization', `Bearer ${token}`)
  xhr.responseType = 'blob'
  xhr.onload = () => {
    if (xhr.status !== 200) {
      ElMessage.error('导出失败')
      return
    }
    const blob = xhr.response
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = `${theme.value?.name || '主题'}-得票统计.xlsx`
    a.click()
    URL.revokeObjectURL(a.href)
    ElMessage.success('已导出')
  }
  xhr.onerror = () => ElMessage.error('导出失败')
  xhr.send()
}

const ACTION_TEXT = {
  START: '开始投票', TALLY: '统计结果', END: '结束归档', REVOKE: '撤回选票',
  REVOTE: '平票重投', GOOD_GENERATE: '生成良好主题'
}
const actionText = a => ACTION_TEXT[a] || a
</script>

<style scoped>
.theme-bar .bar-inner { display: flex; align-items: center; gap: 18px; flex-wrap: wrap; }
.theme-bar .label { font-weight: 700; color: #5b6474; margin-right: 6px; }
.theme-bar .status { display: flex; align-items: center; gap: 6px; }
.theme-bar .actions { margin-left: auto; display: flex; gap: 8px; flex-wrap: wrap; }
.theme-bar .tip { font-size: .82rem; color: #d63d3d; font-weight: 700; }
.card-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.card-head .title { font-size: 1.02rem; font-weight: 800; }
.secret-tip { font-size: .82rem; color: #8b93a5; font-weight: 700; }
.secret-tip.open { color: #1a9c55; }
.rank-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  gap: 8px; padding: 11px 13px;
}
/* 分类分区(与投票端一致) */
.cat-section {
  background: #fff; border: 2px solid #e6eaf2; border-radius: 12px; overflow: hidden;
  margin-bottom: 10px;
}
.cat-section.pick-revote { border-color: #e07b00; }
.cat-head {
  display: flex; align-items: center; gap: 12px; padding: 9px 16px;
  background: linear-gradient(90deg, #e8effc, transparent); border-bottom: 1.5px solid #e6eaf2;
}
.cat-head.revote-head { background: linear-gradient(90deg, #fff4e5, transparent); }
.cat-no {
  width: 28px; height: 28px; border-radius: 8px; background: #2b5fd9; color: #fff; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center; font-size: .92rem; font-weight: 800;
}
.pick-revote .cat-no { background: #e07b00; }
.cat-name { font-size: 1rem; font-weight: 800; }
.cat-quota-tag { font-size: .76rem; font-weight: 800; padding: 3px 11px; border-radius: 999px; background: #eef1f5; color: #6a7383; }
.rank-card {
  display: flex; align-items: center; gap: 10px; padding: 10px 12px;
  border: 1.5px solid #e6eaf2; border-radius: 10px; background: #fff;
}
.rank-card.win { border-color: #1a9c55; background: linear-gradient(90deg, #e8f7ee, #fff); }
.rank-card.tie { border-color: #e07b00; background: #fff4e5; }
.rc-no {
  width: 32px; height: 32px; border-radius: 50%; background: #eef1f5;
  display: flex; align-items: center; justify-content: center;
  font-weight: 800; color: #5b6474; flex-shrink: 0;
}
.rc-no.gold { background: #1a9c55; color: #fff; }
.rc-info { flex: 1; min-width: 0; }
.rc-name { font-size: .95rem; font-weight: 800; display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.rc-sub { font-size: .74rem; color: #8b93a5; margin-top: 2px; }
.rc-votes { text-align: center; min-width: 52px; }
.rc-votes .v { font-size: 1.15rem; font-weight: 800; }
.rc-votes .v.secret { color: #8b93a5; }
.rc-votes .u { font-size: .7rem; color: #8b93a5; }
.empty { color: #8b93a5; text-align: center; padding: 30px 0; font-size: .85rem; }
.part-list { display: flex; flex-direction: column; gap: 6px; max-height: 260px; overflow: auto; }
.part-item {
  display: flex; align-items: center; gap: 8px; padding: 6px 10px;
  border: 1px solid #e6eaf2; border-radius: 8px;
}
.part-item .no { font-weight: 800; width: 50px; }
.part-item .el-button { margin-left: auto; }
.log-card { margin-top: 14px; }
.log-list { max-height: 220px; overflow: auto; padding-left: 4px; }
.log-action { font-weight: 800; font-size: .85rem; }
.log-detail { font-size: .8rem; color: #5b6474; margin-left: 8px; }
.log-op { font-size: .74rem; color: #8b93a5; margin-left: 8px; }
</style>
