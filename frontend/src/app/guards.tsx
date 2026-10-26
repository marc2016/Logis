import type { ReactNode } from 'react'
import { Outlet } from 'react-router'
import { useCurrentUser } from '@/core/auth/session'
import { useActiveModules } from './useActiveModules'
import { NotFound } from './NotFound'

/** Zeigt Modulseiten nur, wenn das Modul aktiv ist - sonst "Seite nicht gefunden". */
export function ModuleGuard({ moduleId }: { moduleId: string }) {
  const { modules, isLoading } = useActiveModules()
  if (isLoading) return null
  return modules.some((m) => m.id === moduleId) ? <Outlet /> : <NotFound />
}

export function RequireAdmin({ children }: { children: ReactNode }) {
  const user = useCurrentUser()
  return user.role === 'ADMIN' ? children : <NotFound />
}
