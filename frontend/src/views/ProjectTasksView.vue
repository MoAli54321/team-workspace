<script setup>
import WorkspaceShell from '../components/WorkspaceShell.vue'
import UiIcon from '../components/UiIcon.vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { requestProjectTasks, requestTask } from '../api/tasks.js'
import { useAuthStore } from '../stores/auth.js'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

// Die Projekt-ID bestimmt die API-Anfragen. Namen aus der URL dienen nur der Anzeige und Navigation.
const projectId = computed(() => route.params.projectId)
const projectName = computed(() => route.query.projectName || `Projekt #${projectId.value}`)
const backTarget = computed(() => route.query.teamId
  ? {
      name: 'team-projects',
      params: { teamId: route.query.teamId },
      query: { teamName: route.query.teamName },
    }
  : { name: 'dashboard' })

const tasks = ref([])
// Die Übersicht wird aus der geladenen Liste berechnet und benötigt keine zusätzliche API-Anfrage.
const taskCounts = computed(() => tasks.value.reduce((counts, task) => {
  if (Object.hasOwn(counts, task.status)) counts[task.status] += 1
  return counts
}, { TODO: 0, IN_PROGRESS: 0, DONE: 0 }))
const loading = ref(true)
const submitting = ref(false)
const mutating = ref(false)
const pageReady = ref(false)
const pendingTask = ref(null)
const deleteError = ref('')
const error = ref('')
let loadVersion = 0

const title = ref('')
const description = ref('')
const status = ref('TODO')
const priority = ref('MEDIUM')

// Änderungen am Text bleiben zunächst im Formular. Abbrechen verändert dadurch die geladene Aufgabe nicht.
const editingTaskId = ref(null)
const editTitle = ref('')
const editDescription = ref('')

// Logout und ein ungültiger Token führen über denselben Weg zurück zur Anmeldung.
watch(() => authStore.token, token => {
  if (!token) {
    loadVersion++
    tasks.value = []
    pendingTask.value = null
    router.replace({ name: 'login' })
  }
})

function startEdit(task) {
  editingTaskId.value = task.id
  editTitle.value = task.title
  editDescription.value = task.description ?? ''
}

function cancelEdit() {
  editingTaskId.value = null
  editTitle.value = ''
  editDescription.value = ''
}

async function loadTasks() {
  const version = ++loadVersion
  loading.value = true
  error.value = ''

  try {
    const response = await requestProjectTasks(projectId.value)
    if (response.status === 403) throw new Error('Du hast keinen Zugriff auf dieses Projekt.')
    if (response.status === 404) throw new Error('Projekt wurde nicht gefunden.')
    if (!response.ok) throw new Error('Aufgaben konnten nicht geladen werden.')
    const data = await response.json()
    if (version !== loadVersion) return
    tasks.value = data
    pageReady.value = true
  } catch (err) {
    if (version !== loadVersion) return
    tasks.value = []
    pageReady.value = false
    error.value = err instanceof Error ? err.message : 'Aufgaben konnten nicht geladen werden.'
  } finally {
    if (version === loadVersion) loading.value = false
  }
}

async function createTask() {
  if (!pageReady.value || !title.value.trim() || submitting.value || mutating.value) {
    if (!title.value.trim()) error.value = 'Bitte einen Titel eingeben.'
    return
  }

  submitting.value = true
  const currentProject = projectId.value
  error.value = ''

  try {
    const response = await requestProjectTasks(currentProject, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        title: title.value.trim(),
        description: description.value.trim(),
        status: status.value,
        priority: priority.value,
      }),
    })

    if (currentProject !== projectId.value) return
    if (response.status === 403) throw new Error('Du hast keinen Zugriff auf dieses Projekt.')
    if (response.status === 400) throw new Error('Bitte prüfe Titel, Beschreibung, Status und Priorität.')
    if (!response.ok) throw new Error('Aufgabe konnte nicht erstellt werden.')

    title.value = ''
    description.value = ''
    status.value = 'TODO'
    priority.value = 'MEDIUM'
    await loadTasks()
  } catch (err) {
    if (currentProject === projectId.value) error.value = err instanceof Error ? err.message : 'Aufgabe konnte nicht erstellt werden.'
  } finally {
    submitting.value = false
  }
}

// Das Backend erwartet bei PUT alle bearbeitbaren Felder. Nicht geänderte Werte werden mitgesendet,
// damit etwa ein Statuswechsel weder Titel noch Beschreibung oder Priorität überschreibt.
async function updateTask(task, changes, failureMessage) {
  if (mutating.value || submitting.value) return
  const currentProject = projectId.value
  mutating.value = true
  error.value = ''

  try {
    const response = await requestTask(task.id, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        title: changes.title ?? task.title,
        description: changes.description ?? task.description,
        status: changes.status ?? task.status,
        priority: changes.priority ?? task.priority,
      }),
    })

    if (currentProject !== projectId.value) return
    if (response.status === 400) throw new Error('Bitte prüfe Titel, Beschreibung, Status und Priorität.')
    if (response.status === 403) throw new Error('Du darfst diese Aufgabe nicht bearbeiten.')
    if (!response.ok) throw new Error(failureMessage)

    // Nach erfolgreichem Speichern werden Liste und Statusübersicht mit dem Serverstand abgeglichen.
    cancelEdit()
    await loadTasks()
  } catch (err) {
    if (currentProject === projectId.value) error.value = err instanceof Error ? err.message : failureMessage
  } finally {
    mutating.value = false
  }
}

