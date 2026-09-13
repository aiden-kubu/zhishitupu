import { createRouter, createWebHistory } from 'vue-router'

/**
 * 信息架构（§4.1）：左侧只放六个模块，无重复入口。
 */
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  scrollBehavior(to, from, savedPosition) {
    return savedPosition || { left: 0, top: 0 }
  },
  routes: [
    {
      path: '/',
      name: 'GraphWorkspace',
      component: () => import('../views/Graph/GraphView.vue'),
      meta: { title: '图谱工作台' },
    },
    {
      path: '/library',
      name: 'Library',
      component: () => import('../views/Library/LibraryView.vue'),
      meta: { title: '知识库' },
    },
    {
      path: '/processing',
      name: 'Processing',
      component: () => import('../views/Processing/ProcessingView.vue'),
      meta: { title: '处理中心' },
    },
    {
      path: '/review',
      name: 'Review',
      component: () => import('../views/Review/ReviewView.vue'),
      meta: { title: '审核中心' },
    },
    {
      path: '/insights',
      name: 'Insights',
      component: () => import('../views/Insights/InsightsView.vue'),
      meta: { title: '数据洞察' },
    },
    {
      path: '/settings',
      name: 'Settings',
      component: () => import('../views/Settings/SettingsView.vue'),
      meta: { title: '系统设置' },
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'NotFound',
      component: () => import('../views/Errors/FourZeroFour.vue'),
      meta: { title: '页面不存在' },
    },
  ],
})

export default router

router.beforeEach((to, _from, next) => {
  document.title = `${String(to.meta.title ?? '')} | 知识图谱系统`
  next()
})
