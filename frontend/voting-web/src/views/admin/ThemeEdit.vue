<template>
  <div class="theme-edit">
    <el-row :gutter="14">
      <!-- 左侧: 主题列表 -->
      <el-col :span="7">
        <el-card shadow="never" class="list-card">
          <div class="card-head">
            <span class="title">投票主题</span>
            <el-button type="primary" size="small" @click="addTheme">
              <el-icon><Plus /></el-icon>新建
            </el-button>
          </div>
          <div v-if="themes.length === 0" class="empty">暂无主题</div>
          <div v-for="t in themes" :key="t.id" class="theme-item"
               :class="{ active: t.id === currentTheme?.id }" @click="selectTheme(t)">
            <div class="ti-top">
              <span class="ti-name">{{ t.name }}</span>
              <el-tag size="small" :type="statusTag(t.status)">{{ statusText(t.status) }}</el-tag>
            </div>
            <div class="ti-sub">
              名额 {{ t.totalQuota }} · 候选 {{ t.candidateCount }}
              <el-tag v-if="t.hasGood" size="small" type="warning" effect="plain">良好级</el-tag>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 右侧: 主题配置 -->
      <el-col :span="17" class="right-col">
        <el-card v-if="currentTheme" shadow="never" class="right-card">
          <div class="card-head">
            <span class="title">主题配置 · {{ currentTheme.name }}</span>
            <div>
              <el-button size="small" @click="saveThemeConfig">保存配置</el-button>
              <el-button size="small" type="danger" plain @click="removeTheme">删除主题</el-button>
            </div>
          </div>

          <el-tabs v-model="activeTab" class="theme-tabs">
            <!-- 基本信息 -->
            <el-tab-pane label="基本信息" name="basic">
              <el-form label-width="110px" class="basic-form">
                <el-form-item label="主题名称">
                  <el-input v-model="currentTheme.name" placeholder="主题名称即奖项名, 如: 优秀部门" />
                </el-form-item>
                <el-form-item label="无分类总名额">
                  <el-input-number v-model="currentTheme.totalQuota" :min="0" :max="99" />
                  <span class="tip">有分类时自动 = Σ分类名额, 无需设置</span>
                </el-form-item>
                <el-form-item label="启用良好级">
                  <el-switch v-model="currentTheme.hasGood" />
                  <span class="tip">归档后自动生成「良好」主题 (候选=优秀候选-当选者)</span>
                </el-form-item>
                <el-form-item v-if="currentTheme.hasGood" label="良好名额">
                  <el-input-number v-model="currentTheme.goodQuota" :min="0" :max="99" />
                  <span class="tip">默认 = 优秀名额, 可单独调整</span>
                </el-form-item>
                <el-form-item label="状态">
                  <el-tag :type="statusTag(currentTheme.status)">{{ statusText(currentTheme.status) }}</el-tag>
                  <span class="tip" style="color:#8b93a5">
                    · 编辑页不发起投票, 请到「现场控场」页开始投票
                  </span>
                </el-form-item>
              </el-form>
            </el-tab-pane>

            <!-- 分类配置 -->
            <el-tab-pane label="分类配置" name="cats">
              <div class="pane-head">
                <span class="tip">分类为可选: 每类自定义名称 + 名额 (总名额 = Σ分类名额)</span>
                <el-button size="small" type="primary" @click="addCat">
                  <el-icon><Plus /></el-icon>添加分类
                </el-button>
              </div>
              <el-table :data="currentTheme.categories" size="small" border>
                <el-table-column type="index" label="#" width="46" />
                <el-table-column label="分类名称">
                  <template #default="{ row }">
                    <el-input v-model="row.name" placeholder="如: 业务部门" />
                  </template>
                </el-table-column>
                <el-table-column label="名额" width="160">
                  <template #default="{ row }">
                    <el-input-number v-model="row.quota" :min="0" :max="99" size="small" />
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="90">
                  <template #default="{ $index }">
                    <el-button size="small" type="danger" link @click="removeCat($index)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <div class="quota-sum" v-if="currentTheme.categories.length">
                当前分类总名额: <b>{{ catTotalQuota }}</b>
              </div>
            </el-tab-pane>

            <!-- 自定义字段 -->
            <el-tab-pane label="自定义字段" name="fields">
              <div class="pane-head">
                <span class="tip">候选人在此框架下填值, 完整展示在投票端候选卡上</span>
                <el-button size="small" type="primary" @click="addField">
                  <el-icon><Plus /></el-icon>添加字段
                </el-button>
              </div>
              <el-table :data="currentTheme.customFields" size="small" border>
                <el-table-column type="index" label="#" width="46" />
                <el-table-column label="字段名">
                  <template #default="{ row }">
                    <el-input v-model="row.fieldLabel" placeholder="如: 负责人" />
                  </template>
                </el-table-column>
                <el-table-column label="类型" width="130">
                  <template #default="{ row }">
                    <el-select v-model="row.fieldType" size="small">
                      <el-option label="单行文本" value="TEXT" />
                      <el-option label="多行文本" value="TEXTAREA" />
                      <el-option label="数字" value="NUMBER" />
                      <el-option label="日期" value="DATE" />
                      <el-option label="下拉" value="SELECT" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="必填" width="80">
                  <template #default="{ row }">
                    <el-switch v-model="row.isRequired" size="small" />
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="90">
                  <template #default="{ $index }">
                    <el-button size="small" type="danger" link @click="removeField($index)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>

            <!-- 候选项管理 -->
            <el-tab-pane label="候选项管理" name="cands" class="cand-pane">
              <div class="pane-head">
                <span class="tip">固定属性: 名称 + 所属部门; 其余按自定义字段填写 (共 {{ currentTheme.customFields.length }} 个字段)</span>
                <div>
                  <el-button size="small" @click="loadCandidates">刷新</el-button>
                  <el-button size="small" @click="exportTemplate">
                    <el-icon><Download /></el-icon>导出模板
                  </el-button>
                  <el-upload :show-file-list="false" :http-request="importFile" accept=".xlsx,.xls"
                             style="display:inline-block;margin:0 8px">
                    <el-button size="small" type="success">
                      <el-icon><Upload /></el-icon>导入
                    </el-button>
                  </el-upload>
                  <el-button v-if="selectedCandIds.length" size="small" type="danger" plain @click="batchRemoveCandidates">
                    批量删除 ({{ selectedCandIds.length }})
                  </el-button>
                  <el-button size="small" type="primary" @click="addCandidate">
                    <el-icon><Plus /></el-icon>新增候选
                  </el-button>
                </div>
              </div>

              <el-table :data="candidates" size="small" border class="cand-table" height="100%"
                        @selection-change="sel => selectedCandIds = sel.map(s => s.id)">
                <el-table-column type="selection" width="40" fixed="left" />
                <el-table-column prop="sort" label="排序" width="60" fixed="left" />
                <el-table-column label="分类" width="100">
                  <template #default="{ row }">{{ row.categoryName || '—' }}</template>
                </el-table-column>
                <el-table-column label="所属部门" width="120">
                  <template #default="{ row }">{{ row.departmentName || '—' }}</template>
                </el-table-column>
                <el-table-column prop="name" label="候选项" width="110" />
                <el-table-column v-for="f in currentTheme.customFields" :key="f.id"
                                 :label="f.fieldLabel" min-width="100">
                  <template #default="{ row }">{{ (row.extraMap && row.extraMap[f.fieldLabel]) || '—' }}</template>
                </el-table-column>
                <el-table-column label="操作" width="120" fixed="right">
                  <template #default="{ row }">
                    <el-button size="small" link type="primary" @click="editCandidate(row)">编辑</el-button>
                    <el-button size="small" link type="danger" @click="removeCandidate(row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </el-card>
        <el-empty v-else description="请选择或新建一个投票主题" />
      </el-col>
    </el-row>

    <!-- 候选项编辑对话框 -->
    <el-dialog v-model="candDialog" :title="candForm.id ? '编辑候选项' : '新增候选项'" width="820px" top="8vh">
      <el-form label-width="110px" class="cand-form">
        <el-form-item label="名称" required>
          <el-input v-model="candForm.name" placeholder="候选项名称" />
        </el-form-item>
        <el-form-item label="所属部门" required>
          <el-select v-model="candForm.departmentId" placeholder="从部门主数据下拉选择" filterable style="width:100%">
            <el-option v-for="d in departments" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="currentTheme?.categories?.length" label="分类">
          <el-select v-model="candForm.categoryId" placeholder="选择分类" clearable style="width:100%">
            <el-option v-for="c in currentTheme.categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <div class="fields-grid">
          <el-form-item v-for="f in currentTheme?.customFields" :key="f.id" :label="f.fieldLabel"
                        :class="{ 'wide-field': f.fieldType === 'TEXTAREA' }">
            <el-input v-model="candForm.extraMap[f.fieldLabel]" :type="f.fieldType === 'TEXTAREA' ? 'textarea' : 'text'"
                      :rows="f.fieldType === 'TEXTAREA' ? 2 : 1" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="candDialog = false">取消</el-button>
        <el-button type="primary" @click="submitCandidate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listThemes, getTheme, saveTheme, deleteTheme,
  listDepartments, listCandidates, saveCandidate, deleteCandidate, batchDeleteCandidates
} from '../../api'
import { wsClient } from '../../utils/ws'

