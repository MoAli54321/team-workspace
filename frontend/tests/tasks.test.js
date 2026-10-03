import assert from 'node:assert/strict'
import { afterEach, beforeEach, mock, test } from 'node:test'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../src/stores/auth.js'
import { requestTasks } from '../src/api/tasks.js'

beforeEach(() => {
  setActivePinia(createPinia())
  useAuthStore().$patch({ token: 'current-token', username: 'test-user', userId: 7 })
})

afterEach(() => mock.restoreAll())

for (const method of ['GET', 'POST', 'PUT', 'DELETE']) {
  test(`${method} task requests carry the token and preserve existing request data`, async () => {
    const response = Response.json([])
    const fetchMock = mock.method(globalThis, 'fetch', async () => response)
    const hasBody = method === 'POST' || method === 'PUT'
    const body = hasBody ? JSON.stringify({ title: 'Test task' }) : undefined
    const headers = new Headers(hasBody ? { 'Content-Type': 'application/json' } : {})
    const path = method === 'PUT' || method === 'DELETE' ? '/42' : ''

    assert.equal(await requestTasks(path, { method, headers, body }), response)

    const [url, options] = fetchMock.mock.calls[0].arguments
    assert.equal(url, `/api/tasks${path}`)
    assert.equal(options.method, method)
    assert.equal(options.headers.get('Authorization'), 'Bearer current-token')
    assert.equal(options.headers.get('Content-Type'), hasBody ? 'application/json' : null)
    assert.equal(options.body, body)
    assert.equal(headers.has('Authorization'), false)
  })
}

test('each request uses the latest token from the auth store', async () => {
  const fetchMock = mock.method(globalThis, 'fetch', async () => Response.json([]))
  await requestTasks()
  useAuthStore().$patch({ token: 'new-login-token' })
  await requestTasks('', { headers: { Authorization: 'Bearer outdated-token' } })

  assert.equal(fetchMock.mock.calls[0].arguments[1].headers.get('Authorization'), 'Bearer current-token')
  assert.equal(fetchMock.mock.calls[1].arguments[1].headers.get('Authorization'), 'Bearer new-login-token')
})

test('without a token no bearer header is sent and HTTP 401 asks for login', async () => {
  useAuthStore().$patch({ token: '' })
  const fetchMock = mock.method(globalThis, 'fetch', async () => new Response(null, { status: 401 }))

  await assert.rejects(requestTasks(), /Bitte melde dich erneut an/)

  assert.equal(fetchMock.mock.calls[0].arguments[1].headers.has('Authorization'), false)
})

test('an expired or rejected token results in a login message', async () => {
  mock.method(globalThis, 'fetch', async () => new Response(null, { status: 401 }))
  await assert.rejects(requestTasks(), /Bitte melde dich erneut an/)
  assert.deepEqual(useAuthStore().$state, { token: '', username: '', userId: null })
})

test('a late unauthorized response does not log out a newer session', async () => {
  let respond
  mock.method(globalThis, 'fetch', () => new Promise(resolve => { respond = resolve }))
  const pendingRequest = requestTasks()
  useAuthStore().$patch({ token: 'newer-session-token' })
  respond(new Response(null, { status: 401 }))

  await assert.rejects(pendingRequest, /Bitte melde dich erneut an/)
  assert.equal(useAuthStore().token, 'newer-session-token')
})

test('other HTTP errors remain available to the task-specific error handling', async () => {
  const response = new Response(null, { status: 500 })
  mock.method(globalThis, 'fetch', async () => response)
  assert.equal(await requestTasks(), response)
})
