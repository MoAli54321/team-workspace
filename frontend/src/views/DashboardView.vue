<script setup>
import WorkspaceShell from '../components/WorkspaceShell.vue'
import UiIcon from '../components/UiIcon.vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { requestTeam, requestTeams } from '../api/teams.js'
import { useAuthStore } from '../stores/auth.js'

const router = useRouter()
const authStore = useAuthStore()

// Die API liefert nur Teams, in denen der angemeldete Benutzer Mitglied ist.
const teams = ref([])
const loading = ref(true)
const submitting = ref(false)
const deleting = ref(false)
const error = ref('')
const deleteError = ref('')
const notice = ref('')
const pendingTeam = ref(null)
const name = ref('')
const description = ref('')
let loadVersion = 0

// Dieser Weg greift sowohl bei Logout als auch nach einer vom Backend abgelehnten Sitzung.
watch(() => authStore.token, token => {
  if (!token) {
    loadVersion++
    teams.value = []
    pendingTeam.value = null
    router.replace({ name: 'login' })
  }
})

async function loadTeams() {
  const version = ++loadVersion
  loading.value = true
  error.value = ''

  try {
    const response = await requestTeams()
    if (!response.ok) throw new Error('Teams konnten nicht geladen werden.')
    const data = await response.json()
    if (version === loadVersion) teams.value = data
  } catch (err) {
    if (version !== loadVersion) return
    teams.value = []
    error.value = err instanceof Error ? err.message : 'Teams konnten nicht geladen werden.'
  } finally {
    if (version === loadVersion) loading.value = false
  }
}

// Der laufende Speichervorgang sperrt das Formular und verhindert doppelte Übermittlungen.
async function createTeam() {
  if (!name.value.trim() || submitting.value) {
    if (!name.value.trim()) error.value = 'Bitte einen Teamnamen eingeben.'
    return
  }

  submitting.value = true
  error.value = ''
  notice.value = ''

  try {
    const response = await requestTeams({
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: name.value.trim(),
        description: description.value.trim(),
      }),
    })

    if (response.status === 400) throw new Error('Bitte prüfe den Teamnamen und die Beschreibung (je höchstens 255 Zeichen).')
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

// Der Dialog zeigt vor dem endgültigen Löschen genau, welche Teamdaten betroffen sind.
function askToDeleteTeam(team) {
  pendingTeam.value = team
  deleteError.value = ''
}

function cancelTeamDeletion() {
  if (!deleting.value) pendingTeam.value = null
}

async function deleteTeam() {
  if (!pendingTeam.value || deleting.value) return

  const team = pendingTeam.value
  deleting.value = true
  deleteError.value = ''

  try {
    const response = await requestTeam(team.id, { method: 'DELETE' })

    if (response.status === 403) {
      throw new Error('Nur der Besitzer darf dieses Team löschen.')
    }
    if (response.status === 404) {
      throw new Error('Das Team wurde bereits gelöscht oder nicht gefunden.')
    }
    if (!response.ok) {
      throw new Error('Team konnte nicht gelöscht werden.')
    }

    pendingTeam.value = null
    notice.value = `„${team.name}“ wurde vollständig gelöscht.`
    await loadTeams()
  } catch (err) {
    deleteError.value = err instanceof Error ? err.message : 'Team konnte nicht gelöscht werden.'
  } finally {
    deleting.value = false
  }
}

onMounted(loadTeams)
onBeforeUnmount(() => { loadVersion++ })
</script>

