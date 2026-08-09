import { defineStore } from 'pinia'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('voting_token') || '',
    user: JSON.parse(localStorage.getItem('voting_user') || 'null')
  }),
  getters: {
    isLogin: state => !!state.token,
    role: state => state.user?.role || '',
    isAdmin: state => state.user && (state.user.role === 'admin' || state.user.role === 'super_admin'),
    isLeader: state => state.user?.role === 'leader',
    isSuperAdmin: state => state.user?.role === 'super_admin'
  },
  actions: {
    setLogin(token, user) {
      this.token = token
      this.user = user
      localStorage.setItem('voting_token', token)
      localStorage.setItem('voting_user', JSON.stringify(user))
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem('voting_token')
      localStorage.removeItem('voting_user')
    }
  }
})
