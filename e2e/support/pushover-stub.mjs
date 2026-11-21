// Stand-in for the Pushover API in the end-to-end stack: answers the three calls Logis makes as the Pushover
// documentation describes and keeps the received messages for the tests to read. Never reachable from outside.
//
//   GET  /1/apps/limits.json?token=…   the application token alone
//   POST /1/users/validate.json        token and user key
//   POST /1/messages.json              a message
//   GET  /messages?user=…              the messages a test's user key received (for the tests only)
//
// Accepts exactly the application token below and every alphanumeric user key that does not start with "invalid".
import http from 'node:http'

const TOKEN = process.env.PUSHOVER_STUB_TOKEN ?? 'e2eapptoken0000000000000000000'
const PORT = Number(process.env.PUSHOVER_STUB_PORT ?? 3080)
const messages = []

function reply(res, status, body) {
  res.writeHead(status, { 'Content-Type': 'application/json' })
  res.end(JSON.stringify(body))
}

function invalid(res, parameter, message) {
  reply(res, 400, { [parameter]: 'invalid', errors: [message], status: 0, request: 'e2e' })
}

function checked(res, form, withUser) {
  if (form.get('token') !== TOKEN) {
    invalid(res, 'token', 'application token is invalid')
    return false
  }
  const user = form.get('user') ?? ''
  if (withUser && (!/^[A-Za-z0-9]+$/.test(user) || user.startsWith('invalid'))) {
    invalid(res, 'user', 'user identifier is invalid')
    return false
  }
  return true
}

http
  .createServer((req, res) => {
    const url = new URL(req.url, 'http://stub')
    let raw = ''
    req.on('data', (chunk) => (raw += chunk))
    req.on('end', () => {
      const form = new URLSearchParams(req.method === 'POST' ? raw : url.search)
      if (req.method === 'GET' && url.pathname === '/1/apps/limits.json') {
        if (checked(res, form, false)) reply(res, 200, { limit: 10000, remaining: 10000 - messages.length, reset: 0, status: 1, request: 'e2e' })
      } else if (req.method === 'POST' && url.pathname === '/1/users/validate.json') {
        if (checked(res, form, true)) reply(res, 200, { status: 1, devices: ['e2e'], licenses: [], request: 'e2e' })
      } else if (req.method === 'POST' && url.pathname === '/1/messages.json') {
        if (!checked(res, form, true)) return
        if (!form.get('message')) return invalid(res, 'message', 'message cannot be blank')
        messages.push(Object.fromEntries(form))
        reply(res, 200, { status: 1, request: 'e2e' })
      } else if (req.method === 'GET' && url.pathname === '/messages') {
        const user = url.searchParams.get('user')
        reply(res, 200, messages.filter((m) => !user || m.user === user))
      } else {
        reply(res, 404, { status: 0, errors: ['not found'] })
      }
    })
  })
  .listen(PORT, '0.0.0.0', () => console.log(`Pushover stand-in listening on ${PORT}`))