function saveTask(task) {
  if (!editTitle.value.trim()) {
    error.value = 'Bitte einen Titel eingeben.'
    return
  }

  return updateTask(task, {
    title: editTitle.value.trim(),
    description: editDescription.value.trim(),
  }, 'Aufgabe konnte nicht bearbeitet werden.')
}

function updateTaskStatus(task, event) {
  const status = event.target.value
  // Bis zur Serverbestätigung bleibt der gespeicherte Wert sichtbar, auch bei einem Fehler.
  event.target.value = task.status
  return updateTask(task, { status }, 'Status konnte nicht geändert werden.')
}

function updateTaskPriority(task, event) {
  const priority = event.target.value
  event.target.value = task.priority
  return updateTask(task, { priority }, 'Priorität konnte nicht geändert werden.')
}

function askToDeleteTask(task) {
  if (mutating.value || submitting.value) return
  pendingTask.value = task
  deleteError.value = ''
}

async function deleteTask() {
  if (!pendingTask.value || mutating.value) return
  const currentProject = projectId.value
  const id = pendingTask.value.id
  mutating.value = true
  deleteError.value = ''

  try {
    const response = await requestTask(id, { method: 'DELETE' })
    if (currentProject !== projectId.value) return
    if (response.status === 403) throw new Error('Du darfst diese Aufgabe nicht löschen.')
    if (!response.ok) throw new Error('Aufgabe konnte nicht gelöscht werden.')
    pendingTask.value = null
    await loadTasks()
  } catch (err) {
    if (currentProject === projectId.value) deleteError.value = err instanceof Error ? err.message : 'Aufgabe konnte nicht gelöscht werden.'
  } finally {
    mutating.value = false
  }
}

// Vue kann die Ansicht beim Projektwechsel wiederverwenden; deshalb wird auch auf die ID reagiert.
watch(projectId, () => {
  tasks.value = []
  pageReady.value = false
  pendingTask.value = null
  title.value = ''
  description.value = ''
  status.value = 'TODO'
  priority.value = 'MEDIUM'
  cancelEdit()
  loadTasks()
}, { immediate: true })

onBeforeUnmount(() => { loadVersion++ })
</script>

