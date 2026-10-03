import { useAuthStore } from '../stores/auth.js'

// Alle Task-Anfragen lesen den aktuellen Token aus demselben Auth-Store.
export async function requestTasks(path = '', options = {}) {
  const authStore = useAuthStore()
  const headers = new Headers(options.headers)
  const requestToken = authStore.token

  if (requestToken) {
    headers.set('Authorization', `Bearer ${requestToken}`)
  } else {
    headers.delete('Authorization')
  }

  const response = await fetch(`/api/tasks${path}`, { ...options, headers })

  if (response.status === 401) {
    // Eine verspätete Antwort darf eine inzwischen neu angemeldete Sitzung nicht löschen.
    if (authStore.token === requestToken) {
      authStore.logout()
    }
    throw new Error('Bitte melde dich erneut an, um auf die Aufgaben zuzugreifen.')
  }

  return response
}
