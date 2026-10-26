import { useState } from 'react'
import { CheckIcon, CopyIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'

export function RecoveryCodes({ codes }: { codes: string[] }) {
  const [copied, setCopied] = useState(false)

  async function copy() {
    await navigator.clipboard.writeText(codes.join('\n'))
    setCopied(true)
  }

  return (
    <div className="space-y-3">
      <ul className="grid grid-cols-2 gap-2 rounded-lg border bg-muted/40 p-4 font-mono text-sm" aria-label="Wiederherstellungscodes">
        {codes.map((code) => (
          <li key={code}>{code}</li>
        ))}
      </ul>
      <Button type="button" variant="outline" size="sm" onClick={copy}>
        {copied ? <CheckIcon /> : <CopyIcon />}
        {copied ? 'Kopiert' : 'Codes kopieren'}
      </Button>
    </div>
  )
}
