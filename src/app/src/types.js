export const GoalStatusType = {
  ACTIVE: 'active',
  COMPLETED: 'completed',
  MISSED: 'missed',
  ABANDONED: 'abandoned',
  ALL: ['active', 'completed', 'missed', 'abandoned']
}

export const MetricType = {
  NUMERIC: 'numeric',
  BOOLEAN: 'boolean',
  SCALE: 'scale',
  CHOICE: 'choice',
  TEXT: 'text',
  ALL: ['numeric', 'boolean', 'scale', 'choice', 'text']
}

export const MetricMeasuresType = {
  OUTCOME: 'outcome',
  EFFORT: 'effort',
  ALL: ['outcome', 'effort']
}

export const MetricDirectionType = {
  INCREASE: 'increase',
  DECREASE: 'decrease',
  MAINTAIN: 'maintain',
  AT_LEAST: 'at_least',
  AT_MOST: 'at_most',
  ALL: ['increase', 'decrease', 'maintain', 'at_least', 'at_most']
}

export const metricDirectionLabels = {
  increase: 'Increase toward the target',
  decrease: 'Decrease toward the target',
  maintain: 'Stay near the target',
  at_least: 'At least the target each time',
  at_most: 'At most the target each time'
}

export const isEachValueTarget = (metric) => metric.direction === 'at_least' || metric.direction === 'at_most'

export const timesPerWeekOf = (metric) => (metric.type === 'boolean' && (metric.frequency ?? 'daily') === 'daily' && metric.timesPerWeek > 0 ? metric.timesPerWeek : null)

export const FrequencyType = {
  DAILY: 'daily',
  WEEKLY: 'weekly',
  MONTHLY: 'monthly',
  ALL: ['daily', 'weekly', 'monthly']
}

export const AdjustmentCategoryType = {
  HABIT: 'habit',
  TOOL: 'tool',
  ENVIRONMENT: 'environment',
  PLAN: 'plan',
  OTHER: 'other',
  ALL: ['habit', 'tool', 'environment', 'plan', 'other']
}

export const metricTypeLabels = {
  numeric: 'Number',
  boolean: 'Yes / No',
  scale: 'Scale',
  choice: 'Choice',
  text: 'Text'
}
