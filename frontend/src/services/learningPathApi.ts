import { api } from './http'
import type { LearningPathState } from '@/features/learning/learningPathStore'

export const learningPathApi = {
  getCurrent(): Promise<LearningPathState | null> {
    return api('/api/learning-path')
  },

  save(path: LearningPathState): Promise<LearningPathState> {
    return api('/api/learning-path', {
      method: 'PUT',
      body: {
        libraryId: path.libraryId,
        goal: path.goal,
        difficulty: path.difficulty,
        steps: path.steps.map((step) => ({
          id: step.id,
          nodeId: step.nodeId,
          title: step.title,
          description: step.description,
          detail: step.detail,
          minutes: step.minutes,
          status: step.status,
        })),
      },
    })
  },

  markVisited(stepId: string): Promise<LearningPathState> {
    return api(`/api/learning-path/steps/${encodeURIComponent(stepId)}/visited`, { method: 'POST' })
  },

  complete(stepId: string): Promise<LearningPathState> {
    return api(`/api/learning-path/steps/${encodeURIComponent(stepId)}/complete`, {
      method: 'POST',
    })
  },

  recordAssessment(payload: {
    score: number
    correctCount: number
    totalCount: number
    weakTopics: string[]
  }): Promise<LearningPathState> {
    return api('/api/learning-path/assessment', { method: 'POST', body: payload })
  },
}
