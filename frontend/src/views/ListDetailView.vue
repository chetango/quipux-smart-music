<template>
  <div class="page">
    <!-- Navbar -->
    <nav class="navbar">
      <button class="btn-back" @click="router.push('/lists')">← Volver</button>
      <span class="navbar-brand">Quipux Smart Music</span>
      <button class="btn-logout" @click="auth.logout()">Cerrar sesión</button>
    </nav>

    <main class="container">

      <!-- Estado de carga inicial -->
      <div v-if="loading" class="state-message">Cargando lista…</div>

      <!-- Error carga inicial -->
      <div v-else-if="loadError" class="error-banner">{{ loadError }}</div>

      <template v-else-if="playlist">
        <!-- Cabecera de la lista -->
        <div class="playlist-header card">
          <div class="playlist-meta">
            <h2>{{ playlist.listName }}</h2>
            <p>{{ playlist.description }}</p>
          </div>
        </div>

        <!-- Sección: Canciones -->
        <section class="section">
          <div class="section-header">
            <h3>Canciones <span class="badge">{{ playlist.songs.length }}</span></h3>
            <button class="btn-secondary" @click="showAddSong = !showAddSong">
              {{ showAddSong ? 'Cancelar' : '+ Agregar canción' }}
            </button>
          </div>

          <!-- Formulario agregar canción -->
          <form v-if="showAddSong" class="card form-card" @submit.prevent="addSong">
            <div class="form-grid">
              <div class="form-group">
                <label>Título</label>
                <input v-model="songForm.titulo" type="text" placeholder="Título" :disabled="addingSong" required />
              </div>
              <div class="form-group">
                <label>Artista</label>
                <input v-model="songForm.artista" type="text" placeholder="Artista" :disabled="addingSong" required />
              </div>
              <div class="form-group">
                <label>Álbum</label>
                <input v-model="songForm.album" type="text" placeholder="Álbum" :disabled="addingSong" required />
              </div>
              <div class="form-group">
                <label>Año</label>
                <input v-model="songForm.anno" type="text" placeholder="Ej. 1975" :disabled="addingSong" required />
              </div>
              <div class="form-group">
                <label>Género</label>
                <!-- Cuando Spotify no está disponible, se muestra un input de texto libre -->
                <select
                  v-if="genres.length > 0"
                  v-model="songForm.genero"
                  :disabled="addingSong || loadingGenres"
                  required
                >
                  <option value="" disabled>
                    {{ loadingGenres ? 'Cargando géneros…' : 'Selecciona un género' }}
                  </option>
                  <option v-for="g in genres" :key="g" :value="g">{{ g }}</option>
                </select>
                <input
                  v-else
                  v-model="songForm.genero"
                  type="text"
                  placeholder="Ej. rock, pop, jazz…"
                  :disabled="addingSong"
                  required
                />
                <span v-if="genresError" class="field-error">{{ genresError }}</span>
              </div>
            </div>
            <p v-if="addSongError" class="error-msg">{{ addSongError }}</p>
            <div class="form-actions">
              <button
                type="submit"
                class="btn-primary"
                :disabled="addingSong || !isSongFormValid"
              >
                {{ addingSong ? 'Agregando…' : 'Agregar canción' }}
              </button>
            </div>
          </form>

          <!-- Lista vacía -->
          <div v-if="playlist.songs.length === 0 && !showAddSong" class="state-message">
            Esta lista no tiene canciones aún.
          </div>

          <!-- Tabla de canciones -->
          <ul v-else-if="playlist.songs.length > 0" class="songs-list">
            <li
              v-for="song in playlist.songs"
              :key="song.id"
              class="song-row card"
            >
              <div class="song-info">
                <span class="song-title">{{ song.titulo }}</span>
                <span class="song-meta">{{ song.artista }} · {{ song.album }} · {{ song.anno }}</span>
                <span class="song-genre">{{ song.genero }}</span>
              </div>
              <button
                class="btn-icon-danger"
                :disabled="deletingSongId === song.id"
                :title="`Eliminar ${song.titulo}`"
                @click="deleteSong(song.id, song.titulo)"
              >
                {{ deletingSongId === song.id ? '…' : '✕' }}
              </button>
            </li>
          </ul>
        </section>

        <!-- Sección: Recomendaciones IA -->
        <section class="section">
          <div class="section-header">
            <h3>Recomendaciones IA</h3>
            <button
              class="btn-ai"
              :disabled="loadingRecs"
              @click="fetchRecommendations"
            >
              <span v-if="loadingRecs">Consultando IA…</span>
              <span v-else>✦ Obtener recomendaciones</span>
            </button>
          </div>

          <!-- Error IA (503) -->
          <div v-if="recsError" class="error-banner ai-error">
            <strong>Servicio de IA no disponible</strong>
            <span>{{ recsError }}</span>
          </div>

          <!-- Estado vacío antes de solicitar -->
          <div v-else-if="recommendations === null" class="state-message">
            Pulsa el botón para obtener sugerencias basadas en tu lista.
          </div>

          <!-- Recomendaciones -->
          <ul v-else-if="recommendations.length > 0" class="recs-list">
            <li v-for="(rec, i) in recommendations" :key="i" class="rec-row card">
              <span class="rec-num">{{ i + 1 }}</span>
              <div class="rec-info">
                <span class="rec-title">{{ rec.titulo }}</span>
                <span class="rec-meta">{{ rec.artista }}</span>
              </div>
              <span class="rec-genre">{{ rec.genero }}</span>
            </li>
          </ul>

          <!-- Lista vacía de recomendaciones -->
          <div v-else class="state-message">
            No se encontraron recomendaciones para esta lista.
          </div>
        </section>
      </template>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import api from '@/api/axios'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

