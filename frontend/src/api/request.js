import { useAuthStore } from '../stores/auth.js'

export async function authenticatedFetch(url, options = {}) {
  const authStore = useAuthStore()
  const headers = new Headers(options.headers)
  const requestToken = authStore.token

  if (requestToken) {
    headers.set('Authorization', `Bearer ${requestToken}`)
  } else {
    headers.delete('Authorization')
  }

  const response = await fetch(url, { ...options, headers })

  if (response.status === 401) {
    if (authStore.token === requestToken) {
      authStore.logout()
    }
    throw new Error('Bitte melde dich erneut an.')
  }

  return response
}
