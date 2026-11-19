import { useEffect, type ReactNode } from 'react'
import { Outlet } from 'react-router'
import { useQueryClient } from '@tanstack/react-query'
import { ApiError } from '@/lib/api'
import { ReauthenticationNotice } from '@/core/auth/ReauthenticationNotice'
import { sessionQueryKey, useCurrentUser, useSession } from '@/core/auth/session'
import { useActiveModules } from './useActiveModules'
import { NotFound } from './NotFound'

/** Shows module pages only if the module is active - otherwise "page not found". */
export function ModuleGuard({ moduleId }: { moduleId: string }) {
  const { modules, isLoading } = useActiveModules()
  if (isLoading) return null
  return modules.some((m) => m.id === moduleId) ? <Outlet /> : <NotFound />
}

const needsReauthentication = (error: unknown) => error instanceof ApiError && error.reauthenticationRequired

/**
 * Admin pages: only for admins, and only after a fresh sign-in. If the session was started by a remembered
 * device - known from `/me`, or noticed when an admin request is refused - the page shows the sign-in notice.
 */
export function RequireAdmin({ children }: { children: ReactNode }) {
  const user = useCurrentUser()
  const { data: session } = useSession()
  const queryClient = useQueryClient()

  useEffect(() => {
    const refresh = () => queryClient.invalidateQueries({ queryKey: sessionQueryKey })
    const offQueries = queryClient.getQueryCache().subscribe((event) => {
      if (event.type === 'updated' && needsReauthentication(event.query.state.error)) refresh()
    })
    const offMutations = queryClient.getMutationCache().subscribe((event) => {
      if (event.type === 'updated' && needsReauthentication(event.mutation?.state.error)) refresh()
    })
    return () => {
      offQueries()
      offMutations()
    }
  }, [queryClient])

  if (user.role !== 'ADMIN') return <NotFound />
  if (session?.reauthenticationRequired) return <ReauthenticationNotice />
  return children
}
