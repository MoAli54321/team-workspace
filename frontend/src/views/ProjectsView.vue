<script setup>
import WorkspaceShell from '../components/WorkspaceShell.vue'
import UiIcon from '../components/UiIcon.vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { requestProject, requestProjects } from '../api/projects.js'
import { requestTeamMember, requestTeamMembers, requestTeams } from '../api/teams.js'
import { useAuthStore } from '../stores/auth.js'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
// Diese Seite bündelt Projekte und Mitglieder des Teams aus der URL.
const teamId = computed(() => route.params.teamId)
const team = ref(null)
const projects = ref([])
const members = ref([])
const loading = ref(true)
const submitting = ref(false)
const addingMember = ref(false)
const error = ref('')
const memberError = ref('')
const notice = ref('')
const name = ref('')
const description = ref('')
const identifier = ref('')
const pendingAction = ref(null)
const deleting = ref(false)
const actionError = ref('')
// Jede Ladeanfrage erhält eine Nummer. Nur das Ergebnis der neuesten Anfrage wird angezeigt.
let loadVersion = 0

const teamName = computed(() => team.value?.name || route.query.teamName || 'Team #' + teamId.value)
// Der Tab steht in der URL, damit Neuladen und Zurücknavigation die Auswahl erhalten.
const membersTab = computed(() => route.query.tab === 'members')
const myMembership = computed(() => members.value.find(member => String(member.userId) === String(authStore.userId)))
// Die Rolle steuert die sichtbaren Aktionen. Das Backend prüft die Berechtigung bei jeder Anfrage erneut.
const isOwner = computed(() => myMembership.value?.role === 'OWNER')
const dialogTitle = computed(() => pendingAction.value?.type === 'project' ? 'Projekt löschen?' : 'Mitglied entfernen?')
const dialogMessage = computed(() => {
  const action = pendingAction.value
  if (!action) return ''
  return action.type === 'project'
    ? '„' + action.name + '“ und alle zugehörigen Aufgaben werden endgültig gelöscht. Diese Aktion kann nicht rückgängig gemacht werden.'
    : '„' + action.name + '“ wird aus diesem Team entfernt und verliert den Zugriff auf dessen Projekte und Aufgaben. Der Benutzerkonto bleibt bestehen.'
})

function tabTarget(tab) {
  return { name: 'team-projects', params: { teamId: teamId.value }, query: { teamName: teamName.value, ...(tab === 'members' ? { tab } : {}) } }
}

// Eine Erfolgsmeldung aus dem Projektbereich soll im Mitgliederbereich nicht weiter angezeigt werden.
watch(membersTab, () => {
  notice.value = ''
  memberError.value = ''
})

watch(() => authStore.token, token => {
  if (!token) {
    // Bereits laufende Anfragen dürfen nach dem Logout keine Teamdaten mehr in die Ansicht schreiben.
    loadVersion++
    team.value = null
    projects.value = []
    members.value = []
    pendingAction.value = null
    router.replace({ name: 'login' })
  }
})

async function loadPage() {
  const version = ++loadVersion
  loading.value = true
  error.value = ''
  try {
    // Projekte und Mitglieder werden zusammen geladen, damit Listen und Rollen zum selben Team gehören.
    const responses = await Promise.all([requestTeams(), requestProjects(teamId.value), requestTeamMembers(teamId.value)])
    if (responses.some(response => response.status === 403)) throw new Error('Du hast keinen Zugriff auf dieses Team.')
    if (responses.some(response => response.status === 404)) throw new Error('Das Team wurde nicht gefunden.')
    if (responses.some(response => !response.ok)) throw new Error('Die Teamdaten konnten nicht geladen werden. Bitte versuche es erneut.')
    const [teams, projectData, memberData] = await Promise.all(responses.map(response => response.json()))
    // Bei einem schnellen Teamwechsel kann eine ältere Antwort später eintreffen; sie wird verworfen.
    if (version !== loadVersion) return
    const currentTeam = teams.find(item => String(item.id) === String(teamId.value))
    if (!currentTeam) throw new Error('Dieses Team ist nicht mehr zugänglich.')
    team.value = currentTeam
    projects.value = projectData
    members.value = memberData
  } catch (err) {
    if (version !== loadVersion) return
    team.value = null
    projects.value = []
    members.value = []
    error.value = err instanceof Error ? err.message : 'Die Teamdaten konnten nicht geladen werden.'
  } finally {
    if (version === loadVersion) loading.value = false
  }
}

