<script setup>
import { ref, onMounted } from 'vue'

const tasks = ref([])
const loading = ref(true)
const error = ref('')

const title = ref('')
const description = ref('')
const status = ref('TODO')
const priority = ref('MEDIUM')

async function loadTasks() {
  try {
    loading.value = true
    error.value = ''

    const response = await fetch('/api/tasks')

    if (!response.ok) {
      throw new Error('Tasks konnten nicht geladen werden')
    }

    tasks.value = await response.json()
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}

async function createTask() {
  if (!title.value.trim()) {
    error.value = 'Bitte einen Titel eingeben'
    return
  }

  try {
    error.value = ''

    const response = await fetch('/api/tasks', {
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

    title.value = ''
    description.value = ''
    status.value = 'TODO'
    priority.value = 'MEDIUM'

    await loadTasks()
  } catch (err) {
    error.value = err.message
  }
}

async function deleteTask(id) {
  try {
    const response = await fetch(`/api/tasks/${id}`, {
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

onMounted(loadTasks)
</script>

<template>
  <main>
    <h1>Team Workspace</h1>

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

      <button @click="createTask">
        Create Task
      </button>
    </section>

    <p v-if="error" class="error">
      {{ error }}
    </p>

    <section>
      <h2>Tasks</h2>

      <p v-if="loading">Loading...</p>

      <p v-else-if="tasks.length === 0">
        No tasks yet.
      </p>

      <div v-else class="task-list">
        <article
          v-for="task in tasks"
          :key="task.id"
          class="task-card"
        >
          <div>
            <h3>{{ task.title }}</h3>

            <p>{{ task.description }}</p>

            <div class="details">
              <span>{{ task.status }}</span>
              <span>{{ task.priority }}</span>
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
main {
  max-width: 900px;
  margin: 50px auto;
  padding: 20px;
  font-family: Arial, sans-serif;
}

h1 {
  font-size: 2.5rem;
  margin-bottom: 30px;
}

.form {
  border: 1px solid #444;
  border-radius: 10px;
  padding: 20px;
  margin-bottom: 40px;
}

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

.form-row {
  display: flex;
  gap: 10px;
}

button {
  padding: 10px 16px;
  cursor: pointer;
}

.task-list {
  display: grid;
  gap: 15px;
}

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