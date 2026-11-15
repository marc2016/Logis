import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createColumnHelper } from '@tanstack/react-table'
import { DataTable } from './DataTable'
import { BulkActionBar } from './BulkActionBar'
import { useSelection } from './useSelection'

interface Row {
  id: number
  name: string
  city: string
}

const column = createColumnHelper<Row>()
const columns = [
  column.accessor('name', { header: 'Name', meta: { label: 'Name', sortable: true } }),
  column.accessor('city', { header: 'Ort', meta: { label: 'Ort' } }),
]
const rows: Row[] = [{ id: 1, name: 'Anna', city: 'Nordhorn' }]

describe('DataTable', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('reports sorting to the caller and toggles the direction', async () => {
    const onSortChange = vi.fn()
    const user = userEvent.setup()
    const { rerender } = render(
      <DataTable tableId="t1" columns={columns} data={rows} getRowId={(r) => String(r.id)} sort={null} onSortChange={onSortChange} />,
    )
    await user.click(screen.getByRole('button', { name: /Name/ }))
    expect(onSortChange).toHaveBeenLastCalledWith({ id: 'name', desc: false })
    rerender(<DataTable tableId="t1" columns={columns} data={rows} getRowId={(r) => String(r.id)} sort={{ id: 'name', desc: false }} onSortChange={onSortChange} />)
    await user.click(screen.getByRole('button', { name: /Name/ }))
    expect(onSortChange).toHaveBeenLastCalledWith({ id: 'name', desc: true })
    expect(screen.queryByRole('button', { name: /Ort/ })).not.toBeInTheDocument()
  })

  it('hides a column and remembers it', async () => {
    const user = userEvent.setup()
    const { unmount } = render(<DataTable tableId="t2" columns={columns} data={rows} getRowId={(r) => String(r.id)} />)
    await user.click(screen.getByRole('button', { name: 'Spalten' }))
    await user.click(await screen.findByRole('menuitemcheckbox', { name: 'Ort' }))
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('columnheader', { name: 'Ort' })).not.toBeInTheDocument()
    unmount()
    render(<DataTable tableId="t2" columns={columns} data={rows} getRowId={(r) => String(r.id)} />)
    expect(screen.queryByRole('columnheader', { name: 'Ort' })).not.toBeInTheDocument()
    expect(within(screen.getByRole('table')).getByText('Anna')).toBeInTheDocument()
  })

  it('renders when storage is unavailable', () => {
    vi.stubGlobal('localStorage', {
      getItem: () => {
        throw new Error('blocked')
      },
      setItem: () => {
        throw new Error('blocked')
      },
    })
    render(<DataTable tableId="t3" columns={columns} data={rows} getRowId={(r) => String(r.id)} />)
    expect(screen.getByRole('columnheader', { name: 'Ort' })).toBeInTheDocument()
    expect(screen.getByText('Nordhorn')).toBeInTheDocument()
  })

  describe('selection', () => {
    const many: Row[] = Array.from({ length: 8 }, (_, i) => ({ id: i + 1, name: `Gast ${i + 1}`, city: i === 2 ? 'gesperrt' : 'Nordhorn' }))

    function Selectable({ onRowClick }: { onRowClick?: (row: Row) => void }) {
      const selection = useSelection('k')
      return (
        <>
          <DataTable
            tableId="sel"
            columns={columns}
            data={many}
            getRowId={(r) => String(r.id)}
            rowLabel={(r) => r.name}
            onRowClick={onRowClick}
            selection={selection}
            selectable={(r) => r.city !== 'gesperrt'}
          />
          <BulkActionBar selection={selection}>
            <button type="button">Löschen</button>
          </BulkActionBar>
        </>
      )
    }

    it('selects a page, skips rows that cannot be changed and shows the action bar', async () => {
      const user = userEvent.setup()
      render(<Selectable />)
      expect(screen.queryByRole('toolbar')).not.toBeInTheDocument()
      expect(screen.queryByRole('checkbox', { name: 'Gast 3 auswählen' })).not.toBeInTheDocument()
      await user.click(screen.getByRole('checkbox', { name: 'Alle auf dieser Seite auswählen' }))
      expect(within(screen.getByRole('toolbar')).getByText('7 ausgewählt')).toBeInTheDocument()
      await user.click(screen.getByRole('button', { name: 'Auswahl aufheben' }))
      expect(screen.queryByRole('toolbar')).not.toBeInTheDocument()
    })

    it('selects a range with Shift without opening the row', async () => {
      const onRowClick = vi.fn()
      const user = userEvent.setup()
      render(<Selectable onRowClick={onRowClick} />)
      await user.click(screen.getByRole('checkbox', { name: 'Gast 2 auswählen' }))
      await user.keyboard('{Shift>}')
      await user.click(screen.getByRole('checkbox', { name: 'Gast 6 auswählen' }))
      await user.keyboard('{/Shift}')
      // Gast 3 cannot be selected, so 2, 4, 5 and 6 are.
      expect(screen.getByText('4 ausgewählt')).toBeInTheDocument()
      expect(screen.getByRole('checkbox', { name: 'Alle auf dieser Seite auswählen' })).toHaveAttribute('data-state', 'indeterminate')
      expect(onRowClick).not.toHaveBeenCalled()
    })
  })
})
