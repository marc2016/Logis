import { PageHeader } from '@/components/PageHeader'
import { useCurrentUser } from '@/core/auth/session'

export function DashboardPage() {
  const user = useCurrentUser()
  return (
    <>
      <PageHeader title="Übersicht" description={`Willkommen, ${user.displayName}.`} />
      <p className="text-muted-foreground">Hier entsteht die Übersicht über Konten, Objekte und Buchungen.</p>
    </>
  )
}
