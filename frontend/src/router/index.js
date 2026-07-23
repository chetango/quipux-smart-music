import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '@/views/LoginView.vue'
import ListsView from '@/views/ListsView.vue'
import ListDetailView from '@/views/ListDetailView.vue'

const routes = [
  { path: '/', redirect: '/lists' },
  {
    path: '/login',
    component: LoginView,
    meta: { requiresAuth: false }
  },
  {
    path: '/lists',
    component: ListsView,
    meta: { requiresAuth: true }
  },
  {
    path: '/lists/:listName',
    component: ListDetailView,
    meta: { requiresAuth: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const token = localStorage.getItem('token')

  if (to.meta.requiresAuth && !token) {
    return '/login'
  }

  if (to.path === '/login' && token) {
    return '/lists'
  }
})

export default router