const themes = ref([])
const currentTheme = ref(null)
const departments = ref([])
const candidates = ref([])
const selectedCandIds = ref([])
const wsUnsub = ref(null)
const activeTab = ref('basic')
const candDialog = ref(false)
const candForm = reactive({ id: null, name: '', departmentId: null, categoryId: null, extraMap: {} })

const catTotalQuota = computed(() =>
  (currentTheme.value?.categories || []).reduce((s, c) => s + (c.quota || 0), 0)
)

const STATUS = {
  DRAFT: { text: '未开始', tag: 'info' },
  OPEN: { text: '投票中', tag: 'success' },
  TALLIED: { text: '已统计', tag: 'warning' },
  TIE_REVOTE: { text: '重投中', tag: 'warning' },
  ARCHIVED: { text: '已归档', tag: 'info' }
}
const statusText = s => STATUS[s]?.text || s
const statusTag = s => STATUS[s]?.tag || 'info'

onMounted(async () => {
  await loadAll()
  // 订阅 WS 主题更新: 归档自动生成良好主题/其他控场动作后, 自动刷新主题列表, 无需手动刷新页面
  wsUnsub.value = wsClient.subscribe(msg => {
    if (msg.type === 'themeUpdate') {
      refreshThemes()
    }
  })
})

onUnmounted(() => { if (wsUnsub.value) wsUnsub.value() })

