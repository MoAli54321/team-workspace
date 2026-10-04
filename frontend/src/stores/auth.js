import { defineStore } from 'pinia'

// Pinia hält die Sitzung für die laufende Oberfläche bereit. sessionStorage erhält sie
// beim Neuladen im selben Tab; das Passwort gehört nicht zu diesen gespeicherten Daten.
const STORAGE_KEY = 'auth'

function emptySession() {
  return { token: '', username: '', userId: null }
}

// Prüft die Form der gespeicherten Daten. Die Echtheit des JWT prüft weiterhin das Backend.
function readSession(data) {
  if (
    typeof data?.token !== 'string' || !data.token.trim() ||
    typeof data?.username !== 'string' || !data.username.trim() ||
    (data.userId != null && (!Number.isSafeInteger(data.userId) || data.userId <= 0))
  ) {
    throw new Error('Die Anmeldung konnte nicht bestätigt werden. Bitte versuche es erneut.')
  }

  // Nur diese drei Werte werden gespeichert, niemals das eingegebene Passwort.
  return { token: data.token, username: data.username, userId: data.userId ?? null }
}

function restoreSession() {
  try {
    return readSession(JSON.parse(sessionStorage.getItem(STORAGE_KEY)))
  } catch {
    // Ein leerer, beschädigter oder gesperrter Sitzungsspeicher verhindert nicht den App-Start.
    return emptySession()
  }
}

export const useAuthStore = defineStore('auth', {
  state: restoreSession,

  actions: {
    hasValidSession() {
      // Nur eine Prüfung für die Navigation; die Signatur prüft weiterhin das Backend.
      try {
        const parts = this.token.split('.')
        if (parts.length !== 3 || parts.some(part => !/^[A-Za-z0-9_-]+$/.test(part))) return false

        const payload = parts[1].replace(/-/g, '+').replace(/_/g, '/')
        const bytes = Uint8Array.from(atob(payload), character => character.charCodeAt(0))
        const claims = JSON.parse(new TextDecoder().decode(bytes))
        return Number.isFinite(claims.exp) && claims.exp * 1000 > Date.now()
      } catch {
        return false
      }
    },

    // Die lokale Sitzung wird entfernt. Bereits ausgestellte JWTs werden dadurch
    // nicht serverseitig widerrufen und bleiben bis zu ihrem Ablauf grundsätzlich gültig.
    logout() {
      this.$patch(emptySession())
      try {
        sessionStorage.removeItem(STORAGE_KEY)
      } catch {
        // Auch bei gesperrtem Browserspeicher bleibt der laufende Store abgemeldet.
      }
    },

    // Erst eine erfolgreiche API-Antwort und ein erfolgreicher Speichervorgang
    // machen den Benutzer für die Oberfläche angemeldet.
    async login(identifier, password) {
      let response

      try {
        response = await fetch('/api/auth/login', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ identifier: identifier.trim(), password }),
        })
      } catch {
        throw new Error('Der Server ist nicht erreichbar. Bitte versuche es erneut.')
      }

      if (response.status === 401) {
        throw new Error('E-Mail, Benutzername oder Passwort ist falsch.')
      }

      if (!response.ok) {
        throw new Error('Login fehlgeschlagen. Bitte versuche es erneut.')
      }

      let data
      try {
        data = await response.json()
      } catch {
        throw new Error('Die Anmeldung konnte nicht bestätigt werden. Bitte versuche es erneut.')
      }

      const session = readSession(data)

      try {
        // Ein gemeinsamer Eintrag hält Token und Benutzerdaten zusammen.
        sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session))
      } catch {
        throw new Error('Die Anmeldung konnte im Browser nicht gespeichert werden. Bitte prüfe deine Browsereinstellungen.')
      }

      // Erst nach erfolgreichem Speichern wird auch der reaktive Store aktualisiert.
      this.$patch(session)
    },
  },
})
