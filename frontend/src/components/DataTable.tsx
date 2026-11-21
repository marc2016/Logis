import { useEffect, useState } from 'react'
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
import { usePreference, usePreferences } from '@/core/preferences/usePreference'
import { Pagination } from '@/core/ledger/transactions/Pagination'
import { cn } from '@/lib/utils'
import { PageCheckbox, RowCheckbox } from './selection'
import type { Selection } from './useSelection'

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
  /** Row selection for bulk actions; rows for which `selectable` is false get no checkbox. */
  selection?: Selection
  selectable?: (row: T) => boolean
}

/** Where the hidden columns were remembered per browser before they became a user setting. */
function legacyKey(tableId: string) {
  return `logis.table.${tableId}.hidden`
}

/** The hidden columns stored in this browser before, or null. */
function readLegacy(tableId: string): string[] | null {
  try {
    const raw = window.localStorage.getItem(legacyKey(tableId))
    const hidden: unknown = raw ? JSON.parse(raw) : null
    return Array.isArray(hidden) ? hidden.map(String) : null
  } catch {
    return null
  }
}

function removeLegacy(tableId: string) {
  try {
    window.localStorage.removeItem(legacyKey(tableId))
  } catch {
    // Storage unavailable: nothing to remove.
  }
}

/**
 * Table on TanStack Table with the shadcn table: sorting and paging happen on the server, the hidden columns are a
 * setting of the user (spec user-preferences, „Remembered list columns“).
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
  selection,
  selectable,
}: Props<T>) {
  const key = `core.tableColumns.${tableId}`
  // Until the settings have loaded, a choice from this browser avoids columns jumping (design D3).
  const [legacy] = useState(() => readLegacy(tableId))
  const [hidden, setHidden, loaded] = usePreference<string[]>(key, legacy ?? [])
  const { data: preferences } = usePreferences()
  useEffect(() => {
    if (!loaded || legacy === null) return
    // Taken over once, unless the user already has the setting from another device; then the browser forgets it.
    if (!(preferences && key in preferences)) setHidden(legacy)
    removeLegacy(tableId)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loaded])
  const columnVisibility: VisibilityState = Object.fromEntries(hidden.map((id) => [id, false]))
  const table = useReactTable({
    data,
    columns,
    getRowId,
    getCoreRowModel: getCoreRowModel(),
    manualSorting: true,
    manualPagination: true,
    state: { columnVisibility },
    onColumnVisibilityChange: (updater) => {
      const next = typeof updater === 'function' ? updater(columnVisibility) : updater
      setHidden(Object.entries(next).filter(([, visible]) => !visible).map(([id]) => id))
    },
  })
  const selectableIds = selection ? data.filter((row) => !selectable || selectable(row)).map(getRowId) : []
  const withSelection = selection !== undefined && selectableIds.length > 0
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
                {withSelection && (
                  <TableHead className="w-10">
                    <PageCheckbox ids={selectableIds} selection={selection} />
                  </TableHead>
                )}
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
                <TableCell colSpan={table.getVisibleLeafColumns().length + (withSelection ? 1 : 0)} className="py-8 text-center text-muted-foreground">
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
                  data-state={selection?.isSelected(row.id) ? 'selected' : undefined}
                >
                  {withSelection && (
                    <TableCell className="w-10">
                      {selectableIds.includes(row.id) && (
                        <RowCheckbox id={row.id} label={rowLabel?.(row.original) ?? 'Zeile'} selection={selection} order={selectableIds} />
                      )}
                    </TableCell>
                  )}
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
