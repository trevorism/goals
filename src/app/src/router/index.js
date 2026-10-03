import { createRouter, createWebHistory } from 'vue-router'
import GoalList from '../views/GoalList.vue'
import GoalDetail from '../views/GoalDetail.vue'
import TodayView from '../views/TodayView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'goals',
      component: GoalList,
      meta: { requiresAuth: true }
    },
    {
      path: '/today',
      name: 'today',
      component: TodayView,
      meta: { requiresAuth: true }
    },
    {
      path: '/goal/:id',
      name: 'goal',
      component: GoalDetail,
      props: true,
      meta: { requiresAuth: true }
    }
  ]
})

export default router
