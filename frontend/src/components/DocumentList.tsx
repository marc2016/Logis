import { useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { DownloadIcon, FileIcon, PaperclipIcon, Trash2Icon, XIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { api } from '@/lib/api'
import { DOCUMENT_ACCEPT, DOCUMENT_RULE, documentKeys, formatSize, MAX_BYTES, uploadDocument, type DocumentInfo } from './documents'
import { errorMessage } from '@/lib/errors'

function tooLarge(file: File) {
  return file.size > MAX_BYTES
}

interface ExistingProps {
  ownerType: string
  ownerId: number
  canEdit: boolean
}

/** Documents of an existing owner: list, download, upload right away, delete. */
export function DocumentList({ ownerType, ownerId, canEdit }: ExistingProps) {
  const queryClient = useQueryClient()
  const key = documentKeys.list(ownerType, ownerId)
  const { data: documents = [] } = useQuery({
    queryKey: key,
    queryFn: () => api<DocumentInfo[]>(`/api/documents?ownerType=${encodeURIComponent(ownerType)}&ownerId=${ownerId}`),
  })
  const [errors, setErrors] = useState<string[]>([])
  const refresh = () => queryClient.invalidateQueries({ queryKey: key })

  const upload = useMutation({
    mutationFn: async (files: File[]) => {
      const failed: string[] = []
      for (const file of files) {
        if (tooLarge(file)) {
          failed.push(`${file.name}: ${DOCUMENT_RULE}`)
          continue
        }
        try {
          await uploadDocument(ownerType, ownerId, file)
        } catch (err) {
          failed.push(`${file.name}: ${errorMessage(err)}`)
        }
      }
      return failed
    },
    onSuccess: async (failed) => {
      setErrors(failed)
      await refresh()
    },
  })
  const remove = useMutation({
    mutationFn: (id: number) => api(`/api/documents/${id}`, { method: 'DELETE' }),
    onSuccess: refresh,
    onError: (err) => setErrors([errorMessage(err)]),
  })

  return (
    <div className="space-y-2">
      {documents.length > 0 && (
        <ul className="divide-y rounded-md border text-sm">
          {documents.map((d) => (
            <li key={d.id} className="flex items-center gap-2 px-3 py-2">
              <FileIcon className="size-4 shrink-0 text-muted-foreground" />
              <span className="min-w-0 flex-1 truncate">{d.fileName}</span>
              <span className="text-xs text-muted-foreground">{formatSize(d.sizeBytes)}</span>
              <Button variant="ghost" size="icon" asChild aria-label={`${d.fileName} herunterladen`}>
                <a href={`/api/documents/${d.id}`} download={d.fileName}>
                  <DownloadIcon />
                </a>
              </Button>
              {canEdit && (
                <Button type="button" variant="ghost" size="icon" aria-label={`${d.fileName} löschen`} disabled={remove.isPending} onClick={() => remove.mutate(d.id)}>
                  <Trash2Icon />
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
      {canEdit && <FilePicker disabled={upload.isPending} onPick={(files) => upload.mutate(files)} />}
      <FileErrors errors={errors} />
    </div>
  )
}

interface PendingProps {
  files: File[]
  onChange: (files: File[]) => void
}

/** Files chosen for an owner that does not exist yet; uploaded after saving. */
export function PendingDocuments({ files, onChange }: PendingProps) {
  const invalid = files.filter(tooLarge)
  return (
    <div className="space-y-2">
      {files.length > 0 && (
        <ul className="divide-y rounded-md border text-sm">
          {files.map((f, i) => (
            <li key={`${f.name}-${i}`} className="flex items-center gap-2 px-3 py-2">
              <FileIcon className="size-4 shrink-0 text-muted-foreground" />
              <span className="min-w-0 flex-1 truncate">{f.name}</span>
              <span className="text-xs text-muted-foreground">{formatSize(f.size)}</span>
              <Button type="button" variant="ghost" size="icon" aria-label={`${f.name} entfernen`} onClick={() => onChange(files.filter((_, j) => j !== i))}>
                <XIcon />
              </Button>
            </li>
          ))}
        </ul>
      )}
      <FilePicker onPick={(picked) => onChange([...files, ...picked])} />
      <FileErrors errors={invalid.map((f) => `${f.name}: ${DOCUMENT_RULE}`)} />
    </div>
  )
}

function FilePicker({ onPick, disabled }: { onPick: (files: File[]) => void; disabled?: boolean }) {
  const input = useRef<HTMLInputElement>(null)
  return (
    <>
      <input
        ref={input}
        type="file"
        multiple
        accept={DOCUMENT_ACCEPT}
        className="hidden"
        aria-label="Dokument auswählen"
        onChange={(e) => {
          const files = Array.from(e.target.files ?? [])
          e.target.value = ''
          if (files.length) onPick(files)
        }}
      />
      <Button type="button" variant="outline" size="sm" disabled={disabled} onClick={() => input.current?.click()}>
        <PaperclipIcon />
        Dokument hinzufügen
      </Button>
      <p className="text-xs text-muted-foreground">{DOCUMENT_RULE}.</p>
    </>
  )
}

function FileErrors({ errors }: { errors: string[] }) {
  if (errors.length === 0) return null
  return (
    <ul className="text-sm text-destructive" role="alert">
      {errors.map((e) => (
        <li key={e}>{e}</li>
      ))}
    </ul>
  )
}