<template>
  <WorkspaceShell :breadcrumbs="[{ label: route.query.teamName || 'Projekte', to: backTarget }, { label: projectName }]">
    <header class="page-header">
      <div>
        <p class="eyebrow">Projekt · Aufgaben</p>
        <h1>{{ projectName }}</h1>
        <p class="subtitle">Aufgaben erstellen, bearbeiten und ihren Fortschritt verfolgen.</p>
      </div>
      <span v-if="!loading" class="page-counter"><UiIcon name="task" />{{ tasks.length }} {{ tasks.length === 1 ? 'Aufgabe' : 'Aufgaben' }}</span>
    </header>

    <a v-if="pageReady" class="button mobile-create-link" href="#create-heading"><UiIcon name="plus" />Neue Aufgabe</a>

    <p v-if="error" class="error" role="alert">{{ error }}</p>

    <div class="task-summary" aria-label="Aufgabenübersicht">
      <div class="summary-item"><span class="summary-icon"><UiIcon name="task" /></span><div><strong>{{ loading ? '–' : taskCounts.TODO }}</strong><span>Offen</span></div></div>
      <div class="summary-item summary-progress"><span class="summary-icon"><UiIcon name="clock" /></span><div><strong>{{ loading ? '–' : taskCounts.IN_PROGRESS }}</strong><span>In Arbeit</span></div></div>
      <div class="summary-item summary-done"><span class="summary-icon"><UiIcon name="check" /></span><div><strong>{{ loading ? '–' : taskCounts.DONE }}</strong><span>Erledigt</span></div></div>
    </div>

    <div class="workspace-columns">
      <section class="collection" aria-labelledby="tasks-heading" :aria-busy="loading">
        <div class="section-heading">
          <div class="section-title"><h2 id="tasks-heading">Alle Aufgaben</h2><span v-if="!loading" class="count-badge">{{ tasks.length }}</span></div>
          <button type="button" class="text-button" :disabled="loading || mutating || submitting" @click="loadTasks"><UiIcon name="refresh" />Aktualisieren</button>
        </div>

        <div v-if="loading" class="empty-state" role="status"><p>Aufgaben werden geladen …</p></div>
        <div v-else-if="pageReady && tasks.length === 0" class="empty-state">
          <span class="entity-icon"><UiIcon name="task" /></span>
          <h3>Noch keine Aufgaben</h3>
          <p>Erstelle die erste Aufgabe für dieses Projekt.</p>
        </div>

        <div v-else class="task-list">
          <article v-for="task in tasks" :key="task.id" class="task-card">
            <div class="task-content">
              <form v-if="editingTaskId === task.id" class="edit-form" @submit.prevent="saveTask(task)">
                <label>Titel<input v-model="editTitle" type="text" required maxlength="255" :disabled="mutating" /></label>
                <label>Beschreibung<textarea v-model="editDescription" maxlength="255" :disabled="mutating"></textarea></label>
                <div class="button-row">
                  <button type="submit" class="button" :disabled="mutating"><UiIcon name="check" />{{ mutating ? 'Wird gespeichert …' : 'Speichern' }}</button>
                  <button type="button" class="button button-secondary" :disabled="mutating" @click="cancelEdit">Abbrechen</button>
                </div>
              </form>

              <div v-else class="task-title-row">
                <div class="task-title">
                  <span class="task-status-mark" :class="task.status" aria-hidden="true"><UiIcon v-if="task.status === 'DONE'" name="check" /></span>
                  <div><h3>{{ task.title }}</h3><p v-if="task.description" class="task-description">{{ task.description }}</p></div>
                </div>
                <div class="task-actions">
                  <button type="button" class="icon-button" :disabled="mutating || submitting" :aria-label="'Aufgabe bearbeiten: ' + task.title" title="Bearbeiten" @click="startEdit(task)"><UiIcon name="edit" /></button>
                  <button type="button" class="icon-button danger-button" :disabled="mutating || submitting" :aria-label="'Aufgabe löschen: ' + task.title" title="Löschen" @click="askToDeleteTask(task)"><UiIcon name="trash" /></button>
                </div>
              </div>

              <!-- Deutsche Bezeichnungen dienen der Anzeige; an die API gehen weiterhin die festen Statuswerte. -->
              <div class="task-controls">
                <label>Status
                  <select :value="task.status" :class="task.status" :disabled="mutating || submitting || editingTaskId === task.id" :aria-label="'Status: ' + task.title" @change="updateTaskStatus(task, $event)">
                    <option value="TODO">Offen</option>
                    <option value="IN_PROGRESS">In Arbeit</option>
                    <option value="DONE">Erledigt</option>
                  </select>
                </label>
                <label>Priorität
                  <select :value="task.priority" :class="task.priority" :disabled="mutating || submitting || editingTaskId === task.id" :aria-label="'Priorität: ' + task.title" @change="updateTaskPriority(task, $event)">
                    <option value="LOW">Niedrig</option>
                    <option value="MEDIUM">Mittel</option>
                    <option value="HIGH">Hoch</option>
                  </select>
                </label>
              </div>
            </div>
          </article>
        </div>
      </section>

      <aside v-if="pageReady" class="panel create-panel" aria-labelledby="create-heading">
        <span class="panel-icon"><UiIcon name="plus" /></span>
        <h2 id="create-heading">Neue Aufgabe</h2>
        <p class="panel-intro">Die Aufgabe wird in diesem Projekt angelegt.</p>
        <form class="stack-form" :aria-busy="submitting" @submit.prevent="createTask">
          <label>Titel<input v-model="title" type="text" placeholder="Was gibt es zu tun?" :disabled="submitting" required maxlength="255" /></label>
          <label><span class="label-row">Beschreibung<span class="optional">Optional</span></span>
            <textarea v-model="description" placeholder="Details, die deinem Team helfen …" :disabled="submitting" maxlength="255"></textarea>
          </label>
          <div class="form-row">
            <label>Status
              <select v-model="status" :disabled="submitting">
                <option value="TODO">Offen</option><option value="IN_PROGRESS">In Arbeit</option><option value="DONE">Erledigt</option>
              </select>
            </label>
            <label>Priorität
              <select v-model="priority" :disabled="submitting">
                <option value="LOW">Niedrig</option><option value="MEDIUM">Mittel</option><option value="HIGH">Hoch</option>
              </select>
            </label>
          </div>
          <button type="submit" class="button" :disabled="submitting || mutating"><UiIcon name="plus" />{{ submitting ? 'Wird erstellt …' : 'Aufgabe erstellen' }}</button>
        </form>
      </aside>
    </div>
    <ConfirmDialog v-if="pendingTask" title="Aufgabe löschen?"
      :message="`„${pendingTask.title}“ wird endgültig gelöscht.`"
      confirm-label="Aufgabe löschen" :busy="mutating" :error="deleteError"
      @cancel="pendingTask = null" @confirm="deleteTask" />
  </WorkspaceShell>
</template>
