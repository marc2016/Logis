import { Navigate, Outlet, useLocation } from 'react-router'
import { useSession } from './session'
import { FullPageSpinner } from '@/components/FullPageSpinner'

/** Lets only fully signed-in users into the app shell. */
export function AuthGate() {
  const { data, isLoading } = useSession()
  const location = useLocation()

  if (isLoading) return <FullPageSpinner />
  if (data?.stage !== 'AUTHENTICATED') {
    const next = encodeURIComponent(location.pathname + location.search)
    return <Navigate to={`/login?next=${next}`} replace />
  }
  return <Outlet />
}
