<template>
  <div class="vote-page">
    <!-- 登录态检查 -->
    <template v-if="!userStore.isLogin">
      <div class="login-guard">
        <el-button type="primary" size="large" @click="router.push('/login')">前往登录</el-button>
      </div>
    </template>

    <template v-else>
      <!-- 顶部栏 -->
      <header class="topbar">
        <div class="brand">
          <div class="logo"><el-icon :size="18"><DataAnalysis /></el-icon></div>
          <span>年度投票系统</span>
        </div>
        <div class="theme-switch" v-if="themes.length > 1">
          <span>当前主题</span>
          <el-select v-model="themeId" size="small" @change="loadTheme" style="width: 260px">
            <el-option v-for="t in themes" :key="t.id" :label="t.name + ' · ' + statusText(t.status)" :value="t.id" />
          </el-select>
        </div>
        <div class="user-chip">
          <span class="dot"></span>
          <span>{{ userStore.user?.accountNo }}</span>
          <el-button size="small" text type="danger" @click="logout">退出</el-button>
        </div>
      </header>

      <main class="main" v-if="themeData">
        <!-- 主题行 -->
        <div class="theme-card">
          <span class="theme-name">{{ theme.name }}</span>
          <el-tag :type="statusTag(theme.status)" size="small">{{ statusText(theme.status) }}</el-tag>
          <span class="theme-tip">{{ themeTip }}</span>
          <div class="theme-progress" v-if="voting">
            <span>参与</span>
            <el-progress :percentage="progress" :stroke-width="9" :show-text="false" style="width:110px" />
            <span>{{ votedCount }}/{{ onlineCount }}</span>
          </div>
        </div>

        <div class="ctrl-grid">
          <!-- 左列 -->
          <div class="left-col">
            <!-- 顶部提醒条: 状态切换仅换文案与配色 -->
            <div class="vote-hint" :class="{ warn: phase === 'REVOTE' }">
              <el-icon><Lock v-if="phase === 'OPEN'" /><RefreshRight v-else-if="phase === 'REVOTE'" /><DocumentChecked v-else /></el-icon>
              <span>{{ hintText }}</span>
            </div>

            <!-- 投票中(含已投)/DRAFT预览: 候选分区 (已投后只读不可勾选) -->
            <div v-if="showCands" id="cat-sections">
              <div v-for="(cat, ci) in visibleCats" :key="cat.id" class="cat-section"
                   :class="{ 'pick-revote': isRevoteCat(ci), pick: !isRevoteCat(ci) }">
                <div class="cat-head" :class="{ 'revote-head': isRevoteCat(ci) }">
                  <span class="cat-no">{{ cn[ci] }}</span>
                  <span class="cat-name">{{ cat.name }}</span>
                  <span class="cat-quota-tag">{{ isRevoteCat(ci) ? '重投名额' : '名额' }} {{ catQuota(cat) }} 名</span>
                  <span class="cat-count">
                    已选 <span class="c-done">{{ catSelected(cat) }}</span> ·
                    待选 <span :class="catLeft(cat) <= 0 ? 'c-ok' : 'c-left'">{{ Math.max(catLeft(cat), 0) }}</span>
                    <span v-if="catLeft(cat) <= 0" class="c-done">· 已完成</span>
                  </span>
                </div>
                <div class="cand-list">
                  <div v-for="c in catCands(cat)" :key="c.id" class="cand-row"
                       :class="{ selected: isSelected(c.id), disabled: !selectable(c) }"
                       @click="selectable(c) && toggleSel(c)">
                    <span class="cand-check"><el-icon :size="13"><Check /></el-icon></span>
                    <span class="cand-info">
                      <span class="cand-name">
                        {{ c.name }}
                        <span v-if="c.isWinner" class="cat-tag locked">✓ 已当选</span>
                        <span v-else-if="c.tie && phase === 'REVOTE'" class="cat-tag revote">重投中</span>
                      </span>
                      <span class="cand-dept">{{ c.departmentName || '' }}</span>
                      <div v-if="fields.length" class="cand-fields">
                        <div v-for="(f, fi) in fields" :key="f.fieldLabel" class="cf" :class="{ wide: isWideField(fi) }">
                          <span class="cf-k">{{ f.fieldLabel }}</span>
                          <span class="cf-v">{{ fieldValue(c, f) }}</span>
                        </div>
                      </div>
                    </span>
                  </div>
                </div>
              </div>
            </div>

            <!-- 结果视图 (统计后同位置切换) -->
            <div v-else class="result-view">
              <div class="result-head">
                <span class="result-title">投票结果 · {{ theme.name }}</span>
                <el-tag :type="theme.status === 'ARCHIVED' ? 'info' : 'warning'">{{ statusText(theme.status) }}</el-tag>
              </div>
              <div class="result-rank">
                <div v-for="(c, idx) in resultList" :key="c.id" class="rr" :class="{ win: c.isWinner }">
                  <span class="rr-no">{{ idx + 1 }}</span>
                  <span class="rr-info">
                    <span class="rr-name">{{ c.name }}
                      <span v-if="c.isWinner" class="win-tag">✓ 当选</span>
                    </span>
                    <span class="rr-dept">{{ c.categoryName }} {{ c.departmentName ? '· ' + c.departmentName : '' }}</span>
                  </span>
                  <span class="rr-votes">{{ c.votes ?? 0 }}<span class="u">票</span></span>
                </div>
              </div>
            </div>
          </div>

          <!-- 右列: 常驻信息栏 -->
          <div class="side-col">
            <div class="card info-card">
              <div class="info-title">我的选择</div>
              <div class="sum-grid">
                <div class="sum-item"><div class="sum-k">总名额</div><div class="sum-v" style="color:#2b5fd9">{{ totalQuota }}</div></div>
                <div class="sum-item"><div class="sum-k">已选</div><div class="sum-v" style="color:#2b5fd9">{{ selectedCount }}</div></div>
                <div class="sum-item"><div class="sum-k">待选</div><div class="sum-v" style="color:#e07b00">{{ Math.max(totalQuota - selectedCount, 0) }}</div></div>
              </div>
            </div>

            <div class="card info-card">
              <div class="info-title">已当选名单</div>
              <div class="winner-list">
                <template v-if="winners.length">
                  <div v-for="w in winners" :key="w.id" class="winner-chip">✓ {{ w.name }}</div>
                </template>
                <div v-else class="empty-line">{{ phase === 'REVOTE' ? '重投中, 等待统计' : '尚未统计，暂无当选' }}</div>
              </div>
            </div>

            <div class="card info-card">
              <div class="info-title">空缺名额</div>
              <div class="quota-line" v-html="vacancyText"></div>
            </div>

            <div class="card submit-box">
              <el-button type="primary" size="large" class="submit-btn" :disabled="!canSubmit" @click="submit">
                提交投票
              </el-button>
              <div class="submit-tip">{{ submitTip }}</div>
            </div>
          </div>
        </div>
      </main>

      <el-empty v-else description="当前没有可参与的主题" style="margin-top: 80px" />
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { voteThemes, voteThemeDetail, voteRevoteInfo, submitVote } from '../../api'
import { useUserStore } from '../../store/user'
import { wsClient } from '../../utils/ws'

