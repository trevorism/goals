import { MetricDirectionType, MetricType } from './types.js'
import { formatDay } from './forms.js'

export const ProgressStatusType = {
  AHEAD: 'ahead',
  ON_TRACK: 'on_track',
  AT_RISK: 'at_risk',
  BEHIND: 'behind',
  NO_DATA: 'no_data',
  ALL: ['ahead', 'on_track', 'at_risk', 'behind', 'no_data']
}

export const statusDisplay = {
  ahead: { label: 'Ahead', icon: '▲', color: '#0ca30c' },
  on_track: { label: 'On track', icon: '✓', color: '#0ca30c' },
  at_risk: { label: 'At risk', icon: '!', color: '#fab219' },
  behind: { label: 'Behind', icon: '▼', color: '#d03b3b' },
  no_data: { label: 'No data', icon: '–', color: '#898781' }
}

export const chartColors = {
  values: '#2a78d6',
  trend: '#eb6834',
  plan: '#898781',
  ink: '#0b0b0b',
  secondaryInk: '#52514e',
  muted: '#898781',
  grid: '#e1e0d9',
  axis: '#c3c2b7',
  surface: '#ffffff',
  targetWash: 'rgba(137, 135, 129, 0.12)',
  meterFill: '#2a78d6',
  meterTrack: '#cde2fb'
}

const DAY_MILLIS = 86_400_000
const DAILY_ADHERENCE_DAYS = 28
const ADHERENCE_PERIODS = 4
const DASH = [6, 4]

export function formatPercent(value) {
  return value === null || value === undefined ? '–' : `${Math.round(value * 100)}%`
}

export function progressById(progress) {
  return {
    goals: Object.fromEntries((progress?.goals ?? []).map((goal) => [goal.goalId, goal])),
    metrics: Object.fromEntries((progress?.metrics ?? []).map((metric) => [metric.metricId, metric]))
  }
}

const time = (value) => new Date(value).getTime()

const utcDay = (value) => {
  const date = new Date(value)
  return Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate())
}

export function periodOf(value, frequency) {
  const day = new Date(utcDay(value))
  if (frequency === 'weekly') {
    const sinceMonday = (day.getUTCDay() + 6) % 7
    return day.getTime() - sinceMonday * DAY_MILLIS
  }
  if (frequency === 'monthly') {
    return Date.UTC(day.getUTCFullYear(), day.getUTCMonth(), 1)
  }
  return day.getTime()
}

function nextPeriod(period, frequency) {
  const date = new Date(period)
  if (frequency === 'weekly') return period + 7 * DAY_MILLIS
  if (frequency === 'monthly') return Date.UTC(date.getUTCFullYear(), date.getUTCMonth() + 1, 1)
  return period + DAY_MILLIS
}

function previousPeriods(period, count, frequency) {
  const date = new Date(period)
  if (frequency === 'weekly') return period - count * 7 * DAY_MILLIS
  if (frequency === 'monthly') return Date.UTC(date.getUTCFullYear(), date.getUTCMonth() - count, 1)
  return period - count * DAY_MILLIS
}

export function rollingAdherence(metric, goal, observations, now) {
  const frequency = metric.frequency || 'daily'
  const answers = new Map()
  const sorted = [...observations]
    .filter((observation) => observation.observedAt && time(observation.observedAt) <= time(now))
    .sort((a, b) => time(a.observedAt) - time(b.observedAt) || time(a.createdDate ?? 0) - time(b.createdDate ?? 0))
  sorted.forEach((observation) => answers.set(periodOf(observation.observedAt, frequency), !observation.missed && observation.value === 1))
  if (!answers.size) {
    return []
  }
  const windowLength = frequency === 'daily' ? DAILY_ADHERENCE_DAYS : ADHERENCE_PERIODS
  const firstAnswered = Math.min(...answers.keys())
  const trackingFloor = Math.max(firstAnswered, periodOf(goal.startDate, frequency))
  const current = periodOf(now, frequency)
  const series = []
  for (let period = firstAnswered; period <= current; period = nextPeriod(period, frequency)) {
    if (period === current && !answers.has(current)) {
      break
    }
    const windowStart = Math.max(previousPeriods(period, windowLength - 1, frequency), trackingFloor)
    let periods = 0
    let yes = 0
    for (let inWindow = windowStart; inWindow <= period; inWindow = nextPeriod(inWindow, frequency)) {
      periods++
      if (answers.get(inWindow)) yes++
    }
    series.push({ x: period, y: periods ? (yes / periods) * 100 : 0 })
  }
  return series
}

