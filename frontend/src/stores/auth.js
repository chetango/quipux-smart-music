import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import api from '@/api/axios'
import router from '@/router'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token'))

  const isAuthenticated = computed(() => !!token.value)

  async function login(username, password) {
    const response = await api.post('/auth/login', { username, password })
    token.value = response.data.token
    localStorage.setItem('token', token.value)
    await router.push('/lists')
  }

  function logout() {
    token.value = null
    localStorage.removeItem('token')
    router.push('/login')
  }

  return { token, isAuthenticated, login, logout }
})
