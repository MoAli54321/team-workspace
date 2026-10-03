import assert from 'node:assert/strict'
import { afterEach, beforeEach, mock, test } from 'node:test'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { authGuard } from '../src/router/authGuard.js'
import { useAuthStore } from '../src/stores/auth.js'
import { requestTasks } from '../src/api/tasks.js'

const originalStorage = Object.getOwnPropertyDescriptor(globalThis, 'sessionStorage')
let savedValues

beforeEach(() => {
  savedValues = new Map()
  Object.defineProperty(globalThis, 'sessionStorage', {
    configurable: true,
    value: {
      getItem: key => savedValues.get(key) ?? null,
      setItem: (key, value) => savedValues.set(key, String(value)),
      removeItem: key => savedValues.delete(key),
    },
  })
  setActivePinia(createPinia())
})

afterEach(() => {
  mock.restoreAll()
  if (originalStorage) Object.defineProperty(globalThis, 'sessionStorage', originalStorage)
  else delete globalThis.sessionStorage
})

function makeToken(expiresAt = Math.floor(Date.now() / 1000) + 3600) {
  const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url')
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({ sub: '7', username: 'test-user', exp: expiresAt })}.testSignature`
}

function saveSession(token = makeToken()) {
  savedValues.set('auth', JSON.stringify({ token, username: 'test-user', userId: 7 }))
}

function makeRouter() {
  const component = { template: '<div />' }
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', name: 'login', component },
      { path: '/register', name: 'register', component },
      { path: '/dashboard', name: 'dashboard', component, meta: { requiresAuth: true } },
    ],
  })
  router.beforeEach(authGuard)
  return router
}

test('an anonymous direct dashboard visit redirects to login', async () => {
  const router = makeRouter()
  await router.push('/dashboard')
  assert.equal(router.currentRoute.value.name, 'login')
})

for (const path of ['/login', '/register']) {
  test(`a restored active session redirects ${path} to the dashboard`, async () => {
    saveSession()
    const router = makeRouter()
    await router.push(path)
    assert.equal(router.currentRoute.value.name, 'dashboard')
  })
}

test('logout followed by a new dashboard visit cannot restore the previous session', async () => {
  saveSession()
  const router = makeRouter()
  await router.push('/dashboard')
  assert.equal(router.currentRoute.value.name, 'dashboard')

  useAuthStore().logout()
  await router.replace({ name: 'login' })
  await router.push('/dashboard')
  assert.equal(router.currentRoute.value.name, 'login')
  assert.equal(savedValues.has('auth'), false)

  setActivePinia(createPinia())
  const reloadedRouter = makeRouter()
  await reloadedRouter.push('/dashboard')
  assert.equal(reloadedRouter.currentRoute.value.name, 'login')
})

test('corrupt stored JSON does not crash routing or prevent registration', async () => {
  savedValues.set('auth', 'not JSON')
  const router = makeRouter()
  await router.push('/register')
  assert.equal(router.currentRoute.value.name, 'register')
  await router.push('/dashboard')
  assert.equal(router.currentRoute.value.name, 'login')
})

test('expired and malformed tokens are cleared without a redirect loop', async () => {
  for (const token of [makeToken(1), 'not-a-jwt', 'header.e30.signature']) {
    setActivePinia(createPinia())
    saveSession(token)
    const router = makeRouter()
    await router.push('/dashboard')
    assert.equal(router.currentRoute.value.name, 'login')
    assert.equal(savedValues.has('auth'), false)
    assert.equal(useAuthStore().token, '')
  }
})

test('expiry is evaluated again on navigation even when the token has not changed', async () => {
  const now = Date.now()
  saveSession(makeToken(Math.floor(now / 1000) + 60))
  const router = makeRouter()
  await router.push('/dashboard')
  assert.equal(router.currentRoute.value.name, 'dashboard')

  mock.method(Date, 'now', () => now + 120000)
  await router.push('/login')
  assert.equal(router.currentRoute.value.name, 'login')
  assert.equal(useAuthStore().token, '')
})

test('an API-rejected session can reach login instead of being redirected back', async () => {
  saveSession()
  const router = makeRouter()
  await router.push('/dashboard')
  mock.method(globalThis, 'fetch', async () => new Response(null, { status: 401 }))

  await assert.rejects(requestTasks(), /Bitte melde dich erneut an/)
  await router.replace({ name: 'login' })

  assert.equal(router.currentRoute.value.name, 'login')
  assert.equal(savedValues.has('auth'), false)
})