async function createProject() {
  if (!name.value.trim() || submitting.value) return
  // Die Ziel-ID wird vor dem Request festgehalten. Ein Teamwechsel soll das Ergebnis nicht falsch zuordnen.
  const currentTeam = teamId.value
  submitting.value = true
  error.value = ''
  notice.value = ''
  try {
    const response = await requestProjects(currentTeam, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: name.value.trim(), description: description.value.trim() }),
    })
    if (currentTeam !== teamId.value) return
    if (response.status === 403) throw new Error('Du bist kein Mitglied dieses Teams.')
    if (response.status === 400) throw new Error('Bitte prüfe den Projektnamen und die Beschreibung (je höchstens 255 Zeichen).')
    if (!response.ok) throw new Error('Projekt konnte nicht erstellt werden.')
    name.value = ''
    description.value = ''
    notice.value = 'Das Projekt wurde erstellt.'
    await loadPage()
  } catch (err) {
    if (currentTeam === teamId.value) error.value = err instanceof Error ? err.message : 'Projekt konnte nicht erstellt werden.'
  } finally {
    submitting.value = false
  }
}

// Es wird ein vorhandener Account hinzugefügt. Die Rolle MEMBER vergibt das Backend selbst.
async function addMember() {
  if (!identifier.value.trim() || addingMember.value || !isOwner.value) return
  const currentTeam = teamId.value
  addingMember.value = true
  memberError.value = ''
  notice.value = ''
  try {
    const response = await requestTeamMembers(currentTeam, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ identifier: identifier.value.trim() }),
    })
    if (currentTeam !== teamId.value) return
    if (response.status === 409) throw new Error('Dieser Benutzer ist bereits Mitglied des Teams.')
    if (response.status === 404) throw new Error('Kein Benutzer gefunden. Die Person muss zuerst ein Konto erstellen.')
    if (response.status === 403) throw new Error('Nur der Teambesitzer darf Mitglieder hinzufügen.')
    if (!response.ok) throw new Error('Das Mitglied konnte nicht hinzugefügt werden. Bitte versuche es erneut.')
    identifier.value = ''
    notice.value = 'Das Mitglied wurde hinzugefügt und kann jetzt die Projekte und Aufgaben dieses Teams öffnen.'
    await loadPage()
  } catch (err) {
    if (currentTeam === teamId.value) memberError.value = err instanceof Error ? err.message : 'Mitglied konnte nicht hinzugefügt werden.'
  } finally {
    addingMember.value = false
  }
}

// Zunächst wird nur die Auswahl für den Bestätigungsdialog gespeichert; gelöscht wird hier noch nichts.
function confirmRemoval(type, item) {
  if (!isOwner.value || deleting.value) return
  actionError.value = ''
  pendingAction.value = {
    type, teamId: teamId.value,
    id: type === 'project' ? item.id : item.userId,
    name: type === 'project' ? item.name : item.username,
  }
}

// Erst die Bestätigung löst den DELETE-Request aus. Währenddessen verhindert deleting einen zweiten Request.
async function removeConfirmed() {
  const action = pendingAction.value
  if (!action || deleting.value || !isOwner.value || action.teamId !== teamId.value) return
  deleting.value = true
  actionError.value = ''
  notice.value = ''
  try {
    const response = action.type === 'project'
      ? await requestProject(action.teamId, action.id, { method: 'DELETE' })
      : await requestTeamMember(action.teamId, action.id, { method: 'DELETE' })
    if (action.teamId !== teamId.value) return
    if (response.status === 403) throw new Error('Nur der Teambesitzer darf diese Aktion ausführen.')
    if (response.status === 404) throw new Error('Der Eintrag existiert nicht mehr. Schließe den Dialog und aktualisiere die Liste.')
    if (response.status === 409) throw new Error('Der Teambesitzer kann nicht entfernt werden.')
    if (!response.ok) throw new Error('Die Aktion ist fehlgeschlagen. Bitte versuche es erneut.')
    pendingAction.value = null
    notice.value = action.type === 'project'
      ? '„' + action.name + '“ und seine Aufgaben wurden gelöscht.'
      : '„' + action.name + '“ wurde aus dem Team entfernt.'
    await loadPage()
  } catch (err) {
    if (action.teamId === teamId.value) actionError.value = err instanceof Error ? err.message : 'Die Aktion ist fehlgeschlagen.'
  } finally {
    deleting.value = false
  }
}

