import { PageHeader } from '@/components/PageHeader'

export function BelegungPage() {
  return (
    <>
      <PageHeader title="Belegung" description="Belegungskalender der Ferienobjekte." />
      <p className="text-muted-foreground">Der Belegungskalender folgt mit dem Change „vacation-rental“.</p>
    </>
  )
}
