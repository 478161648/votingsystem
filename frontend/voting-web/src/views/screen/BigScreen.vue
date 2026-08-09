<template>
  <div class="screen-page" :class="{ secret: isSecret }">
    <header class="screen-head">
      <div class="title">{{ theme?.name || '年度投票' }}</div>
      <el-tag size="large" :type="statusTag(theme?.status)" effect="dark">{{ statusText(theme?.status) }}</el-tag>
    </header>

    <!-- 投票中: 只显示候选名单 + 参与进度 (结果保密) -->
    <template v-if="isSecret">
      <div class="secret-banner">🔒 投票进行中 · 得票结果保密</div>
      <div class="secret-grid">
        <div v-for="(cat, ci) in categories" :key="cat.id" class="cat-block">
          <div class="cat-title">{{ cn[ci] }} · {{ cat.name }} (名额 {{ cat.quota }})</div>
          <div class="name-grid">
            <div v-for="c in catCands(cat)" :key="c.id" class="name-tile">{{ c.name }}</div>
          </div>
        </div>
      </div>
    </template>

    <!-- 统计后: 排名榜(按分类分区, 与投票端一致) -->
    <template v-else>
      <div class="screen-sections">
        <div v-for="(sec, si) in rankSections" :key="si" class="cat-block">
          <div class="cat-title">{{ cn[si] }} · {{ sec.name }} <span class="cat-quota">(名额 {{ sec.quota }})</span></div>
          <div class="rank-grid">
            <div v-for="c in sec.list" :key="c.id" class="rank-tile" :class="{ win: c.isWinner }">
              <div class="rt-no">{{ c.isWinner ? '✓' : c.rank }}</div>
              <div class="rt-name">{{ c.name }}</div>
              <div class="rt-sub">{{ c.categoryName }} {{ c.departmentName ? '· ' + c.departmentName : '' }}</div>
              <div class="rt-votes">{{ c.votes }} <span>票</span></div>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- 参与进度 -->
    <footer class="screen-foot" v-if="voting">
      <div class="prog"><span>参与进度</span>
        <el-progress :percentage="progress" :stroke-width="14" :show-text="false" style="width: 300px" />
        <span class="prog-num">{{ votedCount }}/{{ onlineCount }}</span>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { getTheme, controlRanking, controlParticipation } from '../../api'
import { wsClient } from '../../utils/ws'

const route = useRoute()
const themeId = route.params.themeId
const theme = ref(null)
const categories = ref([])
const candidates = ref([])
const participation = ref([])
let unsub = null

const cn = ['一', '二', '三', '四', '五', '六', '七', '八']
const isSecret = computed(() => theme.value && (theme.value.status === 'OPEN' || theme.value.status === 'TIE_REVOTE'))
const voting = computed(() => isSecret.value)
const onlineCount = computed(() => participation.value.length)
const votedCount = computed(() => participation.value.filter(p => p.voted).length)
const progress = computed(() => onlineCount.value ? Math.round(votedCount.value / onlineCount.value * 100) : 0)
const rankedList = computed(() => [...candidates.value].sort((a, b) => (b.votes || 0) - (a.votes || 0)))

/** 按分类分区的排名区段(与投票端布局一致); 无分类时单池 */
const rankSections = computed(() => {
  if (!categories.value.length) {
    return [{ name: '全部候选项', quota: theme.value?.totalQuota ?? '', list: rankedList.value }]
  }
  return categories.value.map(cat => {
    const list = rankedList.value.filter(c => c.categoryId === cat.id)
    return { name: cat.name, quota: cat.quota ?? '', list }
  })
})

function catCands(cat) {
  return candidates.value.filter(c => c.categoryId === cat.id)
}

onMounted(async () => {
  await loadAll()
  unsub = wsClient.subscribe(msg => {
    if (msg.type === 'themeUpdate') loadAll()
  })
})
onUnmounted(() => { if (unsub) unsub() })

async function loadAll() {
  const t = await getTheme(themeId)
  theme.value = t
  categories.value = t.categories || []
  candidates.value = await controlRanking(themeId)
  participation.value = await controlParticipation(themeId)
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
.screen-page {
  min-height: 100vh; background: linear-gradient(135deg, #141b2e, #1c2333);
  color: #fff; padding: 24px 32px 90px; display: flex; flex-direction: column;
}
.screen-head { display: flex; align-items: center; justify-content: center; gap: 16px; padding-bottom: 18px; border-bottom: 1px solid rgba(255,255,255,.12); }
.screen-head .title { font-size: 2rem; font-weight: 800; letter-spacing: 2px; }
.secret-banner {
  margin: 22px auto; padding: 12px 40px; border-radius: 12px;
  background: rgba(224, 123, 0, .18); border: 1px solid rgba(224, 123, 0, .5);
  font-size: 1.05rem; font-weight: 700; text-align: center;
}
.secret-grid { flex: 1; overflow: auto; }
.cat-block { margin-bottom: 20px; }
.cat-title { font-size: 1.15rem; font-weight: 800; color: #7ea6ff; margin-bottom: 10px; }
.cat-quota { font-size: .85rem; color: rgba(255,255,255,.55); font-weight: 600; }
.name-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 10px; }
.name-tile {
  background: rgba(255,255,255,.07); border: 1px solid rgba(255,255,255,.14);
  border-radius: 10px; padding: 14px 10px; text-align: center; font-size: 1.05rem; font-weight: 700;
}
.rank-grid { flex: 1; display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 12px; overflow: auto; padding: 22px 0; }
.rank-tile {
  background: rgba(255,255,255,.06); border: 1.5px solid rgba(255,255,255,.15);
  border-radius: 14px; padding: 16px; text-align: center;
}
.rank-tile.win { background: rgba(26,156,85,.18); border-color: #1a9c55; }
.rt-no {
  width: 38px; height: 38px; border-radius: 50%; margin: 0 auto 8px;
  background: rgba(255,255,255,.12); display: flex; align-items: center; justify-content: center;
  font-size: 1.1rem; font-weight: 800;
}
.win .rt-no { background: #1a9c55; color: #fff; }
.rt-name { font-size: 1.2rem; font-weight: 800; }
.rt-sub { font-size: .82rem; color: rgba(255,255,255,.55); margin-top: 2px; }
.rt-votes { font-size: 1.7rem; font-weight: 800; color: #4f8cff; margin-top: 6px; }
.rt-votes span { font-size: .85rem; color: rgba(255,255,255,.5); }
.win .rt-votes { color: #1a9c55; }
.screen-foot {
  position: fixed; left: 0; right: 0; bottom: 0; padding: 14px 32px;
  background: rgba(20,27,46,.92); display: flex; justify-content: center;
  border-top: 1px solid rgba(255,255,255,.1);
}
.prog { display: flex; align-items: center; gap: 16px; font-size: 1rem; font-weight: 700; }
.prog-num { font-size: 1.2rem; color: #4f8cff; }
</style>
