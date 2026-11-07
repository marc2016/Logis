import { useState } from 'react'
import { flexRender, getCoreRowModel, useReactTable, type ColumnDef, type VisibilityState } from '@tanstack/react-table'
import { ArrowDownIcon, ArrowUpDownIcon, ArrowUpIcon, Columns3Icon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuLabel,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Pagination } from '@/core/ledger/transactions/Pagination'
import { cn } from '@/lib/utils'

/** Server-side sort state: column id and direction, e.g. `{ id: 'arrival', desc: true }`. */
export interface SortState {
  id: string
  desc: boolean
}

/** Column metadata: a German label for the visibility menu and whether the server can sort by it. */
export interface DataTableColumnMeta {
  label: string
  sortable?: boolean
  className?: string
}

interface Props<T> {
  /** Stable key for remembering hidden columns in this browser. */
  tableId: string
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  columns: ColumnDef<T, any>[]
  data: T[]
  getRowId: (row: T) => string
  sort?: SortState | null
  onSortChange?: (sort: SortState) => void
  page?: number
  pageSize?: number
  total?: number
  onPageChange?: (page: number) => void
  onRowClick?: (row: T) => void
  rowLabel?: (row: T) => string
  rowClassName?: (row: T) => string | undefined
  isLoading?: boolean
  emptyText?: string
}

function storageKey(tableId: string) {
  return `logis.table.${tableId}.hidden`
}

function loadHidden(tableId: string): VisibilityState {
  try {
    const raw = window.localStorage.getItem(storageKey(tableId))
    const hidden: unknown = raw ? JSON.parse(raw) : []
    return Array.isArray(hidden) ? Object.fromEntries(hidden.map((id) => [String(id), false])) : {}
  } catch {
    return {}
  }
}

function saveHidden(tableId: string, visibility: VisibilityState) {
  try {
    const hidden = Object.entries(visibility).filter(([, visible]) => !visible).map(([id]) => id)
    window.localStorage.setItem(storageKey(tableId), JSON.stringify(hidden))
  } catch {
    // Storage unavailable (private mode, blocked): the choice just isn't remembered.
  }
}

/**
 * Table on TanStack Table with the shadcn table: sorting and paging happen on the server, column visibility
 * is remembered per browser.
 */
export function DataTable<T>({
  tableId,
  columns,
  data,
  getRowId,
  sort,
  onSortChange,
  page,
  pageSize,
  total,
  onPageChange,
  onRowClick,
  rowLabel,
  rowClassName,
  isLoading,
  emptyText = 'Keine Einträge.',
}: Props<T>) {
  const [columnVisibility, setColumnVisibility] = useState<VisibilityState>(() => loadHidden(tableId))
  const table = useReactTable({
    data,
    columns,
    getRowId,
    getCoreRowModel: getCoreRowModel(),
    manualSorting: true,
    manualPagination: true,
    state: { columnVisibility },
    onColumnVisibilityChange: (updater) => {
      setColumnVisibility((old) => {
        const next = typeof updater === 'function' ? updater(old) : updater
        saveHidden(tableId, next)
        return next
      })
    },
  })
  const hideable = table.getAllLeafColumns().filter((c) => c.getCanHide() && (c.columnDef.meta as DataTableColumnMeta | undefined)?.label)

  return (
    <div>
      {hideable.length > 0 && (
        <div className="mb-2 flex justify-end">
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="outline" size="sm">
                <Columns3Icon />
                Spalten
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuLabel>Spalten anzeigen</DropdownMenuLabel>
              {hideable.map((column) => (
                <DropdownMenuCheckboxItem
                  key={column.id}
                  checked={column.getIsVisible()}
                  onCheckedChange={(checked) => column.toggleVisibility(Boolean(checked))}
                  onSelect={(e) => e.preventDefault()}
                >
                  {(column.columnDef.meta as DataTableColumnMeta).label}
                </DropdownMenuCheckboxItem>
              ))}
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      )}
      <div className="rounded-lg border">
        <Table>
          <TableHeader>
            {table.getHeaderGroups().map((group) => (
              <TableRow key={group.id}>
                {group.headers.map((header) => {
                  const meta = header.column.columnDef.meta as DataTableColumnMeta | undefined
                  const active = sort?.id === header.column.id
                  const label = flexRender(header.column.columnDef.header, header.getContext())
                  return (
                    <TableHead
                      key={header.id}
                      className={meta?.className}
                      aria-sort={active ? (sort.desc ? 'descending' : 'ascending') : undefined}
                    >
                      {meta?.sortable && onSortChange ? (
                        <button
                          type="button"
                          className="inline-flex items-center gap-1 hover:text-foreground"
                          onClick={() => onSortChange({ id: header.column.id, desc: active ? !sort.desc : false })}
                        >
                          {label}
                          {active ? (
                            sort.desc ? <ArrowDownIcon className="size-3.5" /> : <ArrowUpIcon className="size-3.5" />
                          ) : (
                            <ArrowUpDownIcon className="size-3.5 opacity-40" />
                          )}
                        </button>
                      ) : (
                        label
                      )}
                    </TableHead>
                  )
                })}
              </TableRow>
            ))}
          </TableHeader>
          <TableBody>
            {table.getRowModel().rows.length === 0 ? (
              <TableRow>
                <TableCell colSpan={table.getVisibleLeafColumns().length} className="py-8 text-center text-muted-foreground">
                  {isLoading ? 'Wird geladen …' : emptyText}
                </TableCell>
              </TableRow>
            ) : (
              table.getRowModel().rows.map((row) => (
                <TableRow
                  key={row.id}
                  className={cn(onRowClick && 'cursor-pointer', rowClassName?.(row.original))}
                  onClick={onRowClick ? () => onRowClick(row.original) : undefined}
                  onKeyDown={
                    onRowClick
                      ? (e) => {
                          if (e.key === 'Enter') onRowClick(row.original)
                        }
                      : undefined
                  }
                  tabIndex={onRowClick ? 0 : undefined}
                  aria-label={rowLabel?.(row.original)}
                >
                  {row.getVisibleCells().map((cell) => (
                    <TableCell key={cell.id} className={(cell.column.columnDef.meta as DataTableColumnMeta | undefined)?.className}>
                      {flexRender(cell.column.columnDef.cell, cell.getContext())}
                    </TableCell>
                  ))}
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>
      {onPageChange && pageSize !== undefined && total !== undefined && (
        <Pagination page={page ?? 0} size={pageSize} total={total} onChange={onPageChange} />
      )}
    </div>
  )
}
