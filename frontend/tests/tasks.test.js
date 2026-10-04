import assert from 'node:assert/strict'
import { afterEach, beforeEach, mock, test } from 'node:test'
import { createPinia, setActivePinia } from 'pinia'
import { requestProject, requestProjects } from '../src/api/projects.js'
import { requestProjectTasks, requestTask } from '../src/api/tasks.js'
import { requestTeam, requestTeamMember, requestTeamMembers, requestTeams } from '../src/api/teams.js'
import { useAuthStore } from '../src/stores/auth.js'

beforeEach(() => {
  setActivePinia(createPinia())
  useAuthStore().$patch({ token: 'current-token', username: 'test-user', userId: 7 })
})

afterEach(() => mock.restoreAll())

// Ein gemeinsamer Testlauf prüft URL, HTTP-Methode und Token für alle geschützten API-Helfer.
// fetch wird ersetzt, deshalb gehen dabei keine Anfragen an das laufende Backend.
const apiCases = [
  ['teams', () => requestTeams(), '/api/teams', 'GET'],
  ['team deletion', () => requestTeam(12, { method: 'DELETE' }), '/api/teams/12', 'DELETE'],
  ['projects', () => requestProjects(12), '/api/teams/12/projects', 'GET'],
  ['project deletion', () => requestProject(12, 34, { method: 'DELETE' }), '/api/teams/12/projects/34', 'DELETE'],
  ['team roster', () => requestTeamMembers(12), '/api/teams/12/members', 'GET'],
  ['member removal', () => requestTeamMember(12, 7, { method: 'DELETE' }), '/api/teams/12/members/7', 'DELETE'],
  ['project tasks', () => requestProjectTasks(34), '/api/projects/34/tasks', 'GET'],
  ['single task', () => requestTask(56, { method: 'DELETE' }), '/api/tasks/56', 'DELETE'],
]

for (const [name, request, expectedUrl, expectedMethod] of apiCases) {
  test(`${name} requests use the correct URL and current token`, async () => {
    const response = Response.json([])
    const fetchMock = mock.method(globalThis, 'fetch', async () => response)

    assert.equal(await request(), response)

    const [url, options] = fetchMock.mock.calls[0].arguments
    assert.equal(url, expectedUrl)
    assert.equal(options.method ?? 'GET', expectedMethod)
    assert.equal(options.headers.get('Authorization'), 'Bearer current-token')
  })
}

test('request options and JSON headers are preserved without mutating caller headers', async () => {
  const response = Response.json({})
  const fetchMock = mock.method(globalThis, 'fetch', async () => response)
  const body = JSON.stringify({ title: 'Test task' })
  const headers = new Headers({ 'Content-Type': 'application/json' })

  assert.equal(await requestProjectTasks(4, { method: 'POST', headers, body }), response)

  const [url, options] = fetchMock.mock.calls[0].arguments
  assert.equal(url, '/api/projects/4/tasks')
  assert.equal(options.method, 'POST')
  assert.equal(options.headers.get('Authorization'), 'Bearer current-token')
  assert.equal(options.headers.get('Content-Type'), 'application/json')
  assert.equal(options.body, body)
  assert.equal(headers.has('Authorization'), false)
})

test('each request uses the latest token from the auth store', async () => {
  const fetchMock = mock.method(globalThis, 'fetch', async () => Response.json([]))
  await requestTeams()
  useAuthStore().$patch({ token: 'new-login-token' })
  await requestProjects(1, { headers: { Authorization: 'Bearer outdated-token' } })

  assert.equal(fetchMock.mock.calls[0].arguments[1].headers.get('Authorization'), 'Bearer current-token')
  assert.equal(fetchMock.mock.calls[1].arguments[1].headers.get('Authorization'), 'Bearer new-login-token')
})

test('without a token no bearer header is sent and HTTP 401 asks for login', async () => {
  useAuthStore().$patch({ token: '' })
  const fetchMock = mock.method(globalThis, 'fetch', async () => new Response(null, { status: 401 }))

  await assert.rejects(requestTeams(), /Bitte melde dich erneut an/)

  assert.equal(fetchMock.mock.calls[0].arguments[1].headers.has('Authorization'), false)
})

test('an expired or rejected token clears the current session', async () => {
  mock.method(globalThis, 'fetch', async () => new Response(null, { status: 401 }))
  await assert.rejects(requestProjectTasks(1), /Bitte melde dich erneut an/)
  assert.deepEqual(useAuthStore().$state, { token: '', username: '', userId: null })
})

// Bildet den Fall nach, dass sich jemand neu anmeldet, während eine alte Anfrage noch unterwegs ist.
test('a late unauthorized response does not log out a newer session', async () => {
  let respond
  mock.method(globalThis, 'fetch', () => new Promise(resolve => { respond = resolve }))
  const pendingRequest = requestTeams()
  useAuthStore().$patch({ token: 'newer-session-token' })
  respond(new Response(null, { status: 401 }))

  await assert.rejects(pendingRequest, /Bitte melde dich erneut an/)
  assert.equal(useAuthStore().token, 'newer-session-token')
})

test('other HTTP errors remain available to the view-specific error handling', async () => {
  const response = new Response(null, { status: 500 })
  mock.method(globalThis, 'fetch', async () => response)
  assert.equal(await requestProjects(1), response)
})
