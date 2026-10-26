import { ApiError } from './api'

export function errorMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Ein unerwarteter Fehler ist aufgetreten.'
}
