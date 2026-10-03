<script setup>
import { ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

const router = useRouter()
const route = useRoute()
const identifier = ref('')
const password = ref('')
const error = ref('')
const submitting = ref(false)

async function login() {
  if (submitting.value) return

  error.value = ''

  if (!identifier.value.trim() || !password.value) {
    error.value = 'Bitte E-Mail oder Benutzername und Passwort eingeben.'
    return
  }

  submitting.value = true

  try {
    const response = await fetch('/api/auth/login', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        identifier: identifier.value.trim(),
        password: password.value,
      }),
    })

    if (response.status === 401) {
      throw new Error('E-Mail, Benutzername oder Passwort ist falsch.')
    }

    if (!response.ok) {
      throw new Error('Login fehlgeschlagen. Bitte versuche es erneut.')
    }

    // Das Backend bestätigt bisher nur die Zugangsdaten und erstellt keine Sitzung.
    // Deshalb werden hier noch keine Tokens oder Anmeldedaten gespeichert.
    password.value = ''
    await router.push({ name: 'dashboard' })
  } catch (err) {
    error.value = err instanceof TypeError
      ? 'Der Server ist nicht erreichbar. Bitte versuche es erneut.'
      : err instanceof Error
        ? err.message
        : 'Login fehlgeschlagen. Bitte versuche es erneut.'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <form class="login-box" :aria-busy="submitting" @submit.prevent="login">
      <h1>Team Workspace</h1>
      <h2>Login</h2>

      <p v-if="route.query.registered === '1'" class="success" role="status">
        Account erstellt. Bitte melde dich an.
      </p>

      <div class="field">
        <label for="identifier">E-Mail oder Benutzername</label>
        <input
          id="identifier"
          v-model="identifier"
          name="identifier"
          type="text"
          autocomplete="username"
          placeholder="E-Mail oder Benutzername"
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
          autocomplete="current-password"
          placeholder="Passwort"
          :disabled="submitting"
          required
        />
      </div>

      <button type="submit" :disabled="submitting">
        {{ submitting ? 'Anmeldung läuft …' : 'Login' }}
      </button>

      <p v-if="error" class="error" role="alert">
        {{ error }}
      </p>

      <p>
        Noch keinen Account?
        <RouterLink :to="{ name: 'register' }">Account erstellen</RouterLink>
      </p>
    </form>
  </main>
</template>

<style scoped>
.login-page {
  max-width: 400px;
  margin: 80px auto;
  padding: 20px;
}

.login-box {
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

.success {
  color: #216e39;
}

@media (prefers-color-scheme: dark) {
  .error {
    color: #ff8a80;
  }

  .success {
    color: #81c995;
  }
}
</style>
