import { useAuthStore } from '../stores/auth.js'

// Entscheidet vor jeder Navigation, ob die Zielseite zur aktuellen Sitzung passt.
// Das verbessert die Benutzerführung; den Zugriff auf Daten schützt zusätzlich das Backend.
export function authGuard(to) {
  // Pinia wird in main.js vor dem Router eingebunden.
  const authStore = useAuthStore()
  const loggedIn = authStore.hasValidSession()

  // Auch eine wiederhergestellte Sitzung kann inzwischen abgelaufen sein.
  if (authStore.token && !loggedIn) {
    authStore.logout()
  }

  if (to.meta.requiresAuth && !loggedIn) {
    return { name: 'login' }
  }

  // Angemeldete Benutzer gelangen von den Anmeldeseiten direkt zur Teamübersicht.
  if ((to.name === 'login' || to.name === 'register') && loggedIn) {
    return { name: 'dashboard' }
  }
}
