import request from './request'

// 认证
export const login = data => request.post('/auth/login', data)

// 部门
export const listDepartments = () => request.get('/department/list')
export const saveDepartment = data => request.post('/department/save', data)
export const updateDepartment = data => request.put('/department/update', data)
export const deleteDepartment = id => request.delete(`/department/${id}`)

// 用户
export const listUsers = () => request.get('/user/list')
export const listLeaders = () => request.get('/user/leaders')
export const saveUser = data => request.post('/user/save', data)
export const updateUser = data => request.put('/user/update', data)
export const deleteUser = id => request.delete(`/user/${id}`)

// 主题
export const listThemes = () => request.get('/theme/list')
export const getTheme = id => request.get(`/theme/${id}`)
export const saveTheme = data => request.post('/theme/save', data)
export const deleteTheme = id => request.delete(`/theme/${id}`)

// 候选人
export const listCandidates = themeId => request.get(`/theme/${themeId}/candidate/list`)
export const saveCandidate = (themeId, data) => request.post(`/theme/${themeId}/candidate/save`, data)
export const deleteCandidate = (themeId, id) => request.delete(`/theme/${themeId}/candidate/${id}`)

// 控场
export const controlStart = themeId => request.post(`/control/${themeId}/start`)
export const controlTally = themeId => request.post(`/control/${themeId}/tally`)
export const controlEnd = themeId => request.post(`/control/${themeId}/end`)
export const controlRevoke = (themeId, userId) => request.post(`/control/${themeId}/revoke`, null, { params: { userId } })
export const controlRevote = themeId => request.post(`/control/${themeId}/revote`)
export const controlClear = themeId => request.post(`/control/${themeId}/clear`)
export const controlParticipation = themeId => request.get(`/control/${themeId}/participation`)
export const controlRanking = themeId => request.get(`/control/${themeId}/ranking`)
export const controlLogs = themeId => request.get(`/control/${themeId}/logs`)

// 投票端
export const voteThemes = () => request.get('/vote/themes')
export const voteThemeDetail = themeId => request.get(`/vote/theme/${themeId}`)
export const voteRevoteInfo = themeId => request.get(`/vote/theme/${themeId}/revote`)
export const submitVote = (themeId, candidateIds) => request.post('/vote/submit', { themeId, candidateIds })

// 获奖地图
export const winnerMap = () => request.get('/map/winner')

// 审计
export const auditBallots = themeId => request.get('/audit/ballots', { params: themeId ? { themeId } : {} })
export const auditLogs = themeId => request.get('/audit/logs', { params: themeId ? { themeId } : {} })
