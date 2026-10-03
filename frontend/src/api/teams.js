import { authenticatedFetch } from './request.js'

export function requestTeams(options = {}) {
  return authenticatedFetch('/api/teams', options)
}
