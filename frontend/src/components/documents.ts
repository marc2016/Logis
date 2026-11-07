import { api } from '@/lib/api'

export interface DocumentInfo {
  id: number
  fileName: string
  contentType: string
  sizeBytes: number
  createdAt: string
}

export const DOCUMENT_ACCEPT = 'application/pdf,image/jpeg,image/png,image/webp,image/heic,.heic,.pdf'
export const MAX_BYTES = 10 * 1024 * 1024
export const DOCUMENT_RULE = 'Nur PDF- oder Bilddateien bis 10 MB'

export const documentKeys = {
  list: (ownerType: string, ownerId: number) => ['documents', ownerType, ownerId] as const,
}

export function formatSize(bytes: number) {
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`
  return `${(bytes / 1024 / 1024).toFixed(1).replace('.', ',')} MB`
}

/** Uploads one file to an owner; used directly and after creating a new owner. */
export function uploadDocument(ownerType: string, ownerId: number, file: File) {
  const body = new FormData()
  body.set('ownerType', ownerType)
  body.set('ownerId', String(ownerId))
  body.set('file', file)
  return api<DocumentInfo>('/api/documents', { method: 'POST', body })
}