<template>
  <WorkspaceShell>
    <header class="page-header">
      <div>
        <p class="eyebrow">Dein Arbeitsplatz</p>
        <h1>Meine Teams</h1>
        <p class="subtitle">Hallo {{ authStore.username }}. Öffne ein Team, um Projekte und Mitglieder zu verwalten.</p>
      </div>
      <span v-if="!loading" class="page-counter"><UiIcon name="users" />{{ teams.length }} {{ teams.length === 1 ? 'Team' : 'Teams' }}</span>
    </header>

    <a class="button mobile-create-link" href="#create-heading"><UiIcon name="plus" />Neues Team</a>

    <p v-if="notice" class="success team-notice" role="status">{{ notice }}</p>
    <p v-if="error" class="error" role="alert">{{ error }}</p>

    <!-- Übersicht und Erstellformular teilen sich am Desktop den Platz und stehen mobil untereinander. -->
    <div class="workspace-columns">
      <section class="collection" aria-labelledby="teams-heading" :aria-busy="loading">
        <div class="section-heading">
          <div class="section-title"><h2 id="teams-heading">Deine Arbeitsbereiche</h2><span v-if="!loading" class="count-badge">{{ teams.length }}</span></div>
          <button type="button" class="text-button" :disabled="loading" @click="loadTeams"><UiIcon name="refresh" />Aktualisieren</button>
        </div>

        <div v-if="loading" class="empty-state" role="status"><p>Teams werden geladen …</p></div>
        <div v-else-if="!error && teams.length === 0" class="empty-state">
          <span class="entity-icon"><UiIcon name="users" /></span>
          <h3>Noch keine Teams</h3>
          <p>Erstelle ein Team oder lass dich zu einem bestehenden Team hinzufügen.</p>
        </div>

        <div v-else class="card-grid">
          <article v-for="(team, index) in teams" :key="team.id" class="workspace-card team-card">
            <RouterLink class="team-card-link"
              :to="{ name: 'team-projects', params: { teamId: team.id }, query: { teamName: team.name } }">
              <div class="card-top">
                <span class="entity-icon" :class="'tone-' + (index % 4)">{{ team.name.slice(0, 2).toUpperCase() }}</span>
                <span class="card-kind">{{ team.role === 'OWNER' ? 'Besitzer' : 'Mitglied' }}</span>
              </div>
              <h3>{{ team.name }}</h3>
              <p class="card-description">{{ team.description || 'Keine Beschreibung' }}</p>
              <div class="card-footer"><span class="card-action">Team öffnen</span><UiIcon name="arrow" /></div>
            </RouterLink>
            <div v-if="team.role === 'OWNER'" class="team-card-actions">
              <button type="button" class="text-button destructive-link" :aria-label="'Team löschen: ' + team.name" @click="askToDeleteTeam(team)">
                <UiIcon name="trash" />Team löschen
              </button>
            </div>
          </article>
        </div>
      </section>

      <aside class="panel create-panel" aria-labelledby="create-heading">
        <span class="panel-icon"><UiIcon name="plus" /></span>
        <h2 id="create-heading">Neues Team erstellen</h2>
        <p class="panel-intro">Ein gemeinsamer Arbeitsbereich für dich und dein Team.</p>
        <form class="stack-form" :aria-busy="submitting" @submit.prevent="createTeam">
          <label>Teamname
            <input v-model="name" type="text" placeholder="z. B. Design & Entwicklung" :disabled="submitting" required maxlength="255" />
          </label>
          <label><span class="label-row">Beschreibung<span class="optional">Optional</span></span>
            <textarea v-model="description" placeholder="Woran arbeitet ihr gemeinsam?" :disabled="submitting" maxlength="255"></textarea>
          </label>
          <button type="submit" class="button" :disabled="submitting"><UiIcon name="plus" />{{ submitting ? 'Wird erstellt …' : 'Team erstellen' }}</button>
        </form>
        <p class="form-note"><UiIcon name="check" />Du wirst automatisch Besitzer dieses Teams.</p>
      </aside>
    </div>

    <ConfirmDialog v-if="pendingTeam"
      title="Team endgültig löschen?"
      :message="`„${pendingTeam.name}“ sowie alle Projekte, Aufgaben und Mitgliedschaften dieses Teams werden gelöscht. Die Benutzerkonten bleiben erhalten.`"
      confirm-label="Team löschen"
      :busy="deleting"
      :error="deleteError"
      @confirm="deleteTeam"
      @cancel="cancelTeamDeletion" />
  </WorkspaceShell>
</template>
