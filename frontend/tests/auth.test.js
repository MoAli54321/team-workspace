import assert from 'node:assert/strict'
import { afterEach, beforeEach, mock, test } from 'node:test'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../src/stores/auth.js'

// Der Sitzungsspeicher wird im Test durch eine Map ersetzt, damit kein Browser nötig ist.
// Nach jedem Test wird die ursprüngliche Umgebung wiederhergestellt.
const originalStorage = Object.getOwnPropertyDescriptor(globalThis, 'sessionStorage')
const loginResponse = {
  message: 'Login successful',
  token: 'test-header.test-payload.test-signature',
  tokenType: 'Bearer',
  expiresIn: 3600,
  username: 'test-user',
  userId: 7,
}
const expectedSession = {
  token: loginResponse.token,
  username: loginResponse.username,
  userId: loginResponse.userId,
}
const emptySession = { token: '', username: '', userId: null }
let savedValues

// Jeder Test beginnt mit einem neuen Store und einem leeren Speicher.
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
  if (originalStorage) {
    Object.defineProperty(globalThis, 'sessionStorage', originalStorage)
  } else {
    delete globalThis.sessionStorage
  }
})

test('login sends the credentials and saves only the token and user details', async () => {
  const fetchMock = mock.method(globalThis, 'fetch', async () => Response.json(loginResponse))
  const auth = useAuthStore()

  await auth.login(' test@example.test ', ' Test123! ')

  const [url, options] = fetchMock.mock.calls[0].arguments
  assert.equal(url, '/api/auth/login')
  assert.equal(options.method, 'POST')
  assert.equal(options.headers['Content-Type'], 'application/json')
  assert.deepEqual(JSON.parse(options.body), {
    identifier: 'test@example.test',
    password: ' Test123! ',
  })
  assert.deepEqual(auth.$state, expectedSession)
  assert.deepEqual([...savedValues.keys()], ['auth'])
  assert.deepEqual(JSON.parse(savedValues.get('auth')), expectedSession)
})

test('a new store restores the saved session after a reload', async () => {
  mock.method(globalThis, 'fetch', async () => Response.json(loginResponse))
  const previousStore = useAuthStore()
  await previousStore.login('test-user', 'Test123!')

  setActivePinia(createPinia())
  const restoredStore = useAuthStore()

  assert.notEqual(restoredStore, previousStore)
  assert.deepEqual(restoredStore.$state, expectedSession)
})

test('an unsuccessful login leaves an existing session unchanged', async () => {
  savedValues.set('auth', JSON.stringify(expectedSession))
  mock.method(globalThis, 'fetch', async () => new Response('Invalid credentials', { status: 401 }))
  const auth = useAuthStore()

  await assert.rejects(auth.login('someone-else', 'wrong'), /Passwort ist falsch/)

  assert.deepEqual(auth.$state, expectedSession)
  assert.deepEqual(JSON.parse(savedValues.get('auth')), expectedSession)
})

test('server errors and network errors do not create a session', async () => {
  const fetchMock = mock.method(globalThis, 'fetch', async () => new Response('', { status: 500 }))
  const auth = useAuthStore()

  await assert.rejects(auth.login('test-user', 'Test123!'), /Login fehlgeschlagen/)
  fetchMock.mock.mockImplementation(async () => { throw new TypeError('Failed to fetch') })
  await assert.rejects(auth.login('test-user', 'Test123!'), /Server ist nicht erreichbar/)

  assert.deepEqual(auth.$state, emptySession)
  assert.equal(savedValues.size, 0)
})

test('a success response without a usable token is not treated as a login', async () => {
  const fetchMock = mock.method(globalThis, 'fetch', async () => Response.json({
    message: 'Login successful', username: 'test-user', userId: 7,
  }))
  const auth = useAuthStore()

  await assert.rejects(auth.login('test-user', 'Test123!'), /nicht bestätigt/)
  fetchMock.mock.mockImplementation(async () => new Response('not JSON'))
  await assert.rejects(auth.login('test-user', 'Test123!'), /nicht bestätigt/)

  assert.deepEqual(auth.$state, emptySession)
  assert.equal(savedValues.size, 0)
})

test('a failed storage write does not update the in-memory session', async () => {
  mock.method(globalThis, 'fetch', async () => Response.json(loginResponse))
  mock.method(sessionStorage, 'setItem', () => { throw new Error('Storage disabled') })
  const auth = useAuthStore()

  await assert.rejects(auth.login('test-user', 'Test123!'), /nicht gespeichert/)

  assert.deepEqual(auth.$state, emptySession)
  assert.equal(savedValues.size, 0)
})

test('corrupt or incomplete saved data does not restore a session', () => {
  for (const value of ['not JSON', '{}', '{"token":"token-only"}', '{"token":" ","username":"user"}']) {
    savedValues.set('auth', value)
    setActivePinia(createPinia())
    assert.deepEqual(useAuthStore().$state, emptySession)
  }
})

test('blocked session storage does not prevent creating the store', () => {
  mock.method(sessionStorage, 'getItem', () => { throw new Error('Storage disabled') })
  assert.deepEqual(useAuthStore().$state, emptySession)
})

test('logout clears the auth state and stored session without deleting other data', () => {
  savedValues.set('auth', JSON.stringify(expectedSession))
  savedValues.set('unrelated-setting', 'keep')
  const auth = useAuthStore()

  auth.logout()

  assert.deepEqual(auth.$state, emptySession)
  assert.equal(savedValues.has('auth'), false)
  assert.equal(savedValues.get('unrelated-setting'), 'keep')
  setActivePinia(createPinia())
  assert.deepEqual(useAuthStore().$state, emptySession)
})

test('logout clears the running session even when browser storage is blocked', () => {
  savedValues.set('auth', JSON.stringify(expectedSession))
  const auth = useAuthStore()
  mock.method(sessionStorage, 'removeItem', () => { throw new Error('Storage disabled') })

  assert.doesNotThrow(() => auth.logout())
  assert.deepEqual(auth.$state, emptySession)
})
