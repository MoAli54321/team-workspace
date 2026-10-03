import { authenticatedFetch } from './request.js'

export function requestProjectTasks(projectId, options = {}) {
  return authenticatedFetch(`/api/projects/${encodeURIComponent(projectId)}/tasks`, options)
}

export function requestTask(taskId, options = {}) {
  return authenticatedFetch(`/api/tasks/${encodeURIComponent(taskId)}`, options)
}
