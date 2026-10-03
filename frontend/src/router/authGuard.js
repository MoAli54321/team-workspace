import { useAuthStore } from '../stores/auth.js'

export function authGuard(to) {
  // Pinia wird in main.js vor dem Router eingebunden.
  const authStore = useAuthStore()
  const loggedIn = authStore.hasValidSession()

  if (authStore.token && !loggedIn) {
    authStore.logout()
  }

  if (to.meta.requiresAuth && !loggedIn) {
    return { name: 'login' }
  }

  if ((to.name === 'login' || to.name === 'register') && loggedIn) {
    return { name: 'dashboard' }
  }
}
