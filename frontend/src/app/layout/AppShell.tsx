import { Link, Outlet } from 'react-router'
import { MailWarningIcon } from 'lucide-react'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { useCurrentUser } from '@/core/auth/session'
import { Separator } from '@/components/ui/separator'
import { SidebarInset, SidebarProvider, SidebarTrigger } from '@/components/ui/sidebar'
import { useActiveModules } from '../useActiveModules'
import { AppSidebar } from './AppSidebar'

/** Reads the cookie set by the shadcn sidebar, so that the state survives a reload. */
function initialSidebarOpen(): boolean {
  const match = document.cookie.split('; ').find((c) => c.startsWith('sidebar_state='))
  return match ? match.split('=')[1] !== 'false' : true
}

/** Shown until the user has an e-mail address (spec authentication, „Notice about a missing address“). */
function MissingEmailNotice() {
  const user = useCurrentUser()
  if (!user.emailMissing) return null
  return (
    <Alert className="mb-4">
      <MailWarningIcon />
      <AlertDescription>
        <span>
          Bitte hinterlege eine E-Mail-Adresse unter{' '}
          <Link to="/konto" className="font-medium underline underline-offset-4">
            Mein Konto
          </Link>
          .
        </span>
      </AlertDescription>
    </Alert>
  )
}

export function AppShell() {
  const { modules } = useActiveModules()

  return (
    <SidebarProvider defaultOpen={initialSidebarOpen()}>
      <AppSidebar modules={modules} />
      <SidebarInset className="min-w-0">
        <header className="flex h-14 shrink-0 items-center gap-2 border-b px-4">
          <SidebarTrigger aria-label="Menü umschalten" />
          <Separator orientation="vertical" className="mx-1 h-4" />
          <span className="text-sm text-muted-foreground">Logis</span>
        </header>
        {/* Size container: pages pick their columns with @…: variants by the width of the content area,
            which is the window minus the open sidebar, rather than by the window width. */}
        <div className="@container flex-1 p-4 md:p-6">
          <MissingEmailNotice />
          <Outlet />
        </div>
      </SidebarInset>
    </SidebarProvider>
  )
}