// El router decodifica automáticamente el path param
const listName = route.params.listName

const playlist = ref(null)
const loading = ref(false)
const loadError = ref('')

const genres = ref([])
const loadingGenres = ref(false)
const genresError = ref('')

const showAddSong = ref(false)
const songForm = ref({ titulo: '', artista: '', album: '', anno: '', genero: '' })
const addingSong = ref(false)
const addSongError = ref('')

const deletingSongId = ref(null)

const recommendations = ref(null)
const loadingRecs = ref(false)
const recsError = ref('')

const isSongFormValid = computed(() =>
  songForm.value.titulo.trim() &&
  songForm.value.artista.trim() &&
  songForm.value.album.trim() &&
  songForm.value.anno.trim() &&
  songForm.value.genero
)

onMounted(async () => {
  await Promise.all([fetchPlaylist(), fetchGenres()])
})

async function fetchPlaylist() {
  loading.value = true
  loadError.value = ''
  try {
    const { data } = await api.get(`/lists/${encodeURIComponent(listName)}`)
    playlist.value = data
  } catch (err) {
    const status = err.response?.status
    if (status === 404) {
      loadError.value = 'Lista no encontrada.'
    } else {
      loadError.value = 'No se pudo cargar la lista. Inténtalo de nuevo.'
    }
  } finally {
    loading.value = false
  }
}

async function fetchGenres() {
  loadingGenres.value = true
  genresError.value = ''
  try {
    const { data } = await api.get('/spotify/genres')
    genres.value = data
  } catch {
    genresError.value = 'Géneros no disponibles. Ingresa uno manualmente si necesitas.'
    // Si Spotify no está disponible, permitimos entrada libre igualmente
  } finally {
    loadingGenres.value = false
  }
}

async function addSong() {
  addSongError.value = ''
  addingSong.value = true
  try {
    const { data } = await api.post(`/lists/${encodeURIComponent(listName)}/songs`, {
      titulo: songForm.value.titulo.trim(),
      artista: songForm.value.artista.trim(),
      album: songForm.value.album.trim(),
      anno: songForm.value.anno.trim(),
      genero: songForm.value.genero
    })
    playlist.value.songs.push(data)
    songForm.value = { titulo: '', artista: '', album: '', anno: '', genero: '' }
    showAddSong.value = false
  } catch (err) {
    const status = err.response?.status
    if (status === 400) {
      addSongError.value = 'Todos los campos son obligatorios.'
    } else if (status === 404) {
      addSongError.value = 'La lista no fue encontrada.'
    } else {
      addSongError.value = 'Error al agregar la canción. Inténtalo de nuevo.'
    }
  } finally {
    addingSong.value = false
  }
}

async function deleteSong(songId, songTitulo) {
  if (!confirm(`¿Eliminar "${songTitulo}" de la lista?`)) return
  deletingSongId.value = songId
  try {
    await api.delete(`/lists/${encodeURIComponent(listName)}/songs/${songId}`)
    playlist.value.songs = playlist.value.songs.filter((s) => s.id !== songId)
  } catch (err) {
    const status = err.response?.status
    if (status === 404) {
      playlist.value.songs = playlist.value.songs.filter((s) => s.id !== songId)
    } else {
      alert('Error al eliminar la canción. Inténtalo de nuevo.')
    }
  } finally {
    deletingSongId.value = null
  }
}

async function fetchRecommendations() {
  recsError.value = ''
  loadingRecs.value = true
  try {
    const { data } = await api.get(`/lists/${encodeURIComponent(listName)}/recommendations`)
    recommendations.value = data
  } catch (err) {
    const status = err.response?.status
    if (status === 503) {
      recsError.value = 'El servicio de recomendaciones no está disponible en este momento. Inténtalo más tarde.'
    } else if (status === 404) {
      recsError.value = 'Lista no encontrada.'
    } else {
      recsError.value = 'Error al obtener recomendaciones. Inténtalo de nuevo.'
    }
    recommendations.value = null
  } finally {
    loadingRecs.value = false
  }
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
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
}

