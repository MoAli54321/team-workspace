import { authenticatedFetch } from './request.js'

// Lesen und Erstellen erfolgen über das Projekt, damit neue Aufgaben direkt zugeordnet sind.
export function requestProjectTasks(projectId, options = {}) {
  return authenticatedFetch(`/api/projects/${encodeURIComponent(projectId)}/tasks`, options)
}

// Bearbeiten und Löschen nutzen die Aufgaben-ID; das Backend ermittelt daraus das Teamrecht.
export function requestTask(taskId, options = {}) {
  return authenticatedFetch(`/api/tasks/${encodeURIComponent(taskId)}`, options)
}
