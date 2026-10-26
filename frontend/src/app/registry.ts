import type { LogisModule } from './types'
import { vacationRentalModule } from '@/modules/vacationrental'

/** Alle Frontend-Module. Angezeigt werden nur die, die das Backend als aktiv meldet. */
export const moduleRegistry: LogisModule[] = [vacationRentalModule]