const router = useRouter()
const userStore = useUserStore()

const themes = ref([])
const themeId = ref(null)
const theme = ref(null)
const themeData = ref(null)
const candidates = ref([])
const categories = ref([])
const fields = ref([])
const selected = ref(new Set())
const voted = ref(false)
const phase = ref('OPEN')
/** 上一次阶段(用于检测"刚进入重投轮"以清空上一轮勾选) */
const lastPhase = ref('')
const winners = ref([])
const vacancy = ref(0)
const revoteTieIds = ref([])
/** 分类级空缺: {categoryId -> 该分类空缺数} */
const catVacancyMap = ref({})
const onlineCount = ref(0)
const votedCount = ref(0)
let unsub = null

const cn = ['一', '二', '三', '四', '五', '六', '七', '八']
const voting = computed(() => phase.value === 'OPEN' || phase.value === 'REVOTE')
/** 候选分区显示条件: 投票中(含已投, 投后仅只读) 或 DRAFT 预览(只读) */
const showCands = computed(() => voting.value || phase.value === 'DRAFT')

const isRevoteCat = ci => {
  const cat = visibleCats.value[ci]
  if (!cat) return false
  if (phase.value !== 'REVOTE') return false
  // 无分类虚拟分区: 并列候选即为无分类候选
  if (cat.id === '__all__') {
    return revoteTieIds.value.some(id =>
      !candidates.value.find(c => c.id === id)?.categoryId)
  }
  return revoteTieIds.value.some(id =>
    candidates.value.find(c => c.id === id)?.categoryId === cat.id)
}

