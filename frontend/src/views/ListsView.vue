<template>
  <div class="page">
    <!-- Navbar -->
    <nav class="navbar">
      <span class="navbar-brand">Quipux Smart Music</span>
      <button class="btn-logout" @click="auth.logout()">Cerrar sesión</button>
    </nav>

    <main class="container">
      <div class="page-header">
        <h2>Mis listas</h2>
        <button class="btn-primary" @click="showForm = !showForm">
          {{ showForm ? 'Cancelar' : '+ Nueva lista' }}
        </button>
      </div>

      <!-- Formulario nueva lista -->
      <form v-if="showForm" class="card form-card" @submit.prevent="createList">
        <h3>Nueva lista</h3>
        <div class="form-row">
          <div class="form-group">
            <label>Nombre</label>
            <input
              v-model="newList.listName"
              type="text"
              placeholder="Nombre de la lista"
              :disabled="creating"
              required
            />
          </div>
          <div class="form-group">
            <label>Descripción</label>
            <input
              v-model="newList.description"
              type="text"
              placeholder="Breve descripción"
              :disabled="creating"
              required
            />
          </div>
        </div>
        <p v-if="createError" class="error-msg">{{ createError }}</p>
        <div class="form-actions">
          <button
            type="submit"
            class="btn-primary"
            :disabled="creating || !newList.listName.trim() || !newList.description.trim()"
          >
            {{ creating ? 'Creando…' : 'Crear lista' }}
          </button>
        </div>
      </form>

      <!-- Estado de carga -->
      <div v-if="loading" class="state-message">Cargando listas…</div>

      <!-- Error de carga -->
      <div v-else-if="loadError" class="error-msg">{{ loadError }}</div>

      <!-- Estado vacío -->
      <div v-else-if="lists.length === 0" class="state-message empty">
        <p>Aún no tienes listas de reproducción.</p>
        <p>Crea una para empezar.</p>
      </div>

      <!-- Lista de playlists -->
      <ul v-else class="lists-grid">
        <li v-for="list in lists" :key="list.listName" class="card list-card">
          <div class="list-info">
            <span class="list-name">{{ list.listName }}</span>
            <span class="list-desc">{{ list.description }}</span>
            <span class="list-count">{{ list.songs.length }} canción{{ list.songs.length !== 1 ? 'es' : '' }}</span>
          </div>
          <div class="list-actions">
            <button class="btn-secondary" @click="goToDetail(list.listName)">
              Ver detalle
            </button>
            <button
              class="btn-danger"
              :disabled="deletingName === list.listName"
              @click="deleteList(list.listName)"
            >
              {{ deletingName === list.listName ? '…' : 'Eliminar' }}
            </button>
          </div>
        </li>
      </ul>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import api from '@/api/axios'

const auth = useAuthStore()
const router = useRouter()

const lists = ref([])
const loading = ref(false)
const loadError = ref('')

const showForm = ref(false)
const newList = ref({ listName: '', description: '' })
const creating = ref(false)
const createError = ref('')

const deletingName = ref('')

onMounted(fetchLists)

async function fetchLists() {
  loading.value = true
  loadError.value = ''
  try {
    const { data } = await api.get('/lists')
    lists.value = data
  } catch (err) {
    loadError.value = 'No se pudieron cargar las listas. Inténtalo de nuevo.'
  } finally {
    loading.value = false
  }
}

async function createList() {
  createError.value = ''
  creating.value = true
  try {
    const { data } = await api.post('/lists', {
      listName: newList.value.listName.trim(),
      description: newList.value.description.trim()
    })
    lists.value.push(data)
    newList.value = { listName: '', description: '' }
    showForm.value = false
  } catch (err) {
    const status = err.response?.status
    if (status === 409) {
      createError.value = `Ya existe una lista con el nombre "${newList.value.listName}".`
    } else if (status === 400) {
      createError.value = 'El nombre y la descripción son obligatorios.'
    } else {
      createError.value = 'Error al crear la lista. Inténtalo de nuevo.'
    }
  } finally {
    creating.value = false
  }
}

