<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { requestTasks } from '../api/tasks'

const router = useRouter()
const authStore = useAuthStore()

// ref macht Werte reaktiv: Änderungen aktualisieren automatisch die Oberfläche.
// Im JavaScript erfolgt der Zugriff über .value, im Template direkt über den Namen.
const tasks = ref([])
const loading = ref(true)
const error = ref('')

function logout() {
  authStore.logout()
}

// Reagiert sowohl auf den Logout-Button als auch auf eine vom Backend abgewiesene Sitzung.
watch(() => authStore.token, token => {
  if (!token) {
    tasks.value = []
    router.replace({ name: 'login' })
  }
})

// Eingaben und Standardwerte für das Formular zum Anlegen einer Aufgabe.
const title = ref('')
const description = ref('')
const status = ref('TODO')
const priority = ref('MEDIUM')



// null bedeutet: keine Aufgabe wird bearbeitet. Sonst enthält der Wert ihre ID.
const editingTaskId = ref(null)
// Separate Eingabewerte halten ungespeicherte Änderungen von der Aufgabenliste getrennt.
const editTitle = ref('')
const editDescription = ref('')

// Übernimmt die bisherigen Werte in das Bearbeitungsformular.
function startEdit(task) {
  editingTaskId.value = task.id
  editTitle.value = task.title
  editDescription.value = task.description
}

// Schließt das Formular und verwirft die lokalen Eingaben ohne API-Aufruf.
function cancelEdit() {
  editingTaskId.value = null
  editTitle.value = ''
  editDescription.value = ''
}

// Speichert Titel und Beschreibung. Status und Priorität bleiben erhalten.
// Der PUT-Endpunkt erwartet alle vier bearbeitbaren Felder einer Aufgabe.
async function saveTask(task) {
  try {
    const response = await requestTasks(`/${task.id}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      },
      // Wandelt die Formulardaten in den JSON-Text für den Request-Body um.
      body: JSON.stringify({
        title: editTitle.value,
        description: editDescription.value,
        status: task.status,
        priority: task.priority,
      }),
    })

    if (!response.ok) {
      throw new Error('Task konnte nicht bearbeitet werden')
    }

    // Erst nach erfolgreichem Speichern schließen und den Datenbankstand neu laden.
    cancelEdit()
    await loadTasks()
  } catch (err) {
    error.value = err.message
  }
}

// Lädt die Aufgaben beim Start und erneut nach erfolgreichen Änderungen.
async function loadTasks() {
  try {
    loading.value = true
    error.value = ''

    // Relative /api-Adressen werden in der Entwicklung vom Vite-Proxy weitergeleitet.
    const response = await requestTasks()

    // fetch wirft bei HTTP-Fehlern wie 404 oder 500 nicht automatisch einen Fehler.
    if (!response.ok) {
      throw new Error('Tasks konnten nicht geladen werden')
    }

    // Die JSON-Antwort ersetzt die reaktive Liste und aktualisiert damit das Template.
    tasks.value = await response.json()
  } catch (err) {
    // Netzwerkfehler und selbst ausgelöste Fehler erscheinen in derselben Fehlermeldung.
    error.value = err.message
  } finally {
    // Beendet die Ladeanzeige auch dann, wenn die Anfrage fehlgeschlagen ist.
    loading.value = false
  }
}

async function createTask() {
  // Verhindert im Frontend Titel, die leer sind oder nur aus Leerzeichen bestehen.
  if (!title.value.trim()) {
    error.value = 'Bitte einen Titel eingeben'
    return
  }

  try {
    error.value = ''

    // POST legt eine neue Aufgabe mit den aktuellen Formularwerten an.
    const response = await requestTasks('', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        title: title.value,
        description: description.value,
        status: status.value,
        priority: priority.value,
      }),
    })

    if (!response.ok) {
      throw new Error('Task konnte nicht erstellt werden')
    }

    // Nach erfolgreichem Anlegen das Formular für die nächste Aufgabe zurücksetzen.
    title.value = ''
    description.value = ''
    status.value = 'TODO'
    priority.value = 'MEDIUM'

    await loadTasks()
  } catch (err) {
    error.value = err.message
  }
}
// Ändert den Status und sendet die übrigen Felder mit, damit PUT sie nicht leert.
async function updateTaskStatus(task, newStatus) {
  try {
    const response = await requestTasks(`/${task.id}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        title: task.title,
        description: task.description,
        status: newStatus,
        priority: task.priority,
      }),
    })

    if (!response.ok) {
      throw new Error('Status konnte nicht geändert werden')
    }

    await loadTasks()
  } catch (err) {
    error.value = err.message
  }
}

