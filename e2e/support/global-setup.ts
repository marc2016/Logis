import fs from 'node:fs'
import path from 'node:path'
import { request, type FullConfig } from '@playwright/test'
import { signIn, type Credentials } from './api'

export const AUTH_DIR = path.join(import.meta.dirname, '..', '.auth')
export const ADMIN_FILE = path.join(AUTH_DIR, 'admin.json')
export const ADMIN_STATE = path.join(AUTH_DIR, 'admin-state.json')

/**
 * Signs in the initial admin once and keeps its session for creating test users.
 * Its password and TOTP secret are kept in .auth/ so the suite can run again against the same stack.
 */
export default async function globalSetup(config: FullConfig) {
  const baseURL = config.projects[0].use.baseURL!
  fs.mkdirSync(AUTH_DIR, { recursive: true })
  const admin: Credentials = fs.existsSync(ADMIN_FILE)
    ? JSON.parse(fs.readFileSync(ADMIN_FILE, 'utf8'))
    : { username: process.env.LOGIS_E2E_ADMIN_USERNAME ?? 'admin', password: process.env.LOGIS_E2E_ADMIN_PASSWORD ?? 'e2e-admin-passwort' }
  const context = await request.newContext({ baseURL })
  try {
    await signIn(context, admin)
  } finally {
    fs.writeFileSync(ADMIN_FILE, JSON.stringify(admin))
  }
  await context.storageState({ path: ADMIN_STATE })
  await context.dispose()
}
