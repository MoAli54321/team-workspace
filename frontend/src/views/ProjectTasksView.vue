<script setup>
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { requestProjectTasks, requestTask } from '../api/tasks.js'
import { useAuthStore } from '../stores/auth.js'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

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
const loading = ref(true)
const submitting = ref(false)
const error = ref('')

const title = ref('')
const description = ref('')
const status = ref('TODO')
const priority = ref('MEDIUM')

const editingTaskId = ref(null)
const editTitle = ref('')
const editDescription = ref('')

function logout() {
  authStore.logout()
}

watch(() => authStore.token, token => {
  if (!token) {
    tasks.value = []
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
  loading.value = true
  error.value = ''

  try {
    const response = await requestProjectTasks(projectId.value)
    if (response.status === 403) throw new Error('Du hast keinen Zugriff auf dieses Projekt.')
    if (response.status === 404) throw new Error('Projekt wurde nicht gefunden.')
    if (!response.ok) throw new Error('Tasks konnten nicht geladen werden.')
    tasks.value = await response.json()
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Tasks konnten nicht geladen werden.'
  } finally {
    loading.value = false
  }
}

async function createTask() {
  if (!title.value.trim() || submitting.value) {
    if (!title.value.trim()) error.value = 'Bitte einen Titel eingeben.'
    return
  }

  submitting.value = true
  error.value = ''

  try {
    const response = await requestProjectTasks(projectId.value, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        title: title.value.trim(),
        description: description.value.trim(),
        status: status.value,
        priority: priority.value,
      }),
    })

    if (response.status === 403) throw new Error('Du hast keinen Zugriff auf dieses Projekt.')
    if (!response.ok) throw new Error('Task konnte nicht erstellt werden.')

    title.value = ''
    description.value = ''
    status.value = 'TODO'
    priority.value = 'MEDIUM'
    await loadTasks()
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Task konnte nicht erstellt werden.'
  } finally {
    submitting.value = false
  }
}

async function updateTask(task, changes, failureMessage) {
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

    if (response.status === 403) throw new Error('Du darfst diesen Task nicht bearbeiten.')
    if (!response.ok) throw new Error(failureMessage)

    cancelEdit()
    await loadTasks()
  } catch (err) {
    error.value = err instanceof Error ? err.message : failureMessage
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
  }, 'Task konnte nicht bearbeitet werden.')
}

function updateTaskStatus(task, newStatus) {
  return updateTask(task, { status: newStatus }, 'Status konnte nicht geändert werden.')
}

function updateTaskPriority(task, newPriority) {
  return updateTask(task, { priority: newPriority }, 'Priorität konnte nicht geändert werden.')
}

async function deleteTask(id) {
  error.value = ''

  try {
    const response = await requestTask(id, { method: 'DELETE' })
    if (response.status === 403) throw new Error('Du darfst diesen Task nicht löschen.')
    if (!response.ok) throw new Error('Task konnte nicht gelöscht werden.')
    await loadTasks()
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Task konnte nicht gelöscht werden.'
  }
}

watch(() => route.params.projectId, loadTasks, { immediate: true })
</script>