const visibleCats = computed(() => {
  // 无分类主题(单池): 生成「全部候选项」虚拟分区, 否则候选项无法渲染
  const singlePool = {
    id: '__all__',
    name: '全部候选项',
    quota: phase.value === 'REVOTE' && !voted.value
      ? vacancy.value
      : (theme.value?.totalQuota || 0)
  }
  if (phase.value === 'REVOTE' && !voted.value) {
    // 重投只显示并列候选所在分类
    if (!categories.value.length) {
      return revoteTieIds.value.some(id => !candidates.value.find(c => c.id === id)?.categoryId)
        ? [singlePool] : []
    }
    const tieCatIds = new Set(revoteTieIds.value.map(id => candidates.value.find(c => c.id === id)?.categoryId))
    return categories.value.filter(c => tieCatIds.has(c.id))
  }
  if (!categories.value.length) {
    return [singlePool]
  }
  return categories.value
})

const totalQuota = computed(() => {
  if (phase.value === 'REVOTE' && !voted.value) return vacancy.value
  return theme.value?.totalQuota || 0
})

const selectedCount = computed(() => selected.value.size)

const hintText = computed(() => {
  if (phase.value === 'DRAFT') return '投票尚未开始 · 可预览候选名单，等待管理员发起投票'
  if (voting.value && voted.value && phase.value === 'REVOTE') return '您已完成重投 · 等待管理员统计结果'
  if (voting.value && voted.value) return '您已完成投票 · 等待管理员统计结果'
  if (phase.value === 'OPEN') return '投票进行中，得票结果保密 · 请独立作出您的选择'
  if (phase.value === 'REVOTE') return '平票重投中 · 请为并列候选重新投票'
  if (phase.value === 'TALLIED') return '本主题已统计完成，请查看结果'
  return '本主题已归档，请查看结果'
})

const submitTip = computed(() => {
  if (phase.value === 'DRAFT') return '投票未开始，等待管理员发起'
  if (phase.value === 'REVOTE' && !voted.value) {
    const tieCnt = revoteTieIds.value.length
    if (tieCnt === 1) return '请为并列候选投出 1 票（空缺 1 名），或直接提交弃权'
    return `请从并列候选中选择（最多 ${vacancy.value} 名），或直接提交弃权`
  }
  if (phase.value === 'TALLIED' || phase.value === 'ARCHIVED') return '投票已结束'
  if (voted.value) return '您已完成投票'
  return '可直接提交（可弃权，或选择部分/全部名额）'
})

const canSubmit = computed(() => {
  if (!voting.value || voted.value) return false
  // 支持弃权/不足额提交: 0 选也可提交, 上限由 toggleSel/后端控制
  return true
})

const themeTip = computed(() => {
  if (!categories.value.length) return `共 ${theme.value?.totalQuota || 0} 个名额`
  return categories.value.map(c => `${c.name} ${c.quota} 名`).join(' + ')
})

const progress = computed(() => {
  if (!onlineCount.value) return 0
  return Math.round(votedCount.value / onlineCount.value * 100)
})

const resultList = computed(() => {
  return [...candidates.value].sort((a, b) => (b.votes || 0) - (a.votes || 0) || (a.rank || 99) - (b.rank || 99))
})

