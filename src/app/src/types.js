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
  ALL: ['increase', 'decrease', 'maintain']
}

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

export const MetricSourceType = {
  MANUAL: 'manual',
  PROMPT: 'prompt'
}

export const metricTypeLabels = {
  numeric: 'Number',
  boolean: 'Yes / No',
  scale: 'Scale',
  choice: 'Choice',
  text: 'Text'
}
