<script setup>
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { requestProjects } from '../api/projects.js'
import { requestTeams } from '../api/teams.js'
import { useAuthStore } from '../stores/auth.js'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const teamId = computed(() => route.params.teamId)
const team = ref(null)
const projects = ref([])
const loading = ref(true)
const submitting = ref(false)
const error = ref('')
const name = ref('')
const description = ref('')

const teamName = computed(() => team.value?.name || route.query.teamName || `Team #${teamId.value}`)

function logout() {
  authStore.logout()
}

watch(() => authStore.token, token => {
  if (!token) {
    projects.value = []
    router.replace({ name: 'login' })
  }
})

async function loadPage() {
  loading.value = true
  error.value = ''

  try {
    const [teamsResponse, projectsResponse] = await Promise.all([
      requestTeams(),
      requestProjects(teamId.value),
    ])

    if (projectsResponse.status === 403) throw new Error('Du hast keinen Zugriff auf dieses Team.')
    if (!teamsResponse.ok || !projectsResponse.ok) throw new Error('Projekte konnten nicht geladen werden.')

    const teams = await teamsResponse.json()
    team.value = teams.find(item => String(item.id) === String(teamId.value)) ?? null
    projects.value = await projectsResponse.json()

    if (!team.value) throw new Error('Team wurde nicht gefunden oder ist nicht mehr zugänglich.')
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Projekte konnten nicht geladen werden.'
  } finally {
    loading.value = false
  }
}

async function createProject() {
  if (!name.value.trim() || submitting.value) {
    if (!name.value.trim()) error.value = 'Bitte einen Projektnamen eingeben.'
    return
  }

  submitting.value = true
  error.value = ''

  try {
    const response = await requestProjects(teamId.value, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: name.value.trim(),
        description: description.value.trim(),
      }),
    })

    if (response.status === 403) throw new Error('Du bist kein Mitglied dieses Teams.')
    if (!response.ok) throw new Error('Projekt konnte nicht erstellt werden.')

    name.value = ''
    description.value = ''
    await loadPage()
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Projekt konnte nicht erstellt werden.'
  } finally {
    submitting.value = false
  }
}

watch(() => route.params.teamId, loadPage, { immediate: true })
</script>

<template>
  <main class="workspace-page">
    <nav class="breadcrumbs" aria-label="Breadcrumb">
      <RouterLink :to="{ name: 'dashboard' }">Meine Teams</RouterLink>
      <span aria-hidden="true">/</span>
      <span>{{ teamName }}</span>
    </nav>

    <header class="page-header">
      <div>
        <p class="eyebrow">Team</p>
        <h1>{{ teamName }}</h1>
        <p class="subtitle">Projekte dieses Teams verwalten.</p>
      </div>
      <button type="button" class="secondary-button" @click="logout">Logout</button>
    </header>

    <section class="panel">
      <p class="eyebrow">Neues Vorhaben</p>
      <h2>Projekt erstellen</h2>

      <form class="stack-form" :aria-busy="submitting" @submit.prevent="createProject">
        <label>
          Projektname
          <input v-model="name" type="text" placeholder="z. B. Team Workspace Webapp" :disabled="submitting" required />
        </label>

        <label>
          Beschreibung
          <textarea v-model="description" placeholder="Was soll in diesem Projekt entstehen?" :disabled="submitting"></textarea>
        </label>

        <button type="submit" :disabled="submitting">
          {{ submitting ? 'Projekt wird erstellt …' : 'Projekt erstellen' }}
        </button>
      </form>
    </section>

    <p v-if="error" class="error" role="alert">{{ error }}</p>

    <section>
      <div class="section-heading">
        <div>
          <p class="eyebrow">Übersicht</p>
          <h2>Projekte</h2>
        </div>
        <button type="button" class="text-button" :disabled="loading" @click="loadPage">Aktualisieren</button>
      </div>

      <p v-if="loading" class="empty-state">Projekte werden geladen …</p>
      <p v-else-if="projects.length === 0" class="empty-state">
        Dieses Team hat noch keine Projekte.
      </p>

      <div v-else class="card-grid">
        <RouterLink
          v-for="project in projects"
          :key="project.id"
          class="workspace-card"
          :to="{
            name: 'project-tasks',
            params: { projectId: project.id },
            query: { projectName: project.name, teamId: teamId, teamName: teamName },
          }"
        >
          <div>
            <p class="card-label">Projekt</p>
            <h3>{{ project.name }}</h3>
            <p>{{ project.description || 'Keine Beschreibung' }}</p>
          </div>
          <span class="card-action">Tasks öffnen →</span>
        </RouterLink>
      </div>
    </section>
  </main>
</template>

<style scoped>
.workspace-page { width: min(960px, 100%); margin: 0 auto; padding: 36px 20px 72px; }
.breadcrumbs { display: flex; gap: 8px; margin-bottom: 28px; color: var(--color-text-muted); }
.breadcrumbs a { padding: 0; color: #16805f; }
.page-header,
.section-heading { display: flex; align-items: center; justify-content: space-between; gap: 20px; }
.page-header { margin-bottom: 32px; }
.section-heading { margin-bottom: 18px; }
h1 { font-size: clamp(2rem, 6vw, 3.25rem); line-height: 1.1; }
h2 { margin-bottom: 16px; font-size: 1.5rem; }
.eyebrow,
.card-label { color: #16805f; font-size: 0.75rem; font-weight: 700; letter-spacing: 0.12em; text-transform: uppercase; }
.subtitle { margin-top: 6px; color: var(--color-text-muted); }
.panel { padding: 24px; margin-bottom: 32px; border: 1px solid var(--color-border); border-radius: 16px; background: var(--color-background-soft); }
.stack-form { display: grid; gap: 16px; }
.stack-form label { display: grid; gap: 6px; font-weight: 600; }
input,
textarea,
button { padding: 11px 14px; border: 1px solid var(--color-border-hover); border-radius: 9px; font: inherit; }
input,
textarea { color: var(--color-text); background: var(--color-background); }
textarea { min-height: 92px; resize: vertical; }
button { width: fit-content; color: white; background: #16805f; cursor: pointer; }
button:disabled { cursor: wait; opacity: 0.65; }
.secondary-button { color: var(--color-text); background: transparent; }
.text-button { padding: 6px; border: 0; color: #16805f; background: transparent; }
.card-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 16px; }
.workspace-card { display: flex; min-height: 190px; padding: 22px; border: 1px solid var(--color-border); border-radius: 14px; flex-direction: column; justify-content: space-between; color: var(--color-text); background: var(--color-background-soft); }
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
