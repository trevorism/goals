import { MetricDirectionType, MetricType } from './types.js'

const pad = (number) => String(number).padStart(2, '0')

export function toDay(date) {
  if (!date) {
    return null
  }
  const value = date instanceof Date ? date : new Date(date)
  return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}`
}

export function fromDay(value) {
  if (value === null || value === undefined || value === '') {
    return null
  }
  const date = new Date(value)
  return new Date(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate())
}

export function formatDay(value) {
  if (value === null || value === undefined || value === '') {
    return ''
  }
  return new Date(value).toLocaleDateString(undefined, { timeZone: 'UTC', year: 'numeric', month: 'short', day: 'numeric' })
}

const blankToNull = (value) => (value === '' || value === undefined ? null : value)

const toNumber = (value) => {
  const cleaned = blankToNull(value)
  return cleaned === null ? null : Number(cleaned)
}

export function buildGoalPayload(form) {
  return {
    title: form.title?.trim(),
    description: blankToNull(form.description?.trim()),
    status: form.status,
    startDate: toDay(form.startDate),
    endDate: toDay(form.endDate),
    definitionOfDone: blankToNull(form.definitionOfDone?.trim())
  }
}

export function validateGoal(form, { requireDates }) {
  if (!form.title?.trim()) {
    return 'Enter a title'
  }
  if (requireDates && (!form.startDate || !form.endDate)) {
    return 'Choose a start and end date'
  }
  if (form.startDate && form.endDate && toDay(form.endDate) <= toDay(form.startDate)) {
    return 'The end date must be after the start date'
  }
  return ''
}

export function buildMetricPayload(form) {
  const payload = {
    name: form.name?.trim(),
    description: blankToNull(form.description?.trim()),
    type: form.type,
    measures: form.measures,
    frequency: form.frequency
  }
  if (form.type === MetricType.NUMERIC || form.type === MetricType.SCALE) {
    payload.direction = form.direction
    payload.baseline = toNumber(form.baseline)
    payload.target = toNumber(form.target)
  }
  if (form.type === MetricType.NUMERIC) {
    payload.unit = blankToNull(form.unit?.trim())
  }
  if (form.direction === MetricDirectionType.MAINTAIN && payload.direction) {
    payload.tolerance = toNumber(form.tolerance)
  }
  if (form.type === MetricType.SCALE) {
    payload.scaleMin = toNumber(form.scaleMin)
    payload.scaleMax = toNumber(form.scaleMax)
  }
  if (form.type === MetricType.BOOLEAN) {
    const percent = toNumber(form.targetPercent)
    payload.target = percent === null ? null : percent / 100
  }
  if (form.type === MetricType.CHOICE) {
    payload.choices = enteredChoices(form.choices).map((label) => ({ label }))
  }
  return payload
}

export function enteredChoices(choices) {
  return (choices || []).map((choice) => choice.trim()).filter((choice) => choice.length > 0)
}

export function validateMetric(form) {
  if (!form.name?.trim()) {
    return 'Enter a name'
  }
  if (form.type === MetricType.SCALE) {
    const min = toNumber(form.scaleMin)
    const max = toNumber(form.scaleMax)
    if (min === null || max === null || min >= max) {
      return 'The scale minimum must be below its maximum'
    }
  }
  if (form.type === MetricType.CHOICE && enteredChoices(form.choices).length < 2) {
    return 'Enter at least two choices'
  }
  if (form.type === MetricType.BOOLEAN) {
    const percent = toNumber(form.targetPercent)
    if (percent !== null && (percent <= 0 || percent > 100)) {
      return 'The target must be between 1% and 100%'
    }
  }
  if ((form.type === MetricType.NUMERIC || form.type === MetricType.SCALE) && form.direction === MetricDirectionType.MAINTAIN && toNumber(form.tolerance) === null) {
    return 'A metric to maintain needs a tolerance'
  }
  return ''
}

export function metricFormFrom(metric) {
  return {
    name: metric?.name ?? '',
    description: metric?.description ?? '',
    type: metric?.type ?? MetricType.NUMERIC,
    measures: metric?.measures ?? 'outcome',
    frequency: metric?.frequency ?? 'daily',
    direction: metric?.direction ?? MetricDirectionType.INCREASE,
    unit: metric?.unit ?? '',
    baseline: metric?.baseline ?? '',
    target: metric?.type === MetricType.BOOLEAN ? '' : metric?.target ?? '',
    targetPercent: metric?.type === MetricType.BOOLEAN && metric?.target != null ? Math.round(metric.target * 100) : '',
    tolerance: metric?.tolerance ?? '',
    scaleMin: metric?.scaleMin ?? 1,
    scaleMax: metric?.scaleMax ?? 5,
    choices: metric?.choices?.length ? metric.choices.map((choice) => choice.label) : ['', '']
  }
}

export function buildObservationPayload(metric, entry) {
  const payload = {
    observedAt: toDay(entry.observedAt) ?? toDay(new Date()),
    note: blankToNull(entry.note?.trim())
  }
  switch (metric.type) {
    case MetricType.NUMERIC:
    case MetricType.SCALE:
      payload.value = toNumber(entry.value)
      break
    case MetricType.BOOLEAN:
    case MetricType.CHOICE:
      payload.choice = entry.choice
      break
    case MetricType.TEXT:
      payload.label = entry.text?.trim()
      break
  }
  return payload
}

export function validateObservation(metric, entry) {
  switch (metric.type) {
    case MetricType.NUMERIC:
      return toNumber(entry.value) === null || Number.isNaN(toNumber(entry.value)) ? 'Enter a number' : ''
    case MetricType.SCALE: {
      const value = toNumber(entry.value)
      return value === null || value < metric.scaleMin || value > metric.scaleMax ? `Choose a value from ${metric.scaleMin} to ${metric.scaleMax}` : ''
    }
    case MetricType.BOOLEAN:
    case MetricType.CHOICE:
      return entry.choice ? '' : 'Choose an answer'
    case MetricType.TEXT:
      return entry.text?.trim() ? '' : 'Write an entry'
    default:
      return ''
  }
}

export function describeObservation(metric, observation) {
  if (observation.missed) {
    return 'Missed'
  }
  if (metric.type === MetricType.NUMERIC) {
    return metric.unit ? `${observation.value} ${metric.unit}` : `${observation.value}`
  }
  if (metric.type === MetricType.SCALE) {
    return `${observation.value} / ${metric.scaleMax}`
  }
  return observation.label ?? ''
}

export function describeTarget(metric) {
  if (metric.type === MetricType.BOOLEAN) {
    return metric.target != null ? `Yes ${Math.round(metric.target * 100)}% of the time` : ''
  }
  if (metric.target == null) {
    return ''
  }
  const unit = metric.unit ? ` ${metric.unit}` : ''
  const from = metric.baseline != null ? `${metric.baseline}${unit} → ` : ''
  return `${from}${metric.target}${unit}`
}

export function scalePoints(metric) {
  const points = []
  for (let point = metric.scaleMin; point <= metric.scaleMax; point++) {
    points.push(point)
  }
  return points
}

export function countNodes(node) {
  return 1 + (node?.children || []).reduce((total, child) => total + countNodes(child), 0)
}

export function findNode(node, id) {
  if (!node) {
    return null
  }
  if (node.goal.id === id) {
    return node
  }
  for (const child of node.children || []) {
    const found = findNode(child, id)
    if (found) {
      return found
    }
  }
  return null
}
