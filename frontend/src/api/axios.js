import axios from 'axios'

// La instancia se crea aquí; el router se importa de forma lazy
// para evitar dependencia circular (router importa stores, stores importan api).
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      // Importación lazy para evitar circular dependency
      import('@/router').then(({ default: router }) => {
        if (router.currentRoute.value.path !== '/login') {
          router.push('/login')
        }
      })
    }
    return Promise.reject(error)
  }
)

export default api
