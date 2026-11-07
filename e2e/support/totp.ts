import crypto from 'node:crypto'

const ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'
/** Last used time step per secret: the backend accepts each code only once. */
const used = new Map<string, number>()

function code(secret: string, step: number) {
  let bits = ''
  for (const c of secret.replace(/=+$/, '').toUpperCase()) bits += ALPHABET.indexOf(c).toString(2).padStart(5, '0')
  const key = Buffer.from(bits.match(/.{8}/g)!.map((b) => parseInt(b, 2)))
  const counter = Buffer.alloc(8)
  counter.writeBigUInt64BE(BigInt(step))
  const h = crypto.createHmac('sha1', key).update(counter).digest()
  const o = h[h.length - 1] & 0xf
  return String((h.readUInt32BE(o) & 0x7fffffff) % 1_000_000).padStart(6, '0')
}

/** A TOTP code (RFC 6238, 30 s, 6 digits) that was not used yet; waits for the next time step if needed. */
export async function freshTotp(secret: string): Promise<string> {
  let step = Math.floor(Date.now() / 30_000)
  if ((used.get(secret) ?? -1) >= step) {
    await new Promise((r) => setTimeout(r, (step + 1) * 30_000 - Date.now() + 200))
    step = Math.floor(Date.now() / 30_000)
  }
  used.set(secret, step)
  return code(secret, step)
}

/** The last time step used for a secret, to carry it across runs. */
export function lastStep(secret: string) {
  return used.get(secret)
}

export function rememberStep(secret: string, step: number | undefined) {
  if (step !== undefined && step > (used.get(secret) ?? -1)) used.set(secret, step)
}
