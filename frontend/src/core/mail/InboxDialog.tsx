import { useMutation, useQuery } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { ConfirmButton } from '@/components/ConfirmButton'
import { api } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { mailKeys, useInvalidateAfterMail, type InboxItem } from './api'
import { useMailLabels } from './labels'

/** Details of a fetched mail with the generic actions and those of the module that handled it. */
export function InboxDialog({ item, onClose }: { item: InboxItem; onClose: () => void }) {
  const labels = useMailLabels()
  const module = labels.moduleOf(item)
  const invalidate = useInvalidateAfterMail()
  const { data: text } = useQuery({
    queryKey: mailKeys.text(item.id),
    queryFn: () => api<{ text: string | null }>(`/api/mail/inbox/${item.id}/text`),
  })
  const action = useMutation({
    mutationFn: ({ url, method = 'POST' }: { url: string; method?: string; done: string }) =>
      api(url, { method, json: method === 'POST' ? {} : undefined }),
    onSuccess: async (_, { done }) => {
      await invalidate()
      toast.success(done)
      onClose()
    },
    onError: (err) => toast.error(errorMessage(err)),
  })
  const base = `/api/mail/inbox/${item.id}`
  const ModuleActions = module?.MailActions

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{item.subject || 'Mail'}</DialogTitle>
          <DialogDescription className="flex flex-wrap items-center gap-2">
            <Badge variant="outline">{labels.result(item.result)}</Badge>
            {item.kind && <span>{labels.kind(item.kind)}</span>}
            {item.source && <span>· {item.source}</span>}
            {item.parsedBy === 'AI' && <span>· per KI gelesen</span>}
          </DialogDescription>
        </DialogHeader>
        {item.resultDetail && <p className="text-sm">{item.resultDetail}</p>}

        {ModuleActions && (
          <ModuleActions
            item={item}
            onDone={async () => {
              await invalidate()
              onClose()
            }}
          />
        )}

        <div className="flex flex-wrap gap-2">
          {item.retryable && (
            <Button type="button" size="sm" variant="outline" disabled={action.isPending} onClick={() => action.mutate({ url: `${base}/retry`, done: 'Mail erneut verarbeitet.' })}>
              Erneut verarbeiten
            </Button>
          )}
          {item.result !== 'IGNORED' && (
            <Button type="button" size="sm" variant="outline" disabled={action.isPending} onClick={() => action.mutate({ url: `${base}/ignore`, done: 'Mail ignoriert.' })}>
              Ignorieren
            </Button>
          )}
          <ConfirmButton
            question="Mailinhalt samt Gastdaten löschen? Angehängte Kopien bleiben."
            disabled={action.isPending}
            onConfirm={() => action.mutate({ url: base, method: 'DELETE', done: 'Mail gelöscht.' })}
          >
            Löschen
          </ConfirmButton>
        </div>

        <pre className="max-h-80 overflow-auto whitespace-pre-wrap rounded-md bg-muted p-3 text-xs" aria-label="Mailtext">
          {text?.text ?? 'Wird geladen …'}
        </pre>
      </DialogContent>
    </Dialog>
  )
}
