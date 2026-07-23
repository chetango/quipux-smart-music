<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-header">
        <h1>Quipux Smart Music</h1>
        <p>Listas de reproducción</p>
      </div>

      <form @submit.prevent="handleLogin" novalidate>
        <div class="form-group">
          <label for="username">Usuario</label>
          <input
            id="username"
            v-model="form.username"
            type="text"
            placeholder="admin"
            autocomplete="username"
            :disabled="loading"
            required
          />
        </div>

        <div class="form-group">
          <label for="password">Contraseña</label>
          <input
            id="password"
            v-model="form.password"
            type="password"
            placeholder="••••••••"
            autocomplete="current-password"
            :disabled="loading"
            required
          />
        </div>

        <p v-if="error" class="error-msg" role="alert">{{ error }}</p>

        <button type="submit" class="btn-primary" :disabled="loading || !isFormValid">
          <span v-if="loading">Iniciando sesión…</span>
          <span v-else>Iniciar sesión</span>
        </button>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const form = ref({ username: '', password: '' })
const loading = ref(false)
const error = ref('')

const isFormValid = computed(
  () => form.value.username.trim() !== '' && form.value.password.trim() !== ''
)

async function handleLogin() {
  error.value = ''
  loading.value = true
  try {
    await auth.login(form.value.username.trim(), form.value.password)
  } catch (err) {
    const status = err.response?.status
    if (status === 401) {
      error.value = 'Usuario o contraseña incorrectos.'
    } else if (status === 400) {
      error.value = 'Completa todos los campos.'
    } else {
      error.value = 'Error al conectar con el servidor. Inténtalo de nuevo.'
    }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
}

.login-card {
  background: white;
  border-radius: 12px;
  padding: 2.5rem;
  width: 100%;
  max-width: 380px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

.login-header {
  text-align: center;
  margin-bottom: 2rem;
}

.login-header h1 {
  font-size: 1.75rem;
  font-weight: 700;
  color: #1a1a2e;
  letter-spacing: -0.5px;
}

.login-header p {
  color: #6b7280;
  font-size: 0.875rem;
  margin-top: 0.25rem;
}

.form-group {
  margin-bottom: 1rem;
}

.form-group label {
  display: block;
  font-size: 0.8125rem;
  font-weight: 500;
  color: #374151;
  margin-bottom: 0.375rem;
}

.form-group input {
  width: 100%;
  padding: 0.625rem 0.875rem;
  border: 1.5px solid #d1d5db;
  border-radius: 8px;
  font-size: 0.875rem;
  color: #1a1a2e;
  transition: border-color 0.15s;
  outline: none;
}

.form-group input:focus {
  border-color: #0f3460;
  box-shadow: 0 0 0 3px rgba(15, 52, 96, 0.1);
}

.form-group input:disabled {
  background: #f9fafb;
  color: #9ca3af;
}

.error-msg {
  font-size: 0.8125rem;
  color: #dc2626;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 6px;
  padding: 0.5rem 0.75rem;
  margin-bottom: 1rem;
}

.btn-primary {
  width: 100%;
  padding: 0.75rem;
  background: #0f3460;
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 0.9375rem;
  font-weight: 600;
  transition: background 0.15s, opacity 0.15s;
  margin-top: 0.5rem;
}

.btn-primary:hover:not(:disabled) {
  background: #16213e;
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