/** 刷新主题列表(保留当前选中主题); 若当前主题已被删除则回到第一个 */
async function refreshThemes() {
  const cur = currentTheme.value?.id
  themes.value = await listThemes()
  if (cur && themes.value.some(t => t.id === cur)) {
    currentTheme.value = themes.value.find(t => t.id === cur)
    if (currentTheme.value.candidateCount !== undefined) {
      loadCandidates()
    }
  } else if (themes.value.length) {
    selectTheme(themes.value[0])
  } else {
    currentTheme.value = null
    candidates.value = []
  }
}

async function loadAll() {
  themes.value = await listThemes()
  departments.value = await listDepartments()
  if (!currentTheme.value && themes.value.length) {
    selectTheme(themes.value[0])
  }
}

function selectTheme(t) {
  currentTheme.value = t
  loadCandidates()
}

async function addTheme() {
  const name = await ElMessageBox.prompt('请输入主题名称(即奖项名)', '新建主题', {
    inputPlaceholder: '如: 优秀部门', inputPattern: /.+/, inputErrorMessage: '名称不能为空'
  }).catch(() => null)
  if (!name) return
  const theme = await saveTheme({
    name: name.value, status: 'DRAFT', totalQuota: 1,
    hasGood: false, currentRound: 1, categories: [], customFields: []
  })
  await loadAll()
  selectTheme(themes.value.find(t => t.id === theme.id))
  ElMessage.success('主题已创建')
}