async function deleteList(listName) {
  if (!confirm(`¿Eliminar la lista "${listName}" y todas sus canciones?`)) return
  deletingName.value = listName
  try {
    await api.delete(`/lists/${encodeURIComponent(listName)}`)
    lists.value = lists.value.filter((l) => l.listName !== listName)
  } catch (err) {
    const status = err.response?.status
    if (status === 404) {
      lists.value = lists.value.filter((l) => l.listName !== listName)
    } else {
      alert('Error al eliminar la lista. Inténtalo de nuevo.')
    }
  } finally {
    deletingName.value = ''
  }
}

function goToDetail(listName) {
  router.push(`/lists/${encodeURIComponent(listName)}`)
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f4f6f9;
}

.navbar {
  background: #1a1a2e;
  color: white;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 1.5rem;
  height: 56px;
  position: sticky;
  top: 0;
  z-index: 10;
}

.navbar-brand {
  font-weight: 700;
  font-size: 1.125rem;
  letter-spacing: -0.3px;
}

.container {
  max-width: 860px;
  margin: 0 auto;
  padding: 1.5rem 1rem;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 1.25rem;
}

.page-header h2 {
  font-size: 1.375rem;
  font-weight: 700;
  color: #1a1a2e;
}

.card {
  background: white;
  border-radius: 10px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}

.form-card {
  padding: 1.25rem 1.5rem;
  margin-bottom: 1.25rem;
}

.form-card h3 {
  font-size: 1rem;
  font-weight: 600;
  color: #374151;
  margin-bottom: 1rem;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.75rem;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
}

.form-group label {
  font-size: 0.8125rem;
  font-weight: 500;
  color: #374151;
}

.form-group input,
.form-group select {
  padding: 0.5rem 0.75rem;
  border: 1.5px solid #d1d5db;
  border-radius: 7px;
  font-size: 0.875rem;
  color: #1a1a2e;
  outline: none;
  transition: border-color 0.15s;
}

.form-group input:focus,
.form-group select:focus {
  border-color: #0f3460;
  box-shadow: 0 0 0 2px rgba(15, 52, 96, 0.1);
}

.form-group input:disabled,
.form-group select:disabled {
  background: #f9fafb;
  color: #9ca3af;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 1rem;
}

.error-msg {
  font-size: 0.8125rem;
  color: #dc2626;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 6px;
  padding: 0.5rem 0.75rem;
  margin: 0.75rem 0 0;
}

.state-message {
  text-align: center;
  color: #6b7280;
  padding: 3rem 1rem;
  font-size: 0.9375rem;
}

.state-message.empty p + p {
  margin-top: 0.25rem;
  font-size: 0.875rem;
}

.lists-grid {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.list-card {
  padding: 1rem 1.25rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  transition: box-shadow 0.15s;
}

.list-card:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.list-info {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  min-width: 0;
}

.list-name {
  font-weight: 600;
  font-size: 0.9375rem;
  color: #1a1a2e;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.list-desc {
  font-size: 0.8125rem;
  color: #6b7280;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.list-count {
  font-size: 0.75rem;
  color: #9ca3af;
}

.list-actions {
  display: flex;
  gap: 0.5rem;
  flex-shrink: 0;
}

.btn-primary {
  padding: 0.5rem 1rem;
  background: #0f3460;
  color: white;
  border: none;
  border-radius: 7px;
  font-weight: 500;
  transition: background 0.15s, opacity 0.15s;
}

.btn-primary:hover:not(:disabled) {
  background: #16213e;
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-secondary {
  padding: 0.5rem 0.875rem;
  background: #f3f4f6;
  color: #374151;
  border: 1.5px solid #d1d5db;
  border-radius: 7px;
  font-weight: 500;
  transition: background 0.15s;
}

.btn-secondary:hover:not(:disabled) {
  background: #e5e7eb;
}

.btn-danger {
  padding: 0.5rem 0.875rem;
  background: white;
  color: #dc2626;
  border: 1.5px solid #fca5a5;
  border-radius: 7px;
  font-weight: 500;
  transition: background 0.15s, border-color 0.15s;
}

.btn-danger:hover:not(:disabled) {
  background: #fef2f2;
  border-color: #dc2626;
}

.btn-danger:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-logout {
  padding: 0.375rem 0.875rem;
  background: transparent;
  color: #d1d5db;
  border: 1.5px solid #4b5563;
  border-radius: 7px;
  font-size: 0.8125rem;
  transition: color 0.15s, border-color 0.15s;
}

.btn-logout:hover {
  color: white;
  border-color: #9ca3af;
}
</style>
