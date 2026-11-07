import { Link, useLocation, useNavigate } from 'react-router'
import { useQueryClient } from '@tanstack/react-query'
import {
  ArrowLeftRightIcon,
  BuildingIcon,
  ChevronsUpDownIcon,
  HouseIcon,
  LandmarkIcon,
  InboxIcon, LayoutDashboardIcon,
  LogOutIcon,
  TagsIcon,
  UserCogIcon,
  UsersIcon,
  WalletIcon,
  type LucideIcon,
} from 'lucide-react'
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarRail,
} from '@/components/ui/sidebar'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { api } from '@/lib/api'
import { useCurrentUser } from '@/core/auth/session'
import type { LogisModule } from '../types'

interface NavItem {
  label: string
  to: string
  icon?: LucideIcon
}

function isActivePath(pathname: string, to: string) {
  return to === '/' ? pathname === '/' : pathname === to || pathname.startsWith(`${to}/`)
}

function NavGroup({ label, items }: { label: string; items: NavItem[] }) {
  const { pathname } = useLocation()
  return (
    <SidebarGroup>
      <SidebarGroupLabel>{label}</SidebarGroupLabel>
      <SidebarGroupContent>
        <SidebarMenu>
          {items.map((item) => {
            const Icon = item.icon
            return (
              <SidebarMenuItem key={item.to}>
                <SidebarMenuButton asChild isActive={isActivePath(pathname, item.to)} tooltip={item.label}>
                  <Link to={item.to}>
                    {Icon && <Icon />}
                    <span>{item.label}</span>
                  </Link>
                </SidebarMenuButton>
              </SidebarMenuItem>
            )
          })}
        </SidebarMenu>
      </SidebarGroupContent>
    </SidebarGroup>
  )
}

function UserMenu() {
  const user = useCurrentUser()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  async function logout() {
    try {
      await api('/api/auth/logout', { method: 'POST' })
    } finally {
      queryClient.clear()
      navigate('/login', { replace: true })
    }
  }

  return (
    <SidebarMenu>
      <SidebarMenuItem>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <SidebarMenuButton size="lg" tooltip={user.displayName}>
              <div className="flex size-8 shrink-0 items-center justify-center rounded-md bg-sidebar-primary text-sm font-medium text-sidebar-primary-foreground">
                {user.displayName.slice(0, 1).toUpperCase()}
              </div>
              <div className="grid flex-1 text-left text-sm leading-tight">
                <span className="truncate font-medium">{user.displayName}</span>
                <span className="truncate text-xs text-muted-foreground">{user.username}</span>
              </div>
              <ChevronsUpDownIcon className="ml-auto" />
            </SidebarMenuButton>
          </DropdownMenuTrigger>
          <DropdownMenuContent side="top" align="start" className="w-56">
            <DropdownMenuLabel>{user.displayName}</DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem asChild>
              <Link to="/konto">
                <UserCogIcon />
                Mein Konto
              </Link>
            </DropdownMenuItem>
            <DropdownMenuItem onSelect={logout}>
              <LogOutIcon />
              Abmelden
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </SidebarMenuItem>
    </SidebarMenu>
  )
}

export function AppSidebar({ modules }: { modules: LogisModule[] }) {
  const user = useCurrentUser()

  return (
    <Sidebar collapsible="icon">
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton size="lg" asChild tooltip="Logis">
              <Link to="/">
                <div className="flex size-8 shrink-0 items-center justify-center rounded-md bg-sidebar-primary text-sidebar-primary-foreground">
                  <LandmarkIcon className="size-4" />
                </div>
                <span className="text-base font-semibold">Logis</span>
              </Link>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>
      <SidebarContent>
        <NavGroup
          label="Allgemein"
          items={[
            { label: 'Übersicht', to: '/', icon: LayoutDashboardIcon },
            { label: 'Posteingang', to: '/posteingang', icon: InboxIcon },
          ]}
        />
        <NavGroup
          label="Finanzen"
          items={[
            { label: 'Konten', to: '/finanzen/konten', icon: WalletIcon },
            { label: 'Transaktionen', to: '/finanzen/transaktionen', icon: ArrowLeftRightIcon },
            { label: 'Eigentümer', to: '/finanzen/eigentuemer', icon: BuildingIcon },
          ]}
        />
        <NavGroup label="Immobilien" items={[{ label: 'Objekte', to: '/immobilien', icon: HouseIcon }]} />
        {modules.map((module) => (
          <NavGroup
            key={module.id}
            label={module.label}
            items={module.navItems.map((item) => ({
              label: item.label,
              to: `/${module.basePath}/${item.path}`,
              icon: item.icon ?? module.icon,
            }))}
          />
        ))}
        {user.role === 'ADMIN' && (
          <NavGroup
            label="Einstellungen"
            items={[
              { label: 'Benutzer', to: '/einstellungen/benutzer', icon: UsersIcon },
              { label: 'Kategorien', to: '/einstellungen/kategorien', icon: TagsIcon },
            ]}
          />
        )}
      </SidebarContent>
      <SidebarFooter>
        <UserMenu />
      </SidebarFooter>
      <SidebarRail />
    </Sidebar>
  )
}