async function saveThemeConfig() {
  const payload = {
    ...currentTheme.value,
    totalQuota: currentTheme.value.categories?.length ? catTotalQuota.value : currentTheme.value.totalQuota,
    goodQuota: currentTheme.value.goodQuota
  }
  await saveTheme(payload)
  await loadAll()
  selectTheme(themes.value.find(t => t.id === payload.id))
  ElMessage.success('配置已保存')
}

async function removeTheme() {
  await ElMessageBox.confirm(`确定删除主题「${currentTheme.value.name}」? 仅 DRAFT 状态可删`, '提示', { type: 'warning' })
  await deleteTheme(currentTheme.value.id)
  currentTheme.value = null
  await loadAll()
  ElMessage.success('已删除')
}

function addCat() {
  if (!currentTheme.value.categories) currentTheme.value.categories = []
  currentTheme.value.categories.push({ name: '', quota: 1, sort: currentTheme.value.categories.length })
}
function removeCat(i) { currentTheme.value.categories.splice(i, 1) }

function addField() {
  if (!currentTheme.value.customFields) currentTheme.value.customFields = []
  currentTheme.value.customFields.push({ fieldLabel: '', fieldType: 'TEXT', isRequired: false, sort: currentTheme.value.customFields.length })
}
function removeField(i) { currentTheme.value.customFields.splice(i, 1) }

async function loadCandidates() {
  if (!currentTheme.value) return
  candidates.value = await listCandidates(currentTheme.value.id)
  selectedCandIds.value = []
}

function addCandidate() {
  Object.assign(candForm, { id: null, name: '', departmentId: null, categoryId: null })
  candForm.extraMap = {}
  for (const f of currentTheme.value.customFields) {
    candForm.extraMap[f.fieldLabel] = ''
  }
  candDialog.value = true
}

function editCandidate(row) {
  Object.assign(candForm, {
    id: row.id, name: row.name, departmentId: row.departmentId, categoryId: row.categoryId
  })
  candForm.extraMap = {}
  for (const f of currentTheme.value.customFields) {
    candForm.extraMap[f.fieldLabel] = (row.extraMap && row.extraMap[f.fieldLabel]) || ''
  }
  candDialog.value = true
}

async function submitCandidate() {
  if (!candForm.name) { ElMessage.warning('请输入名称'); return }
  if (!candForm.departmentId) { ElMessage.warning('请选择所属部门'); return }
  const payload = {
    id: candForm.id, themeId: currentTheme.value.id, name: candForm.name,
    departmentId: candForm.departmentId, categoryId: candForm.categoryId || null,
    extra: JSON.stringify(candForm.extraMap), sort: candidates.value.length
  }
  await saveCandidate(currentTheme.value.id, payload)
  candDialog.value = false
  await loadCandidates()
  ElMessage.success('已保存')
}

async function removeCandidate(row) {
  await ElMessageBox.confirm(`删除候选人「${row.name}」?`, '提示', { type: 'warning' })
  await deleteCandidate(currentTheme.value.id, row.id)
  await loadCandidates()
  ElMessage.success('已删除')
}

async function batchRemoveCandidates() {
  await ElMessageBox.confirm(
    `确定删除选中的 ${selectedCandIds.value.length} 个候选人? 已产生的投票记录将一并清理关联`, '批量删除', { type: 'warning' }
  )
  await batchDeleteCandidates(currentTheme.value.id, selectedCandIds.value)
  await loadCandidates()
  ElMessage.success(`已删除 ${selectedCandIds.value.length} 个候选人`)
}

