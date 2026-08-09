import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../store/user'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/login/Login.vue'),
    meta: { public: true }
  },
  {
    path: '/vote',
    name: 'VoteHome',
    component: () => import('../views/vote/VoteHome.vue'),
    meta: { roles: ['leader'] }
  },
  {
    path: '/screen/:themeId',
    name: 'BigScreen',
    component: () => import('../views/screen/BigScreen.vue'),
    meta: { public: true }
  },
  {
    path: '/admin',
    component: () => import('../views/admin/AdminLayout.vue'),
    redirect: '/admin/themes',
    meta: { roles: ['admin', 'super_admin'] },
    children: [
      {
        path: 'themes',
        name: 'ThemeEdit',
        component: () => import('../views/admin/ThemeEdit.vue'),
        meta: { title: '主题编辑' }
      },
      {
        path: 'control',
        name: 'ControlRoom',
        component: () => import('../views/admin/ControlRoom.vue'),
        meta: { title: '现场控场' }
      },
      {
        path: 'map',
        name: 'WinnerMap',
        component: () => import('../views/admin/WinnerMap.vue'),
        meta: { title: '获奖地图' }
      },
      {
        path: 'departments',
        name: 'Departments',
        component: () => import('../views/admin/Departments.vue'),
        meta: { title: '部门管理' }
      },
      {
        path: 'users',
        name: 'Users',
        component: () => import('../views/admin/Users.vue'),
        meta: { title: '账号管理' }
      },
      {
        path: 'audit',
        name: 'Audit',
        component: () => import('../views/admin/Audit.vue'),
        meta: { title: '审计追溯', roles: ['super_admin'] }
      }
    ]
  },
  {
    path: '/',
    redirect: '/login'
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/login'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  if (to.meta.public) {
    next()
    return
  }
  if (!userStore.isLogin) {
    next('/login')
    return
  }
  // 角色检查: 路由 meta.roles 与子路由 meta 合并
  const roles = to.meta.roles || to.matched.filter(m => m.meta.roles).map(m => m.meta.roles).flat()
  if (roles && roles.length && !roles.includes(userStore.role)) {
    // 领导只能去投票端
    if (userStore.isLeader) {
      next('/vote')
    } else {
      next('/login')
    }
    return
  }
  // 领导默认跳投票端
  if (userStore.isLeader && to.path.startsWith('/admin')) {
    next('/vote')
    return
  }
  next()
})

export default router
