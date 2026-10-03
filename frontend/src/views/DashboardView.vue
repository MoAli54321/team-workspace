<script setup>
import { onMounted, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { requestTeams } from '../api/teams.js'
import { useAuthStore } from '../stores/auth.js'

const router = useRouter()
const authStore = useAuthStore()

const teams = ref([])
const loading = ref(true)
const submitting = ref(false)
const error = ref('')
const name = ref('')
const description = ref('')

function logout() {
  authStore.logout()
}

watch(() => authStore.token, token => {
  if (!token) {
    teams.value = []
    router.replace({ name: 'login' })
  }
})

async function loadTeams() {
  loading.value = true
  error.value = ''

  try {
    const response = await requestTeams()
    if (!response.ok) throw new Error('Teams konnten nicht geladen werden.')
    teams.value = await response.json()
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Teams konnten nicht geladen werden.'
  } finally {
    loading.value = false
  }
}

async function createTeam() {
  if (!name.value.trim() || submitting.value) {
    if (!name.value.trim()) error.value = 'Bitte einen Teamnamen eingeben.'
    return
  }

  submitting.value = true
  error.value = ''

  try {
    const response = await requestTeams({
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: name.value.trim(),
        description: description.value.trim(),
      }),
    })

    if (!response.ok) throw new Error('Team konnte nicht erstellt werden.')

    name.value = ''
    description.value = ''
    await loadTeams()
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Team konnte nicht erstellt werden.'
  } finally {
    submitting.value = false
  }
}

onMounted(loadTeams)
</script>

<template>
  <main class="workspace-page">
    <header class="page-header">
      <div>
        <p class="eyebrow">Team Workspace</p>
        <h1>Meine Teams</h1>
        <p class="subtitle">Willkommen, {{ authStore.username }}.</p>
      </div>
      <button type="button" class="secondary-button" @click="logout">Logout</button>
    </header>

    <section class="panel">
      <div class="section-heading">
        <div>
          <p class="eyebrow">Neuer Arbeitsbereich</p>
          <h2>Team erstellen</h2>
        </div>
      </div>

      <form class="stack-form" :aria-busy="submitting" @submit.prevent="createTeam">
        <label>
          Teamname
          <input v-model="name" type="text" placeholder="z. B. Software Projekt" :disabled="submitting" required />
        </label>

        <label>
          Beschreibung
          <textarea v-model="description" placeholder="Woran arbeitet dieses Team?" :disabled="submitting"></textarea>
        </label>

        <button type="submit" :disabled="submitting">
          {{ submitting ? 'Team wird erstellt …' : 'Team erstellen' }}
        </button>
      </form>
    </section>

    <p v-if="error" class="error" role="alert">{{ error }}</p>

    <section>
      <div class="section-heading">
        <div>
          <p class="eyebrow">Übersicht</p>
          <h2>Teams</h2>
        </div>
        <button type="button" class="text-button" :disabled="loading" @click="loadTeams">Aktualisieren</button>
      </div>

      <p v-if="loading" class="empty-state">Teams werden geladen …</p>
      <p v-else-if="teams.length === 0" class="empty-state">
        Du bist noch in keinem Team. Erstelle oben dein erstes Team.
      </p>

      <div v-else class="card-grid">
        <RouterLink
          v-for="team in teams"
          :key="team.id"
          class="workspace-card"
          :to="{ name: 'team-projects', params: { teamId: team.id }, query: { teamName: team.name } }"
        >
          <div>
            <p class="card-label">Team</p>
            <h3>{{ team.name }}</h3>
            <p>{{ team.description || 'Keine Beschreibung' }}</p>
          </div>
          <span class="card-action">Projekte öffnen →</span>
        </RouterLink>
      </div>
    </section>
  </main>
</template>

<style scoped>
.workspace-page {
  width: min(960px, 100%);
  margin: 0 auto;
  padding: 48px 20px 72px;
}

.page-header,
.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.page-header { margin-bottom: 32px; }
.section-heading { margin-bottom: 18px; }

h1 { font-size: clamp(2rem, 6vw, 3.25rem); line-height: 1.1; }
h2 { font-size: 1.5rem; }

.eyebrow,
.card-label {
  color: #16805f;
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.subtitle { margin-top: 6px; color: var(--color-text-muted); }

.panel {
  padding: 24px;
  margin-bottom: 32px;
  border: 1px solid var(--color-border);
  border-radius: 16px;
  background: var(--color-background-soft);
}

.stack-form { display: grid; gap: 16px; }
.stack-form label { display: grid; gap: 6px; font-weight: 600; }

input,
textarea,
button {
  padding: 11px 14px;
  border: 1px solid var(--color-border-hover);
  border-radius: 9px;
  font: inherit;
}

input,
textarea { color: var(--color-text); background: var(--color-background); }
textarea { min-height: 92px; resize: vertical; }

button {
  width: fit-content;
  color: white;
  background: #16805f;
  cursor: pointer;
}

button:disabled { cursor: wait; opacity: 0.65; }
.secondary-button { color: var(--color-text); background: transparent; }
.text-button { padding: 6px; border: 0; color: #16805f; background: transparent; }

.card-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 16px; }

.workspace-card {
  display: flex;
  min-height: 190px;
  padding: 22px;
  border: 1px solid var(--color-border);
  border-radius: 14px;
  flex-direction: column;
  justify-content: space-between;
  color: var(--color-text);
  background: var(--color-background-soft);
}

.workspace-card:hover { border-color: #16805f; background: var(--color-background-mute); }
.workspace-card h3 { margin: 7px 0; font-size: 1.25rem; }
.workspace-card p { color: var(--color-text-muted); }
.card-action { margin-top: 24px; color: #16805f; font-weight: 700; }

.empty-state { padding: 28px; border: 1px dashed var(--color-border-hover); border-radius: 12px; text-align: center; }
.error { padding: 12px 14px; margin-bottom: 24px; border-radius: 9px; color: #a51d1d; background: #fff0f0; }

@media (max-width: 600px) {
  .page-header,
  .section-heading { align-items: flex-start; flex-direction: column; }
}
</style>