function baseOptions(goal, now, yScale, annotations, showLegend) {
  const start = time(goal.startDate)
  const end = time(goal.endDate)
  const today = time(now)
  const allAnnotations = { ...annotations }
  if (today >= start && today <= end) {
    allAnnotations.today = {
      type: 'line',
      xMin: today,
      xMax: today,
      borderColor: chartColors.axis,
      borderWidth: 1,
      label: { display: true, content: 'Today', position: 'start', color: chartColors.secondaryInk, backgroundColor: 'transparent', font: { size: 11 } }
    }
  }
  return {
    responsive: true,
    maintainAspectRatio: false,
    animation: false,
    interaction: { mode: 'nearest', intersect: false },
    scales: {
      x: {
        type: 'linear',
        min: start,
        max: end,
        grid: { color: chartColors.grid },
        border: { color: chartColors.axis },
        ticks: { color: chartColors.muted, maxTicksLimit: 6, callback: (value) => formatDay(value) }
      },
      y: { grid: { color: chartColors.grid }, border: { color: chartColors.axis }, ...yScale, ticks: { color: chartColors.muted, ...(yScale.ticks ?? {}) } }
    },
    plugins: {
      legend: { display: showLegend, position: 'bottom', labels: { color: chartColors.secondaryInk, usePointStyle: true, boxHeight: 8 } },
      tooltip: { callbacks: { title: (items) => (items.length ? formatDay(items[0].parsed.x) : '') } },
      annotation: { annotations: allAnnotations }
    }
  }
}

const valuePoints = (observations, now) =>
  observations
    .filter((observation) => !observation.missed && observation.value !== null && observation.value !== undefined && time(observation.observedAt) <= time(now))
    .map((observation) => ({ x: time(observation.observedAt), y: observation.value }))
    .sort((a, b) => a.x - b.x)

const pointStyle = (color) => ({
  backgroundColor: color,
  borderColor: chartColors.surface,
  borderWidth: 2,
  pointRadius: 5,
  pointHoverRadius: 7,
  pointHitRadius: 12,
  showLine: false
})

const lineStyle = (color) => ({ borderColor: color, backgroundColor: color, borderWidth: 2, pointRadius: 0, pointHitRadius: 8, showLine: true, borderCapStyle: 'round', borderJoinStyle: 'round' })

function planBaseline(metric, points) {
  if (metric.baseline !== null && metric.baseline !== undefined) return metric.baseline
  if (metric.type === MetricType.SCALE) return metric.direction === MetricDirectionType.DECREASE ? metric.scaleMax : metric.scaleMin
  return points.length ? points[0].y : null
}

function planTarget(metric) {
  if (metric.target !== null && metric.target !== undefined) return metric.target
  if (metric.type === MetricType.SCALE) return metric.direction === MetricDirectionType.DECREASE ? metric.scaleMin : metric.scaleMax
  return null
}

const withUnit = (metric, value) => (metric.unit ? `${formatNumber(value)} ${metric.unit}` : formatNumber(value))

export function formatNumber(value) {
  return Number.isInteger(value) ? String(value) : value.toFixed(1)
}

export function buildTrendChart(metric, goal, observations, metricProgress, now) {
  const points = valuePoints(observations, now)
  const start = time(goal.startDate)
  const end = time(goal.endDate)
  const datasets = [{ label: 'Values', data: points, ...pointStyle(chartColors.values), order: 0 }]
  const annotations = {}
  const target = planTarget(metric)

  if (metric.direction === MetricDirectionType.MAINTAIN) {
    if (target !== null && metric.tolerance !== null && metric.tolerance !== undefined) {
      annotations.tolerance = {
        type: 'box',
        yMin: target - metric.tolerance,
        yMax: target + metric.tolerance,
        backgroundColor: chartColors.targetWash,
        borderWidth: 0,
        label: { display: true, content: `Target ${withUnit(metric, target)} ± ${formatNumber(metric.tolerance)}`, position: { x: 'start', y: 'start' }, color: chartColors.secondaryInk, font: { size: 11 } }
      }
    }
  } else {
    const baseline = planBaseline(metric, points)
    if (baseline !== null && target !== null) {
      datasets.push({ label: 'Plan', data: [{ x: start, y: baseline }, { x: end, y: target }], ...lineStyle(chartColors.plan), borderDash: DASH, order: 2 })
      annotations.target = {
        type: 'line',
        yMin: target,
        yMax: target,
        borderColor: chartColors.axis,
        borderWidth: 1,
        label: {
          display: true,
          content: `Target ${withUnit(metric, target)}`,
          position: 'end',
          yAdjust: metric.direction === MetricDirectionType.DECREASE ? 10 : -10,
          color: chartColors.secondaryInk,
          backgroundColor: 'transparent',
          font: { size: 11 }
        }
      }
    }
  }

  const hasFit = metricProgress?.fitSlope !== null && metricProgress?.fitSlope !== undefined && points.length
  if (hasFit) {
    const fitAt = (x) => metricProgress.fitIntercept + (metricProgress.fitSlope * (x - start)) / DAY_MILLIS
    const firstX = points[0].x
    const lastX = points[points.length - 1].x
    const fitPoints = [firstX, lastX, end].filter((x, index, all) => index === 0 || x > all[index - 1]).map((x) => ({ x, y: fitAt(x) }))
    datasets.push({
      label: 'Trend',
      data: fitPoints,
      ...lineStyle(chartColors.trend),
      segment: { borderDash: (context) => (context.p0.parsed.x >= lastX ? DASH : undefined) },
      order: 1
    })
  }

  const yScale = metric.type === MetricType.SCALE
    ? { min: metric.scaleMin, max: metric.scaleMax, ticks: { stepSize: 1 } }
    : { grace: '10%', title: { display: !!metric.unit, text: metric.unit, color: chartColors.muted } }
  const options = baseOptions(goal, now, yScale, annotations, datasets.length > 1)
  options.plugins.tooltip.callbacks.label = (item) => `${item.dataset.label}: ${withUnit(metric, item.parsed.y)}`
  return { data: { datasets }, options }
}

