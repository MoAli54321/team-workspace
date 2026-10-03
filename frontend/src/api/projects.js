import { authenticatedFetch } from './request.js'

export function requestProjects(teamId, options = {}) {
  return authenticatedFetch(`/api/teams/${encodeURIComponent(teamId)}/projects`, options)
}