// Ein neues Team startet mit leeren Formularen und ohne Meldungen oder Auswahl des vorherigen Teams.
watch(teamId, () => {
  team.value = null
  projects.value = []
  members.value = []
  pendingAction.value = null
  notice.value = ''
  memberError.value = ''
  name.value = ''
  description.value = ''
  identifier.value = ''
  loadPage()
}, { immediate: true })

onBeforeUnmount(() => { loadVersion++ })
</script>

<template>
  <WorkspaceShell :breadcrumbs="[{ label: teamName }]">
    <header class="page-header">
      <div>
        <p class="eyebrow">Dein Team</p>
        <h1>{{ teamName }}</h1>
        <p class="subtitle">{{ team?.description || 'Projekte und Mitglieder dieses Teams.' }}</p>
      </div>
      <span v-if="myMembership" class="role-badge" :class="{ 'role-owner': isOwner }">{{ isOwner ? 'Du bist Besitzer' : 'Du bist Mitglied' }}</span>
    </header>

    <!-- Beide Bereiche gehören zum selben Team; die Mitgliederrechte gelten für alle seine Projekte. -->
    <nav class="team-tabs" aria-label="Teambereich">
      <RouterLink :to="tabTarget('projects')" :class="{ selected: !membersTab }" :aria-current="!membersTab ? 'page' : undefined">
        <UiIcon name="folder" />Projekte<span v-if="team" class="count-badge">{{ projects.length }}</span>
      </RouterLink>
      <RouterLink :to="tabTarget('members')" :class="{ selected: membersTab }" :aria-current="membersTab ? 'page' : undefined">
        <UiIcon name="users" />Mitglieder<span v-if="team" class="count-badge">{{ members.length }}</span>
      </RouterLink>
    </nav>

    <p v-if="notice" class="success team-notice" role="status">{{ notice }}</p>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <div v-if="loading" class="empty-state" role="status"><p>Team wird geladen …</p></div>
    <button v-else-if="!team" type="button" class="button button-secondary" @click="loadPage">Erneut versuchen</button>

    <template v-else-if="!membersTab">
      <a class="button mobile-create-link" href="#create-heading"><UiIcon name="plus" />Neues Projekt</a>
      <div class="workspace-columns">
        <section class="collection" aria-labelledby="projects-heading">
          <div class="section-heading">
            <div class="section-title"><h2 id="projects-heading">Projekte im Team</h2><span class="count-badge">{{ projects.length }}</span></div>
            <button type="button" class="text-button" @click="loadPage"><UiIcon name="refresh" />Aktualisieren</button>
          </div>
          <p v-if="!isOwner" class="section-note">Du kannst Projekte erstellen und Aufgaben bearbeiten. Projekte löschen kann der Teambesitzer.</p>
          <div v-if="projects.length === 0" class="empty-state">
            <span class="entity-icon"><UiIcon name="folder" /></span>
            <h3>Noch keine Projekte</h3>
            <p>Erstelle das erste Projekt für dieses Team.</p>
          </div>
          <div v-else class="card-grid">
            <article v-for="(project, index) in projects" :key="project.id" class="workspace-card project-card">
              <RouterLink class="project-card-link"
                :to="{ name: 'project-tasks', params: { projectId: project.id }, query: { projectName: project.name, teamId: teamId, teamName: teamName } }">
                <div class="card-top">
                  <span class="entity-icon" :class="'tone-' + (index % 4)"><UiIcon name="folder" /></span>
                  <span class="card-kind">Projekt</span>
                </div>
                <h3>{{ project.name }}</h3>
                <p class="card-description">{{ project.description || 'Keine Beschreibung' }}</p>
                <span class="project-open"><span>Aufgaben öffnen</span><UiIcon name="arrow" /></span>
              </RouterLink>
              <div v-if="isOwner" class="project-card-actions">
                <button type="button" class="text-button destructive-link" :aria-label="'Projekt löschen: ' + project.name"
                  @click="confirmRemoval('project', project)"><UiIcon name="trash" />Projekt löschen</button>
              </div>
            </article>
          </div>
        </section>

        <aside class="panel create-panel" aria-labelledby="create-heading">
          <span class="panel-icon"><UiIcon name="plus" /></span>
          <h2 id="create-heading">Neues Projekt erstellen</h2>
          <p class="panel-intro">Das Projekt wird diesem Team zugeordnet.</p>
          <form class="stack-form" :aria-busy="submitting" @submit.prevent="createProject">
            <label>Projektname<input v-model="name" type="text" placeholder="z. B. Website-Relaunch" :disabled="submitting" required maxlength="255" /></label>
            <label><span class="label-row">Beschreibung<span class="optional">Optional</span></span>
              <textarea v-model="description" placeholder="Was möchtet ihr erreichen?" :disabled="submitting" maxlength="255"></textarea>
            </label>
            <button type="submit" class="button" :disabled="submitting"><UiIcon name="plus" />{{ submitting ? 'Wird erstellt …' : 'Projekt erstellen' }}</button>
          </form>
          <p class="form-note"><UiIcon name="users" />Für alle Mitglieder dieses Teams zugänglich.</p>
        </aside>
      </div>
    </template>

    <template v-else>
      <a v-if="isOwner" class="button mobile-create-link" href="#add-member-heading"><UiIcon name="plus" />Mitglied hinzufügen</a>
      <div class="workspace-columns">
        <section class="collection" aria-labelledby="members-heading">
          <div class="section-heading">
            <div class="section-title"><h2 id="members-heading">Teammitglieder</h2><span class="count-badge">{{ members.length }}</span></div>
            <button type="button" class="text-button" @click="loadPage"><UiIcon name="refresh" />Aktualisieren</button>
          </div>
          <p class="section-note">Mitglieder gehören zum gesamten Team und haben Zugriff auf alle seine Projekte und Aufgaben.</p>
          <ul class="member-list" :class="{ 'can-manage': isOwner }">
            <li v-for="member in members" :key="member.userId" class="member-row">
              <span class="avatar member-avatar">{{ member.username.slice(0, 2).toUpperCase() }}</span>
              <div class="member-identity">
                <strong>{{ member.username }} <span v-if="String(member.userId) === String(authStore.userId)" class="you-label">(du)</span></strong>
                <span>{{ member.role === 'OWNER' ? 'Verwaltet dieses Team' : 'Zugriff auf Projekte und Aufgaben' }}</span>
              </div>
              <span class="role-badge" :class="{ 'role-owner': member.role === 'OWNER' }">{{ member.role === 'OWNER' ? 'Besitzer' : 'Mitglied' }}</span>
              <button v-if="isOwner && member.role !== 'OWNER'" type="button" class="text-button destructive-link member-remove"
                :aria-label="'Mitglied entfernen: ' + member.username" @click="confirmRemoval('member', member)"><UiIcon name="trash" />Entfernen</button>
            </li>
          </ul>
        </section>

        <aside class="panel create-panel" aria-labelledby="add-member-heading">
          <span class="panel-icon"><UiIcon name="users" /></span>
          <h2 id="add-member-heading">{{ isOwner ? 'Mitglied hinzufügen' : 'Berechtigungen' }}</h2>
          <template v-if="isOwner">
            <p class="panel-intro">Füge eine Person mit ihrem Benutzernamen oder ihrer E-Mail-Adresse hinzu.</p>
            <form class="stack-form" :aria-busy="addingMember" @submit.prevent="addMember">
              <label>Benutzername oder E-Mail<input v-model="identifier" type="text" placeholder="z. B. anna oder anna@beispiel.de" :disabled="addingMember" required maxlength="255" /></label>
              <p v-if="memberError" class="error inline-error" role="alert">{{ memberError }}</p>
              <button type="submit" class="button" :disabled="addingMember"><UiIcon name="plus" />{{ addingMember ? 'Wird hinzugefügt …' : 'Mitglied hinzufügen' }}</button>
            </form>
            <p class="form-note"><UiIcon name="check" />Die Person braucht ein bestehendes Konto und erhält sofort Zugriff auf dieses Team.</p>
          </template>
          <p v-else class="panel-intro">Nur der Teambesitzer kann Personen hinzufügen oder entfernen. Alle Teammitglieder können an Projekten und Aufgaben arbeiten.</p>
          <div class="role-guide">
            <p><strong>Besitzer</strong><span>Verwaltet Mitglieder und kann Projekte sowie das Team löschen.</span></p>
            <p><strong>Mitglied</strong><span>Erstellt Projekte und bearbeitet Aufgaben.</span></p>
          </div>
        </aside>
      </div>
    </template>

    <!-- Der Dialog übernimmt die Rückfrage; Auswahl, API-Aufruf und Fehlermeldung bleiben in dieser Seite. -->
    <ConfirmDialog v-if="pendingAction" :title="dialogTitle" :message="dialogMessage"
      :confirm-label="pendingAction.type === 'project' ? 'Projekt endgültig löschen' : 'Mitglied entfernen'"
      :busy="deleting" :error="actionError" @cancel="pendingAction = null" @confirm="removeConfirmed" />
  </WorkspaceShell>
</template>
