import type { LogisModule } from './types'
import { vacationRentalModule } from '@/modules/vacationrental'

/** All frontend modules. Only those the backend reports as active are shown. */
export const moduleRegistry: LogisModule[] = [vacationRentalModule]
