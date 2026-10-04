import { useAuthStore } from '../stores/auth.js'

// Gemeinsamer Einstieg für geschützte API-Anfragen. Die einzelnen API-Dateien bestimmen die URL,
// während dieser Helfer den aktuellen Token ergänzt und eine abgelaufene Sitzung behandelt.
export async function authenticatedFetch(url, options = {}) {
  const authStore = useAuthStore()
  // Eine Kopie erhält vorhandene Header, ohne die Optionen des Aufrufers zu verändern.
  const headers = new Headers(options.headers)
  const requestToken = authStore.token

  if (requestToken) {
    headers.set('Authorization', `Bearer ${requestToken}`)
  } else {
    headers.delete('Authorization')
  }

  const response = await fetch(url, { ...options, headers })

  if (response.status === 401) {
    // Eine verspätete Antwort darf eine inzwischen neu angemeldete Sitzung nicht abmelden.
    if (authStore.token === requestToken) {
      authStore.logout()
    }
    throw new Error('Bitte melde dich erneut an.')
  }

  // Andere Fehler bleiben bei der jeweiligen Seite, die dazu eine passende Meldung anzeigen kann.
  return response
}