export function buildAdherenceChart(metric, goal, observations, now) {
  const series = rollingAdherence(metric, goal, observations, now)
  const annotations = {}
  if (metric.target !== null && metric.target !== undefined) {
    annotations.target = {
      type: 'line',
      yMin: metric.target * 100,
      yMax: metric.target * 100,
      borderColor: chartColors.axis,
      borderWidth: 1,
      label: { display: true, content: `Target ${formatPercent(metric.target)}`, position: 'end', color: chartColors.secondaryInk, backgroundColor: 'transparent', font: { size: 11 } }
    }
  }
  const datasets = [{ label: 'Rolling adherence', data: series, ...lineStyle(chartColors.values), pointRadius: series.length === 1 ? 4 : 0 }]
  const options = baseOptions(goal, now, { min: 0, max: 100, ticks: { stepSize: 25, callback: (value) => `${value}%` } }, annotations, false)
  options.plugins.tooltip.callbacks.label = (item) => `Adherence: ${Math.round(item.parsed.y)}%`
  return { data: { datasets }, options }
}

export function buildChoiceChart(metric, goal, observations, now) {
  const labels = (metric.choices ?? []).map((choice) => choice.label)
  const datasets = [{ label: 'Answers', data: valuePoints(observations, now), ...pointStyle(chartColors.values) }]
  const options = baseOptions(goal, now, { min: 0, max: Math.max(labels.length - 1, 1), ticks: { stepSize: 1, callback: (value) => labels[value] ?? '' } }, {}, false)
  options.plugins.tooltip.callbacks.label = (item) => labels[item.parsed.y] ?? ''
  return { data: { datasets }, options }
}

export function buildMetricChart(metric, goal, observations, metricProgress, now) {
  switch (metric.type) {
    case MetricType.NUMERIC:
    case MetricType.SCALE:
      return buildTrendChart(metric, goal, observations, metricProgress, now)
    case MetricType.BOOLEAN:
      return buildAdherenceChart(metric, goal, observations, now)
    case MetricType.CHOICE:
      return buildChoiceChart(metric, goal, observations, now)
    default:
      return null
  }
}

export function describeScore(metric, score) {
  if (score === null || score === undefined) return ''
  if (metric.type === MetricType.BOOLEAN) return `${formatPercent(score)} of target`
  if (metric.type === MetricType.CHOICE) return `${formatPercent(score)} of best`
  if (metric.direction === MetricDirectionType.MAINTAIN) return `${formatPercent(score)} within tolerance`
  return `${formatPercent(score)} of the way to target`
}

export function describeMetricProgress(metric, metricProgress) {
  if (!metricProgress) {
    return []
  }
  const facts = []
  if (metric.type === MetricType.BOOLEAN) {
    if (metricProgress.adherence !== null) facts.push(`${formatPercent(metricProgress.adherence)} yes, last 4 ${metric.frequency === 'monthly' ? 'months' : 'weeks'}`)
    if (metricProgress.currentStreak) facts.push(`streak ${metricProgress.currentStreak} (best ${metricProgress.bestStreak})`)
    return facts
  }
  if (metricProgress.projectedEnd !== null && metricProgress.projectedEnd !== undefined) {
    facts.push(`on this trend, ${withUnit(metric, metricProgress.projectedEnd)} by the end`)
  }
  if (metricProgress.projectedTargetDate) {
    facts.push(`target reached around ${formatDay(metricProgress.projectedTargetDate)}`)
  }
  if (metricProgress.fitR2 !== null && metricProgress.fitR2 !== undefined) {
    facts.push(`fit r² ${metricProgress.fitR2.toFixed(2)} over ${metricProgress.fitCount} values`)
  }
  return facts
}