const vacancyText = computed(() => {
  if (phase.value === 'REVOTE' && !voted.value) return `当前空缺 <b>${vacancy.value}</b> 名<br>（并列候选所在分类）`
  if (phase.value === 'REVOTE' && voted.value) return `当前空缺 <b>${vacancy.value}</b> 名<br>· 等待管理员统计`
  if (phase.value === 'TALLIED' || phase.value === 'ARCHIVED') return '已全部选定（无空缺）'
  return '等待统计后显示'
})

onMounted(async () => {
  await refreshThemes()
  // 订阅 WS: 管理员控场动作(开始/统计/归档/生成良好主题)自动推送到投票端
  unsub = wsClient.subscribe(msg => {
    if (msg.type === 'themeUpdate') {
      refreshThemes()
    }
  })
})

onUnmounted(() => { if (unsub) unsub() })

watch(themeId, () => {
  selected.value = new Set() // 切换主题时清空旧选择
  lastPhase.value = ''       // 重置阶段记录, 使进入重投轮时能正确清空
  loadTheme()
})

/** 刷新主题下拉列表; 若当前主题不在列表(如刚归档/新生成), 自动切到第一个可投/可看主题 */
async function refreshThemes() {
  themes.value = await voteThemes()
  if (!themeId.value || !themes.value.some(t => t.id === themeId.value)) {
    if (themes.value.length) {
      themeId.value = themes.value[0].id
    } else {
      theme.value = null
      themeData.value = null
      return
    }
  }
  await loadTheme()
}

async function loadTheme() {
  if (!themeId.value) return
  themeData.value = await voteThemeDetail(themeId.value)
  theme.value = themeData.value.theme
  categories.value = themeData.value.categories || []
  fields.value = themeData.value.customFields || []
  candidates.value = themeData.value.candidates || []
  voted.value = themeData.value.myVoted
  phase.value = theme.value.status === 'DRAFT' ? 'DRAFT'
    : theme.value.status === 'OPEN' ? 'OPEN'
    : theme.value.status === 'TIE_REVOTE' ? 'REVOTE'
    : theme.value.status === 'ARCHIVED' ? 'ARCHIVED' : 'TALLIED'
  // selected 清理策略:
  // - 切换主题: watch(themeId) 清空
  // - 刚进入平票重投轮(上一阶段非 REVOTE): 清空上一轮残留勾选(如第一轮投的 A、C), 否则重投会被误判为已选超限
  // - 刚回到 DRAFT(清除数据): 清空勾选, 数据已清零从零开始
  // - 同阶段内 WS 刷新(仍 REVOTE / 仍 OPEN / 仍 DRAFT): 保留用户勾选, 避免投票前勾选被意外清掉导致空票提交
  if ((phase.value === 'REVOTE' || phase.value === 'DRAFT') && lastPhase.value !== phase.value) {
    selected.value = new Set()
  }
  lastPhase.value = phase.value

  if (phase.value === 'REVOTE') {
    try {
      const info = await voteRevoteInfo(themeId.value)
      winners.value = info.winners || []
      vacancy.value = info.vacancy || 0
      revoteTieIds.value = (info.tieCandidates || []).map(c => c.id)
      // 分类级空缺: {categoryId -> 该分类空缺数}
      catVacancyMap.value = {}
      for (const cv of (info.catVacancies || [])) {
        catVacancyMap.value[cv.categoryId] = cv.vacancy
      }
    } catch (e) {
      winners.value = []
      vacancy.value = 0
      revoteTieIds.value = []
      catVacancyMap.value = {}
    }
  } else {
    winners.value = candidates.value.filter(c => c.isWinner)
    revoteTieIds.value = []
    vacancy.value = 0
    catVacancyMap.value = {}
  }
}

function catCands(cat) {
  if (cat.id === '__all__') {
    return candidates.value.filter(c => !c.categoryId)
  }
  return candidates.value.filter(c => c.categoryId === cat.id)
}

function selectable(c) {
  if (!voting.value || voted.value) return false
  if (phase.value === 'REVOTE') {
    // 重投: 已当选不可选, 只可选并列候选
    if (c.isWinner) return false
    return revoteTieIds.value.includes(c.id)
  }
  return true
}