<template>
  <main class="workspace-page">
    <nav class="breadcrumbs" aria-label="Breadcrumb">
      <RouterLink :to="{ name: 'dashboard' }">Meine Teams</RouterLink>
      <span aria-hidden="true">/</span>
      <RouterLink :to="backTarget">{{ route.query.teamName || 'Projekte' }}</RouterLink>
      <span aria-hidden="true">/</span>
      <span>{{ projectName }}</span>
    </nav>

    <header class="page-header">
      <div>
        <p class="eyebrow">Projekt</p>
        <h1>{{ projectName }}</h1>
        <p class="subtitle">Tasks planen, priorisieren und abschließen.</p>
      </div>
      <button type="button" class="secondary-button" @click="logout">Logout</button>
    </header>

    <section class="panel">
      <p class="eyebrow">Neue Aufgabe</p>
      <h2>Task erstellen</h2>

      <form class="stack-form" :aria-busy="submitting" @submit.prevent="createTask">
        <label>
          Titel
          <input v-model="title" type="text" placeholder="z. B. Dashboard bauen" :disabled="submitting" required />
        </label>

        <label>
          Beschreibung
          <textarea v-model="description" placeholder="Was muss erledigt werden?" :disabled="submitting"></textarea>
        </label>

        <div class="form-row">
          <label>
            Status
            <select v-model="status" :disabled="submitting">
              <option value="TODO">TODO</option>
              <option value="IN_PROGRESS">IN PROGRESS</option>
              <option value="DONE">DONE</option>
            </select>
          </label>

          <label>
            Priorität
            <select v-model="priority" :disabled="submitting">
              <option value="LOW">LOW</option>
              <option value="MEDIUM">MEDIUM</option>
              <option value="HIGH">HIGH</option>
            </select>
          </label>
        </div>

        <button type="submit" :disabled="submitting">
          {{ submitting ? 'Task wird erstellt …' : 'Task erstellen' }}
        </button>
      </form>
    </section>

    <p v-if="error" class="error" role="alert">{{ error }}</p>

    <section>
      <div class="section-heading">
        <div>
          <p class="eyebrow">Projektarbeit</p>
          <h2>Tasks</h2>
        </div>
        <button type="button" class="text-button" :disabled="loading" @click="loadTasks">Aktualisieren</button>
      </div>

      <p v-if="loading" class="empty-state">Tasks werden geladen …</p>
      <p v-else-if="tasks.length === 0" class="empty-state">Dieses Projekt hat noch keine Tasks.</p>

      <div v-else class="task-list">
        <article v-for="task in tasks" :key="task.id" class="task-card">
          <div class="task-content">
            <div v-if="editingTaskId === task.id" class="edit-form">
              <input v-model="editTitle" type="text" aria-label="Task-Titel" />
              <textarea v-model="editDescription" aria-label="Task-Beschreibung"></textarea>
              <div class="button-row">
                <button type="button" @click="saveTask(task)">Speichern</button>
                <button type="button" class="secondary-button" @click="cancelEdit">Abbrechen</button>
              </div>
            </div>

            <div v-else>
              <div class="task-title-row">
                <h3>{{ task.title }}</h3>
                <button type="button" class="text-button" @click="startEdit(task)">Bearbeiten</button>
              </div>
              <p>{{ task.description || 'Keine Beschreibung' }}</p>
            </div>

            <div class="task-controls">
              <label>
                Status
                <select :value="task.status" @change="updateTaskStatus(task, $event.target.value)">
                  <option value="TODO">TODO</option>
                  <option value="IN_PROGRESS">IN PROGRESS</option>
                  <option value="DONE">DONE</option>
                </select>
              </label>

              <label>
                Priorität
                <select :value="task.priority" @change="updateTaskPriority(task, $event.target.value)">
                  <option value="LOW">LOW</option>
                  <option value="MEDIUM">MEDIUM</option>
                  <option value="HIGH">HIGH</option>
                </select>
              </label>
            </div>
          </div>

          <button type="button" class="danger-button" @click="deleteTask(task.id)">Löschen</button>
        </article>
      </div>
    </section>
  </main>
</template>

<style scoped>
.workspace-page { width: min(960px, 100%); margin: 0 auto; padding: 36px 20px 72px; }
.breadcrumbs { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 28px; color: var(--color-text-muted); }
.breadcrumbs a { padding: 0; color: #16805f; }
.page-header,
.section-heading,
.task-title-row { display: flex; align-items: center; justify-content: space-between; gap: 20px; }
.page-header { margin-bottom: 32px; }
.section-heading { margin-bottom: 18px; }
h1 { font-size: clamp(2rem, 6vw, 3.25rem); line-height: 1.1; }
h2 { margin-bottom: 16px; font-size: 1.5rem; }
.eyebrow { color: #16805f; font-size: 0.75rem; font-weight: 700; letter-spacing: 0.12em; text-transform: uppercase; }
.subtitle { margin-top: 6px; color: var(--color-text-muted); }
.panel { padding: 24px; margin-bottom: 32px; border: 1px solid var(--color-border); border-radius: 16px; background: var(--color-background-soft); }
.stack-form,
.edit-form { display: grid; gap: 16px; }
.stack-form label,
.task-controls label { display: grid; gap: 6px; font-weight: 600; }
.form-row,
.task-controls,
.button-row { display: flex; gap: 14px; }
.form-row > *,
.task-controls > * { flex: 1; }
input,
textarea,
select,
button { padding: 11px 14px; border: 1px solid var(--color-border-hover); border-radius: 9px; font: inherit; }
input,
textarea,
select { width: 100%; color: var(--color-text); background: var(--color-background); }
textarea { min-height: 92px; resize: vertical; }
button { width: fit-content; color: white; background: #16805f; cursor: pointer; }
button:disabled { cursor: wait; opacity: 0.65; }
.secondary-button { color: var(--color-text); background: transparent; }
.text-button { padding: 6px; border: 0; color: #16805f; background: transparent; }
.danger-button { color: #a51d1d; background: transparent; border-color: #dc8c8c; }
.task-list { display: grid; gap: 16px; }
.task-card { display: flex; align-items: center; justify-content: space-between; gap: 24px; padding: 22px; border: 1px solid var(--color-border); border-radius: 14px; background: var(--color-background-soft); }
.task-content { flex: 1; min-width: 0; }
.task-content h3 { font-size: 1.2rem; font-weight: 650; }
.task-content p { margin-top: 5px; color: var(--color-text-muted); }
.task-controls { max-width: 440px; margin-top: 18px; }
.empty-state { padding: 28px; border: 1px dashed var(--color-border-hover); border-radius: 12px; text-align: center; }
.error { padding: 12px 14px; margin-bottom: 24px; border-radius: 9px; color: #a51d1d; background: #fff0f0; }
@media (max-width: 680px) {
  .page-header,
  .section-heading,
  .task-card { align-items: flex-start; flex-direction: column; }
  .form-row,
  .task-controls { flex-direction: column; }
}
</style>
