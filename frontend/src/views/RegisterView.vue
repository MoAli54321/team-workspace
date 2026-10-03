<script setup>
import { ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

const router = useRouter()
const username = ref('')
const email = ref('')
const password = ref('')
const error = ref('')
const submitting = ref(false)

async function register() {
  if (submitting.value) return

  error.value = ''

  if (!username.value.trim() || !email.value.trim() || !password.value) {
    error.value = 'Bitte Benutzername, E-Mail und Passwort eingeben.'
    return
  }

  submitting.value = true

  try {
    const response = await fetch('/api/auth/register', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        username: username.value.trim(),
        email: email.value.trim(),
        password: password.value,
      }),
    })

    if (response.status === 400) {
      // Das Backend liefert bei bereits vergebenen Daten eine Textantwort.
      const message = (await response.text()).trim()

      if (message === 'Username already exists') {
        throw new Error('Dieser Benutzername ist bereits vergeben.')
      }

      if (message === 'Email already exists') {
        throw new Error('Diese E-Mail-Adresse ist bereits registriert.')
      }

      throw new Error('Bitte überprüfe deine Angaben und versuche es erneut.')
    }

    if (!response.ok) {
      throw new Error('Registrierung fehlgeschlagen. Bitte versuche es erneut.')
    }

    password.value = ''
    // Nur die Bestätigung wird übergeben; Zugangsdaten gehören nicht in die URL.
    await router.push({ name: 'login', query: { registered: '1' } })
  } catch (err) {
    error.value = err instanceof TypeError
      ? 'Der Server ist nicht erreichbar. Bitte versuche es erneut.'
      : err instanceof Error
        ? err.message
        : 'Registrierung fehlgeschlagen. Bitte versuche es erneut.'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="register-page">
    <form class="register-box" :aria-busy="submitting" @submit.prevent="register">
      <h1>Team Workspace</h1>
      <h2>Registrierung</h2>

      <div class="field">
        <label for="username">Benutzername</label>
        <input
          id="username"
          v-model="username"
          name="username"
          type="text"
          autocomplete="username"
          placeholder="Benutzername"
          :disabled="submitting"
          required
        />
      </div>

      <div class="field">
        <label for="email">E-Mail</label>
        <input
          id="email"
          v-model="email"
          name="email"
          type="email"
          autocomplete="email"
          placeholder="E-Mail"
          :disabled="submitting"
          required
        />
      </div>

      <div class="field">
        <label for="password">Passwort</label>
        <input
          id="password"
          v-model="password"
          name="password"
          type="password"
          autocomplete="new-password"
          placeholder="Passwort"
          :disabled="submitting"
          required
        />
      </div>

      <button type="submit" :disabled="submitting">
        {{ submitting ? 'Account wird erstellt …' : 'Account erstellen' }}
      </button>

      <p v-if="error" class="error" role="alert">
        {{ error }}
      </p>

      <p>
        Bereits registriert?
        <RouterLink :to="{ name: 'login' }">Anmelden</RouterLink>
      </p>
    </form>
  </main>
</template>

<style scoped>
.register-page {
  max-width: 400px;
  margin: 80px auto;
  padding: 20px;
}

.register-box {
  display: grid;
  gap: 15px;
}

.field {
  display: grid;
  gap: 6px;
}

input,
button {
  width: 100%;
  min-width: 0;
  padding: 12px;
  font: inherit;
}

button {
  cursor: pointer;
}

button:disabled {
  cursor: wait;
  opacity: 0.7;
}

.error {
  color: #c62828;
}

@media (prefers-color-scheme: dark) {
  .error {
    color: #ff8a80;
  }
}
</style>