function catQuota(cat) {
  if (phase.value === 'REVOTE' && !voted.value) {
    if (cat.id === '__all__') return vacancy.value
    // 该分类是否存在并列候选: 重投限额 = 该分类空缺数(分类名额 - 该分类已当选), 而非原分类名额
    const hasTie = revoteTieIds.value.some(id =>
      candidates.value.find(c => c.id === id)?.categoryId === cat.id)
    if (hasTie) {
      const cv = catVacancyMap.value[cat.id]
      return cv != null ? cv : vacancy.value
    }
  }
  return cat.quota
}

function catSelected(cat) {
  return catCands(cat).filter(c => isSelected(c.id)).length
}

function catLeft(cat) {
  return catQuota(cat) - catSelected(cat)
}

function isSelected(id) { return selected.value.has(id) }

function toggleSel(c) {
  const cat = categories.value.find(x => x.id === c.categoryId)
  if (selected.value.has(c.id)) {
    selected.value.delete(c.id)
  } else {
    if (phase.value === 'REVOTE' && !voted.value) {
      // 重投: 全局已选不能超过空缺总数; 分类已选不能超过该分类空缺数
      if (selected.value.size >= vacancy.value) {
        ElMessage.warning(`重投最多选择 ${vacancy.value} 名(已当选名额已扣除)`)
        return
      }
      const quota = cat ? catQuota(cat) : vacancy.value
      const inCat = cat ? catCands(cat).filter(x => selected.value.has(x.id)).length : selected.value.size
      if (inCat >= quota) {
        ElMessage.warning(cat ? `该分类重投最多选 ${quota} 个` : `本主题最多选 ${quota} 个`)
        return
      }
    } else if (cat) {
      const inCat = catCands(cat).filter(x => selected.value.has(x.id)).length
      if (inCat >= catQuota(cat)) {
        ElMessage.warning(`该分类最多选 ${catQuota(cat)} 个`)
        return
      }
    } else {
      // 无分类主题(单池): 按总名额限制
      const quota = theme.value?.totalQuota || 0
      if (selected.value.size >= quota) {
        ElMessage.warning(`本主题最多选 ${quota} 个`)
        return
      }
    }
    selected.value.add(c.id)
  }
}

function isWideField(fi) {
  const f = fields.value[fi]
  return f?.fieldType === 'TEXTAREA' || f?.fieldLabel === '备注'
}

function fieldValue(c, f) {
  const v = c.extraMap?.[f.fieldLabel]
  return v !== undefined && v !== null && v !== '' ? v : '—'
}

async function submit() {
  const ids = [...selected.value]
  await submitVote(themeId.value, ids)
  voted.value = true
  ElMessage.success(phase.value === 'REVOTE' ? '重投成功' : '投票成功')
  loadTheme()
}

function logout() {
  userStore.logout()
  router.push('/login')
}

const STATUS = {
  DRAFT: { text: '未开始', tag: 'info' },
  OPEN: { text: '投票中', tag: 'success' },
  TALLIED: { text: '已统计', tag: 'warning' },
  TIE_REVOTE: { text: '重投中', tag: 'warning' },
  ARCHIVED: { text: '已归档', tag: 'info' }
}
const statusText = s => STATUS[s]?.text || s
const statusTag = s => STATUS[s]?.tag || 'info'
</script>