.container {
  max-width: 860px;
  margin: 0 auto;
  padding: 1.5rem 1rem;
}

.card {
  background: white;
  border-radius: 10px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}

/* Cabecera playlist */
.playlist-header {
  padding: 1.25rem 1.5rem;
  margin-bottom: 1.5rem;
}

.playlist-meta h2 {
  font-size: 1.375rem;
  font-weight: 700;
  color: #1a1a2e;
}

.playlist-meta p {
  color: #6b7280;
  font-size: 0.875rem;
  margin-top: 0.25rem;
}

/* Secciones */
.section {
  margin-bottom: 2rem;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.875rem;
}

.section-header h3 {
  font-size: 1rem;
  font-weight: 600;
  color: #1a1a2e;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.badge {
  background: #e5e7eb;
  color: #6b7280;
  border-radius: 99px;
  padding: 0.1rem 0.5rem;
  font-size: 0.75rem;
  font-weight: 600;
}

/* Form */
.form-card {
  padding: 1.25rem 1.5rem;
  margin-bottom: 1rem;
}

.form-grid {
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

.field-error {
  font-size: 0.75rem;
  color: #f59e0b;
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
  margin-top: 0.75rem;
}

.error-banner {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 8px;
  padding: 0.875rem 1rem;
  color: #dc2626;
  font-size: 0.875rem;
}

.error-banner strong {
  font-weight: 600;
}

.ai-error {
  background: #fffbeb;
  border-color: #fde68a;
  color: #92400e;
}

.state-message {
  text-align: center;
  color: #9ca3af;
  padding: 2rem 1rem;
  font-size: 0.9375rem;
}

/* Canciones */
.songs-list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.song-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem 1rem;
  gap: 0.75rem;
  transition: box-shadow 0.15s;
}

.song-row:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.song-info {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  min-width: 0;
}

.song-title {
  font-weight: 600;
  font-size: 0.9rem;
  color: #1a1a2e;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.song-meta {
  font-size: 0.8rem;
  color: #6b7280;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.song-genre {
  font-size: 0.75rem;
  color: #9ca3af;
}

/* Recomendaciones */
.recs-list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.rec-row {
  display: flex;
  align-items: center;
  padding: 0.75rem 1rem;
  gap: 0.875rem;
}

.rec-num {
  font-size: 0.8125rem;
  font-weight: 700;
  color: #d1d5db;
  min-width: 1.25rem;
  text-align: right;
}

.rec-info {
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
  flex: 1;
  min-width: 0;
}

.rec-title {
  font-weight: 600;
  font-size: 0.9rem;
  color: #1a1a2e;
}

.rec-meta {
  font-size: 0.8rem;
  color: #6b7280;
}

.rec-genre {
  font-size: 0.75rem;
  color: #9ca3af;
  background: #f3f4f6;
  padding: 0.2rem 0.5rem;
  border-radius: 99px;
  flex-shrink: 0;
}

/* Botones */
.btn-primary {
  padding: 0.5rem 1rem;
  background: #0f3460;
  color: white;
  border: none;
  border-radius: 7px;
  font-weight: 500;
  font-size: 0.875rem;
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
  font-size: 0.875rem;
  font-weight: 500;
  transition: background 0.15s;
}

.btn-secondary:hover:not(:disabled) {
  background: #e5e7eb;
}

.btn-ai {
  padding: 0.5rem 1rem;
  background: linear-gradient(135deg, #7c3aed, #4f46e5);
  color: white;
  border: none;
  border-radius: 7px;
  font-size: 0.875rem;
  font-weight: 500;
  transition: opacity 0.15s;
}

.btn-ai:hover:not(:disabled) {
  opacity: 0.9;
}

.btn-ai:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-icon-danger {
  width: 28px;
  height: 28px;
  background: transparent;
  color: #9ca3af;
  border: 1.5px solid #e5e7eb;
  border-radius: 6px;
  font-size: 0.75rem;
  transition: color 0.15s, border-color 0.15s, background 0.15s;
  flex-shrink: 0;
}

.btn-icon-danger:hover:not(:disabled) {
  color: #dc2626;
  border-color: #fca5a5;
  background: #fef2f2;
}

.btn-icon-danger:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.btn-back {
  padding: 0.375rem 0.75rem;
  background: transparent;
  color: #d1d5db;
  border: 1.5px solid #4b5563;
  border-radius: 7px;
  font-size: 0.8125rem;
  transition: color 0.15s, border-color 0.15s;
}

.btn-back:hover {
  color: white;
  border-color: #9ca3af;
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
