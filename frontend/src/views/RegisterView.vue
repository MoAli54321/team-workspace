<script setup>
import AuthLayout from '../components/AuthLayout.vue'
import UiIcon from '../components/UiIcon.vue'
import { ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

const router = useRouter()
const username = ref('')
const email = ref('')
const password = ref('')
const showPassword = ref(false)
const error = ref('')
const submitting = ref(false)

// Die Registrierung legt den Account an. Anschließend folgt bewusst die normale Anmeldung,
// denn erst der Login stellt einen JWT für die geschützten Bereiche aus.
async function register() {
  if (submitting.value) return

  error.value = ''

  if (!username.value.trim() || !email.value.trim() || !password.value) {
    error.value = 'Bitte Benutzername, E-Mail und Passwort eingeben.'
    return
  }

  if (new TextEncoder().encode(password.value).length > 72) {
    error.value = 'Das Passwort ist zu lang. Bitte wähle ein kürzeres Passwort.'
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
      // Die bekannten Textantworten werden in konkrete deutsche Hinweise für das Formular übersetzt.
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
    showPassword.value = false
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
  <AuthLayout>
    <header class="auth-heading">
      <h1>Konto erstellen</h1>
      <p>Mit deinem Konto kannst du Teams erstellen und in anderen Teams mitarbeiten.</p>
    </header>
    <form class="auth-form" :aria-busy="submitting" @submit.prevent="register">
      <div class="field">
        <label for="username">Benutzername</label>
        <input id="username" v-model="username" name="username" type="text" autocomplete="username"
          placeholder="Dein Benutzername" maxlength="255" :disabled="submitting" required />
      </div>
      <div class="field">
        <label for="email">E-Mail</label>
        <input id="email" v-model="email" name="email" type="email" autocomplete="email"
          placeholder="name@beispiel.de" maxlength="255" :disabled="submitting" required />
      </div>
      <div class="field">
        <label for="password">Passwort</label>
        <div class="password-field">
          <input id="password" v-model="password" name="password" :type="showPassword ? 'text' : 'password'"
            autocomplete="new-password" placeholder="Wähle ein Passwort" :disabled="submitting" required />
          <button type="button" class="password-toggle" :aria-pressed="showPassword"
            :aria-label="showPassword ? 'Passwort verbergen' : 'Passwort anzeigen'"
            :disabled="submitting" @click="showPassword = !showPassword">
            <UiIcon :name="showPassword ? 'eyeOff' : 'eye'" />
            {{ showPassword ? 'Verbergen' : 'Anzeigen' }}
          </button>
        </div>
      </div>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <button type="submit" class="button" :disabled="submitting">{{ submitting ? 'Konto wird erstellt …' : 'Konto erstellen' }}<UiIcon name="arrow" /></button>
    </form>
    <p class="auth-switch">Du hast schon ein Konto? <RouterLink :to="{ name: 'login' }">Anmelden</RouterLink></p>
  </AuthLayout>
</template>