<style scoped>
.topbar {
  position: sticky; top: 0; z-index: 100; background: rgba(255,255,255,.95);
  border-bottom: 1px solid #e6eaf2; padding: 0 20px; height: 56px;
  display: flex; align-items: center; gap: 12px;
}
.brand { display: flex; align-items: center; gap: 9px; font-size: 1.08rem; font-weight: 800; }
.brand .logo {
  width: 34px; height: 34px; border-radius: 9px;
  background: linear-gradient(135deg, #2b5fd9, #1d46b8);
  display: flex; align-items: center; justify-content: center; color: #fff;
}
.theme-switch { margin-left: 14px; display: flex; align-items: center; gap: 8px; font-size: .85rem; color: #5b6474; font-weight: 600; }
.user-chip {
  margin-left: auto; display: flex; align-items: center; gap: 8px; padding: 6px 14px;
  background: #e8effc; border-radius: 999px; font-weight: 800; color: #1d46b8; font-size: .92rem;
}
.user-chip .dot { width: 8px; height: 8px; border-radius: 50%; background: #1a9c55; }
.main { max-width: 1380px; margin: 0 auto; padding: 14px 18px 60px; }
.ctrl-grid { display: grid; grid-template-columns: 1fr 290px; gap: 14px; align-items: start; }
.left-col { display: flex; flex-direction: column; gap: 10px; }
.theme-card {
  background: #fff; border: 1px solid #e6eaf2; border-radius: 12px; box-shadow: 0 1px 3px rgba(28,35,51,.05);
  padding: 12px 18px; display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 10px;
}
.theme-name { font-size: 1.28rem; font-weight: 800; }
.theme-tip { font-size: .86rem; color: #5b6474; font-weight: 600; }
.theme-progress { display: flex; align-items: center; gap: 9px; font-size: .84rem; font-weight: 700; color: #5b6474; }
.vote-hint {
  display: flex; align-items: center; gap: 10px; padding: 10px 18px; border-radius: 10px;
  background: #e8effc; border: 1px solid #c6d6f5; color: #1d46b8;
  font-size: .9rem; font-weight: 700; min-height: 42px;
}
.vote-hint.warn { background: #fff4e5; border-color: #e07b00; color: #e07b00; }
.cat-section {
  background: #fff; border: 2px solid #e6eaf2; border-radius: 12px; overflow: hidden; margin-bottom: 10px;
}
.cat-section.pick { border-color: #2b5fd9; }
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
.cat-count { margin-left: auto; display: flex; align-items: center; gap: 10px; font-size: .8rem; font-weight: 800; }
.c-done { color: #2b5fd9; }
.c-left { color: #e07b00; }
.c-ok { color: #1a9c55; }
.cand-list { display: grid; grid-template-columns: repeat(auto-fill, minmax(250px, 1fr)); gap: 8px; padding: 11px 13px; }
.cand-row {
  display: flex; align-items: flex-start; gap: 10px; padding: 11px 12px; border-radius: 9px;
  border: 1.5px solid #e6eaf2; background: #fff; cursor: pointer; transition: all .15s; text-align: left; width: 100%;
}
.cand-row:hover { border-color: #b9c6dd; }
.cand-row.selected { border-color: #2b5fd9; background: #e8effc; }
.cand-row.disabled { cursor: not-allowed; opacity: .6; }
.cand-check {
  width: 22px; height: 22px; border-radius: 50%; border: 2.5px solid #d9dfea; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center; color: transparent; margin-top: 1px;
}
.selected .cand-check { background: #2b5fd9; border-color: #2b5fd9; color: #fff; }
.cand-info { flex: 1; min-width: 0; }
.cand-name { font-size: .95rem; font-weight: 800; display: flex; align-items: center; gap: 7px; flex-wrap: wrap; }
.cand-dept { font-size: .74rem; color: #5b6474; font-weight: 600; margin-top: 1px; }
.cat-tag { font-size: .72rem; font-weight: 800; padding: 2px 9px; border-radius: 999px; background: #eef1f5; color: #6a7383; white-space: nowrap; }
.cat-tag.locked { background: #e8f7ee; color: #1a9c55; }
.cat-tag.revote { background: #fff4e5; color: #e07b00; }
.cand-fields { display: grid; grid-template-columns: 1fr 1fr; gap: 4px 8px; margin-top: 8px; }
.cf {
  display: flex; justify-content: space-between; align-items: center; gap: 6px;
  background: #eef1f6; border-radius: 6px; padding: 3px 8px; min-width: 0;
}
.cf .cf-k { font-size: .7rem; color: #8b93a5; font-weight: 700; white-space: nowrap; flex-shrink: 0; }
.cf .cf-v { font-size: .74rem; font-weight: 800; color: #1c2333; text-align: right; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cf.wide { grid-column: 1/-1; }
.cf.wide .cf-v { white-space: normal; text-align: right; line-height: 1.4; }
.side-col { display: flex; flex-direction: column; gap: 10px; position: sticky; top: 66px; }
.card { background: #fff; border: 1px solid #e6eaf2; border-radius: 11px; box-shadow: 0 1px 3px rgba(28,35,51,.05); }
.info-card { padding: 14px 16px; }
.info-title { font-size: .84rem; font-weight: 800; color: #5b6474; margin-bottom: 9px; }
.sum-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 7px; }
.sum-item { text-align: center; padding: 9px 4px; border-radius: 9px; background: #eef1f6; }
.sum-item .sum-k { font-size: .7rem; color: #5b6474; font-weight: 700; }
.sum-item .sum-v { font-size: 1.25rem; font-weight: 800; line-height: 1.2; }
.winner-list { display: flex; flex-direction: column; gap: 6px; max-height: 180px; overflow-y: auto; }
.winner-chip {
  display: flex; align-items: center; gap: 8px; padding: 7px 12px; border-radius: 9px;
  background: #e8f7ee; border: 1.5px solid #1a9c55; color: #1a9c55; font-size: .88rem; font-weight: 800;
}
.quota-line { font-size: .86rem; color: #5b6474; font-weight: 700; line-height: 1.7; }
.quota-line b { color: #e07b00; font-size: 1rem; }
.empty-line { font-size: .82rem; color: #8b93a5; font-weight: 600; line-height: 1.6; }
.submit-box { padding: 12px 16px 14px; text-align: center; margin-top: auto; }
.submit-btn { width: 100%; height: 44px; font-size: 1.05rem; font-weight: 700; }
.submit-tip { font-size: .76rem; color: #8b93a5; font-weight: 600; margin-top: 7px; }
.voted-ok {
  display: flex; align-items: center; gap: 10px; padding: 16px; border-radius: 10px;
  background: #e8f7ee; border: 1.5px solid #1a9c55; color: #1a9c55; font-size: .95rem; font-weight: 800;
}
.voted-ok .ico {
  width: 30px; height: 30px; border-radius: 50%; background: #1a9c55; color: #fff;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.result-view { background: #fff; border: 1px solid #e6eaf2; border-radius: 12px; padding: 16px 18px; }
.result-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 13px; }
.result-title { font-size: 1.1rem; font-weight: 800; }
.result-rank { display: grid; grid-template-columns: repeat(auto-fill, minmax(290px, 1fr)); gap: 8px; }
.rr { display: flex; align-items: center; gap: 12px; padding: 11px 13px; border: 1.5px solid #e6eaf2; border-radius: 10px; }
.rr.win { border-color: #1a9c55; background: linear-gradient(90deg, #e8f7ee, #fff); }
.rr-no {
  width: 30px; height: 30px; border-radius: 50%; background: #eef1f5; display: flex; align-items: center;
  justify-content: center; font-size: .92rem; font-weight: 800; color: #5b6474; flex-shrink: 0;
}
.rr.win .rr-no { background: #1a9c55; color: #fff; }
.rr-info { flex: 1; min-width: 0; }
.rr-name { font-size: .98rem; font-weight: 800; display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.rr-dept { font-size: .76rem; color: #5b6474; font-weight: 600; }
.rr-votes { font-size: 1.15rem; font-weight: 800; text-align: center; min-width: 66px; }
.rr-votes .u { font-size: .7rem; color: #8b93a5; font-weight: 600; }
.win-tag { display: inline-flex; align-items: center; gap: 4px; font-size: .76rem; font-weight: 800; padding: 2px 11px; border-radius: 999px; background: #1a9c55; color: #fff; }
.login-guard { min-height: 80vh; display: flex; align-items: center; justify-content: center; }
@media (max-width: 1080px) {
  .ctrl-grid { grid-template-columns: 1fr; }
  .side-col { position: static; }
}
</style>
