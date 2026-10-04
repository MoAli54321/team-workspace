import { authenticatedFetch } from './request.js'

// Projekte werden immer im Kontext eines Teams geladen oder erstellt.
// encodeURIComponent hält die übergebene ID innerhalb ihres vorgesehenen URL-Abschnitts.
// Der Teambezug bleibt auch beim Löschen erhalten und wird vom Backend geprüft.
export function requestProjects(teamId, options = {}) {
  return authenticatedFetch(`/api/teams/${encodeURIComponent(teamId)}/projects`, options)
}

export function requestProject(teamId, projectId, options = {}) {
  return authenticatedFetch(`/api/teams/${encodeURIComponent(teamId)}/projects/${encodeURIComponent(projectId)}`, options)
}
