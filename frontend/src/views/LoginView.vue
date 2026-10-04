<script setup>
import AuthLayout from '../components/AuthLayout.vue'
import UiIcon from '../components/UiIcon.vue'
import { ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const identifier = ref('')
const password = ref('')
const showPassword = ref(false)
const error = ref('')
const submitting = ref(false)

// Die Seite verwaltet Eingaben und Fehlermeldungen. Speichern und Wiederherstellen der Sitzung
// übernimmt der gemeinsame Auth-Store, damit alle Seiten denselben Anmeldestand verwenden.
async function login() {
  if (submitting.value) return

  error.value = ''

  if (!identifier.value.trim() || !password.value) {
    error.value = 'Bitte E-Mail oder Benutzername und Passwort eingeben.'
    return
  }

  submitting.value = true

  try {
    await authStore.login(identifier.value, password.value)

    // Erst nach dem erfolgreichen Speichern der Sitzung geht es zur Teamübersicht.
    password.value = ''
    showPassword.value = false
    await router.push({ name: 'dashboard' })
  } catch (err) {
    error.value = err instanceof Error
      ? err.message
      : 'Login fehlgeschlagen. Bitte versuche es erneut.'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AuthLayout>
    <header class="auth-heading">
      <h1>Anmelden</h1>
      <p>Nutze deine E-Mail-Adresse oder deinen Benutzernamen.</p>
    </header>
    <form class="auth-form" :aria-busy="submitting" @submit.prevent="login">
      <p v-if="route.query.registered === '1'" class="success" role="status">Dein Konto wurde erstellt. Du kannst dich jetzt anmelden.</p>
      <div class="field">
        <label for="identifier">E-Mail oder Benutzername</label>
        <input id="identifier" v-model="identifier" name="identifier" type="text" autocomplete="username"
          placeholder="name@beispiel.de" :disabled="submitting" required maxlength="255" />
      </div>
      <div class="field">
        <label for="password">Passwort</label>
        <div class="password-field">
          <input id="password" v-model="password" name="password" :type="showPassword ? 'text' : 'password'"
            autocomplete="current-password" placeholder="Dein Passwort" :disabled="submitting" required />
          <!-- Der Schalter verändert nur die Darstellung; das Passwort wird weiterhin nicht gespeichert. -->
          <button type="button" class="password-toggle" :aria-pressed="showPassword"
            :aria-label="showPassword ? 'Passwort verbergen' : 'Passwort anzeigen'"
            :disabled="submitting" @click="showPassword = !showPassword">
            <UiIcon :name="showPassword ? 'eyeOff' : 'eye'" />
            {{ showPassword ? 'Verbergen' : 'Anzeigen' }}
          </button>
        </div>
      </div>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <button type="submit" class="button" :disabled="submitting">{{ submitting ? 'Anmeldung läuft …' : 'Anmelden' }}<UiIcon name="arrow" /></button>
    </form>
    <p class="auth-switch">Noch kein Konto? <RouterLink :to="{ name: 'register' }">Registrieren</RouterLink></p>
  </AuthLayout>
</template>
