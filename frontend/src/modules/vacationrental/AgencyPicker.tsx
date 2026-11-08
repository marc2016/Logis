import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { CheckIcon, ChevronsUpDownIcon, PlusIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from '@/components/ui/command'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { api } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { rentalKeys, useAgencies, type Agency, type AgencyRef } from './api'

interface Props {
  id: string
  value: AgencyRef | null
  onChange: (agency: AgencyRef | null) => void
  disabled?: boolean
  onError: (message: string) => void
}

/** Choose an active agency from the shared list, or create a new one from the typed name. */
export function AgencyPicker({ id, value, onChange, disabled, onError }: Props) {
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const { data: agencies = [] } = useAgencies()
  const queryClient = useQueryClient()

  const text = query.trim()
  const lower = text.toLowerCase()
  const active = agencies.filter((a) => !a.archived)
  const matches = active.filter(
    (a) => !lower || a.name.toLowerCase().includes(lower) || a.aliases.some((al) => al.alias.toLowerCase().includes(lower)),
  )
  const exists = agencies.some((a) => a.name.toLowerCase() === lower)

  const create = useMutation({
    mutationFn: (name: string) => api<Agency>('/api/vacationrental/agencies', { json: { name } }),
    onSuccess: async (created) => {
      await queryClient.invalidateQueries({ queryKey: rentalKeys.agencies })
      onChange({ id: created.id, name: created.name, archived: false })
      setQuery('')
      setOpen(false)
    },
    onError: (err) => onError(errorMessage(err)),
  })

  function choose(agency: AgencyRef | null) {
    onChange(agency)
    setQuery('')
    setOpen(false)
  }

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          id={id}
          type="button"
          variant="outline"
          role="combobox"
          aria-expanded={open}
          disabled={disabled}
          className="w-full justify-between font-normal"
        >
          <span className="truncate">
            {value ? value.name : <span className="text-muted-foreground">Kein Vermittler</span>}
            {value?.archived && <span className="text-muted-foreground"> (archiviert)</span>}
          </span>
          <ChevronsUpDownIcon className="opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-(--radix-popover-trigger-width) p-0" align="start">
        <Command shouldFilter={false}>
          <CommandInput placeholder="Vermittler suchen oder anlegen" value={query} onValueChange={setQuery} />
          <CommandList>
            <CommandEmpty>Kein Vermittler gefunden</CommandEmpty>
            <CommandGroup>
              {!text && (
                <CommandItem value="none" onSelect={() => choose(null)}>
                  <CheckIcon className={value === null ? 'opacity-100' : 'opacity-0'} />
                  Kein Vermittler
                </CommandItem>
              )}
              {matches.map((a) => (
                <CommandItem key={a.id} value={String(a.id)} onSelect={() => choose({ id: a.id, name: a.name, archived: false })}>
                  <CheckIcon className={value?.id === a.id ? 'opacity-100' : 'opacity-0'} />
                  {a.name}
                </CommandItem>
              ))}
              {text && !exists && (
                <CommandItem value={`create:${text}`} onSelect={() => create.mutate(text)} disabled={create.isPending}>
                  <PlusIcon />„{text}“ anlegen
                </CommandItem>
              )}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  )
}
