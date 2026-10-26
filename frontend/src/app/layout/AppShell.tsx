import { Outlet } from 'react-router'
import { Separator } from '@/components/ui/separator'
import { SidebarInset, SidebarProvider, SidebarTrigger } from '@/components/ui/sidebar'
import { useActiveModules } from '../useActiveModules'
import { AppSidebar } from './AppSidebar'

/** Liest den von der shadcn-Sidebar gesetzten Cookie, damit der Zustand einen Reload uebersteht. */
function initialSidebarOpen(): boolean {
  const match = document.cookie.split('; ').find((c) => c.startsWith('sidebar_state='))
  return match ? match.split('=')[1] !== 'false' : true
}

export function AppShell() {
  const { modules } = useActiveModules()

  return (
    <SidebarProvider defaultOpen={initialSidebarOpen()}>
      <AppSidebar modules={modules} />
      <SidebarInset>
        <header className="flex h-14 shrink-0 items-center gap-2 border-b px-4">
          <SidebarTrigger aria-label="Menü umschalten" />
          <Separator orientation="vertical" className="mx-1 h-4" />
          <span className="text-sm text-muted-foreground">Logis</span>
        </header>
        <div className="flex-1 p-4 md:p-6">
          <Outlet />
        </div>
      </SidebarInset>
    </SidebarProvider>
  )
}