// Ändert die Priorität; Titel, Beschreibung und Status werden unverändert mitgesendet.
async function updateTaskPriority(task, newPriority) {
  try {
    const response = await requestTasks(`/${task.id}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        title: task.title,
        description: task.description,
        status: task.status,
        priority: newPriority,
      }),
    })

    if (!response.ok) {
      throw new Error('Priorität konnte nicht geändert werden')
    }

    await loadTasks()
  } catch (err) {
    error.value = err.message
  }
}

// DELETE identifiziert die Aufgabe über ihre ID in der URL; ein Body ist nicht nötig.
async function deleteTask(id) {
  try {
    const response = await requestTasks(`/${id}`, {
      method: 'DELETE',
    })

    if (!response.ok) {
      throw new Error('Task konnte nicht gelöscht werden')
    }

    await loadTasks()
  } catch (err) {
    error.value = err.message
  }
}

// Startet das erste Laden, sobald Vue die Komponente in die Seite eingebunden hat.
onMounted(loadTasks)
</script>

<template>
  <main>
    <header class="dashboard-header">
      <h1>Team Workspace</h1>
      <button type="button" @click="logout">Logout</button>
    </header>

    <!-- v-model verbindet die Eingabefelder mit den reaktiven Formularwerten. -->
    <section class="form">
      <h2>New Task</h2>

      <input
        v-model="title"
        type="text"
        placeholder="Task title"
      />

      <textarea
        v-model="description"
        placeholder="Description"
      ></textarea>

      <div class="form-row">
        <select v-model="status">
          <option value="TODO">TODO</option>
          <option value="IN_PROGRESS">IN PROGRESS</option>
          <option value="DONE">DONE</option>
        </select>

        <select v-model="priority">
          <option value="LOW">LOW</option>
          <option value="MEDIUM">MEDIUM</option>
          <option value="HIGH">HIGH</option>
        </select>
      </div>

      <!-- @click ruft die Funktion auf, wenn der Benutzer den Button anklickt. -->
      <button @click="createTask">
        Create Task
      </button>
    </section>

    <!-- Die gemeinsame Fehlermeldung wird nur bei einem nicht leeren Fehlertext angezeigt. -->
    <p v-if="error" class="error">
      {{ error }}
    </p>

    <section>
      <h2>Tasks</h2>

      <!-- Je nach Zustand erscheinen die Ladeanzeige, der Leerzustand oder die Liste. -->
      <p v-if="loading">Loading...</p>

      <p v-else-if="tasks.length === 0">
        No tasks yet.
      </p>

      <div v-else class="task-list">
        <!-- v-for erzeugt eine Karte pro Aufgabe; :key ordnet sie dauerhaft ihrer ID zu. -->
        <article
          v-for="task in tasks"
          :key="task.id"
          class="task-card"
        >
            <div>
              <!-- Nur die über ihre ID ausgewählte Aufgabe zeigt das Bearbeitungsformular. -->
              <div v-if="editingTaskId === task.id">
                <input
                  v-model="editTitle"
                  type="text"
              />

              <textarea
                v-model="editDescription"
              ></textarea>

              <button @click="saveTask(task)">
              Save
              </button>

              <button @click="cancelEdit">
              Cancel
              </button>
            </div>

            <div v-else>
              <h3>{{ task.title }}</h3>
              <p>{{ task.description }}</p>

              <button @click="startEdit(task)">
              Edit
              </button>
            </div>

            <!-- :value zeigt den gespeicherten Wert; @change sendet die neue Auswahl an die API. -->
            <div class="details">
              <select
                :value="task.status"
                @change="updateTaskStatus(task, $event.target.value)"
>
                <option value="TODO">TODO</option>
                <option value="IN_PROGRESS">IN PROGRESS</option>
                <option value="DONE">DONE</option>
              </select>
              <select
                :value="task.priority"
                @change="updateTaskPriority(task, $event.target.value)"
> 
                <option value="LOW">LOW</option>
                <option value="MEDIUM">MEDIUM</option>
                <option value="HIGH">HIGH</option>
              </select>
            </div>
          </div>

          <button @click="deleteTask(task.id)">
            Delete
          </button>
        </article>
      </div>
    </section>
  </main>
</template>

<style scoped>
/* scoped begrenzt diese Regeln auf die Elemente dieser Komponente. */
/* Begrenzt die Inhaltsbreite und zentriert den Aufgabenbereich horizontal. */
main {
  max-width: 900px;
  margin: 50px auto;
  padding: 20px;
  font-family: Arial, sans-serif;
}

h1 {
  font-size: 2.5rem;
}

.dashboard-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 15px;
  margin-bottom: 30px;
}

.form {
  border: 1px solid #444;
  border-radius: 10px;
  padding: 20px;
  margin-bottom: 40px;
}

/* Einheitliche Eingabefelder; padding und Rahmen sind in width enthalten. */
input,
textarea,
select {
  box-sizing: border-box;
  width: 100%;
  padding: 10px;
  margin-bottom: 12px;
}

textarea {
  min-height: 100px;
}

/* Status und Priorität stehen im Formular nebeneinander. */
.form-row {
  display: flex;
  gap: 10px;
}

button {
  padding: 10px 16px;
  cursor: pointer;
}

/* Grid hält die Abstände zwischen den Aufgabenkarten gleichmäßig. */
.task-list {
  display: grid;
  gap: 15px;
}

/* Flexbox verteilt Aufgabeninhalt und Löschbutton auf die beiden Seiten der Karte. */
.task-card {
  border: 1px solid #444;
  border-radius: 10px;
  padding: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.details {
  display: flex;
  gap: 15px;
  margin-top: 10px;
  font-size: 0.9rem;
}

.error {
  color: red;
}
</style>
