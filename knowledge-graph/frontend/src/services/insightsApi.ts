import { api } from './http'
import type { InsightSummaryDto } from './types'

export const insightsApi = {
  getSummary(): Promise<InsightSummaryDto> {
    return api('/api/insights/summary')
  },
}