function exportTemplate() {
  const token = localStorage.getItem('voting_token')
  const url = `/api/theme/${currentTheme.value.id}/candidate/template`
  const xhr = new XMLHttpRequest()
  xhr.open('GET', url, true)
  xhr.setRequestHeader('Authorization', `Bearer ${token}`)
  xhr.responseType = 'blob'
  xhr.onload = () => {
    const blob = xhr.response
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = `${currentTheme.value.name}-候选人模板.xlsx`
    a.click()
    URL.revokeObjectURL(a.href)
  }
  xhr.send()
}

async function importFile({ file }) {
  const token = localStorage.getItem('voting_token')
  const fd = new FormData()
  fd.append('file', file)
  const res = await fetch(`/api/theme/${currentTheme.value.id}/candidate/import`, {
    method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: fd
  })
  const json = await res.json()
  if (json.code === 200) {
    const d = json.data
    ElMessage.success(`导入成功 ${d.success} 条` + (d.errors.length ? `, ${d.errors.length} 行失败` : ''))
    if (d.errors.length) {
      ElMessageBox.alert(d.errors.slice(0, 20).join('\n'), '导入错误行', { type: 'warning' })
    }
    await loadCandidates()
  } else {
    ElMessage.error(json.msg || '导入失败')
  }
}
</script>

<style scoped>
/* 页面整体撑满, 右栏卡片/表格拉到页面底部 */
.theme-edit { height: calc(100vh - 96px); }
.theme-edit :deep(.el-row) { height: 100%; }
.theme-edit :deep(.el-col) { height: 100%; }
.right-col { display: flex; }
.right-col > .el-card { flex: 1; display: flex; flex-direction: column; min-height: 0; }
.right-col :deep(.el-card__body) {
  flex: 1; display: flex; flex-direction: column; min-height: 0;
}
.theme-tabs {
  flex: 1; display: flex; flex-direction: column; min-height: 0;
}
.theme-tabs :deep(.el-tabs__content) {
  flex: 1; min-height: 0; overflow: auto;
}
.theme-tabs :deep(.el-tab-pane) {
  height: 100%; display: flex; flex-direction: column;
}
.theme-tabs :deep(.cand-pane) { min-height: 0; }
.cand-table { flex: 1; min-height: 0; }
.card-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.card-head .title { font-size: 1.05rem; font-weight: 800; }
.list-card { height: 100%; overflow: auto; }
.list-card :deep(.el-card__body) { display: flex; flex-direction: column; }
.theme-item {
  padding: 12px 14px; border: 1.5px solid #e6eaf2; border-radius: 10px;
  margin-bottom: 8px; cursor: pointer; transition: all .15s; background: #fff;
}
.theme-item:hover { border-color: #b9c6dd; }
.theme-item.active { border-color: #2b5fd9; background: #e8effc; }
.ti-top { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.ti-name { font-weight: 800; font-size: .95rem; }
.ti-sub { font-size: .78rem; color: #8b93a5; margin-top: 5px; display: flex; gap: 6px; align-items: center; }
.empty { color: #8b93a5; text-align: center; padding: 30px 0; font-size: .85rem; }
.pane-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.tip { font-size: .8rem; color: #8b93a5; margin-left: 10px; }
.quota-sum { margin-top: 12px; font-size: .9rem; color: #5b6474; }
.quota-sum b { color: #2b5fd9; font-size: 1.1rem; }
.basic-form { max-width: 620px; }
.cand-table :deep(.el-table__cell) { padding: 5px 6px; }
.cand-form .fields-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
  padding: 0 4px;
}
.cand-form .fields-grid .el-form-item { margin-bottom: 14px; }
.cand-form .fields-grid .wide-field { grid-column: 1 / -1; }
.cand-form :deep(.el-form-item__label) {
  white-space: nowrap;
  min-width: 110px;
  padding-right: 8px;
}
.cand-form :deep(.el-input__inner),
.cand-form :deep(.el-textarea__inner) {
  min-width: 0;
}
</style>
