import { describe, it, expect } from 'vitest'
import {
  buildAdherenceChart,
  buildChoiceChart,
  buildMetricChart,
  buildTrendChart,
  describeMetricProgress,
  describeScore,
  describeSegments,
  describeHeadline,
  adjustmentMarkers,
  formatPercent,
  periodOf,
  progressById,
  rollingAdherence,
  weeklyYesCounts
} from '../src/progress.js'

const DAY = 86_400_000
const start = Date.UTC(2027, 0, 1)
const day = (offset) => new Date(start + offset * DAY).toISOString()
const goal = { startDate: day(0), endDate: day(100) }
const now = new Date(start + 50 * DAY)

const dataset = (chart, label) => chart.data.datasets.find((set) => set.label === label)

describe('formatting and lookup', () => {
  it('formats fractions as whole percentages', () => {
    expect(formatPercent(0.856)).toBe('86%')
    expect(formatPercent(null)).toBe('–')
  })

  it('indexes progress by goal and metric id', () => {
    const maps = progressById({ goals: [{ goalId: 'g' }], metrics: [{ metricId: 'm' }] })
    expect(maps.goals.g).toEqual({ goalId: 'g' })
    expect(maps.metrics.m).toEqual({ metricId: 'm' })
    expect(progressById(null)).toEqual({ goals: {}, metrics: {}, segments: {} })
  })

  it('maps a day to its period start', () => {
    expect(periodOf('2027-01-06T15:00:00Z', 'weekly')).toBe(Date.UTC(2027, 0, 4))
    expect(periodOf('2027-01-06T15:00:00Z', 'monthly')).toBe(Date.UTC(2027, 0, 1))
    expect(periodOf('2027-01-06T15:00:00Z', 'daily')).toBe(Date.UTC(2027, 0, 6))
  })
})

describe('trend chart', () => {
  const weight = { type: 'numeric', unit: 'lb', direction: 'decrease', baseline: 200, target: 180 }
  const observations = [{ observedAt: day(0), value: 200 }, { observedAt: day(25), value: 195 }, { observedAt: day(50), value: 190 }, { observedAt: day(90), value: 1 }]
  const progress = { fitSlope: -0.2, fitIntercept: 200 }

  it('fixes the x axis to the goal dates and marks today', () => {
    const chart = buildTrendChart(weight, goal, observations, progress, now)

    expect(chart.options.scales.x.min).toBe(start)
    expect(chart.options.scales.x.max).toBe(start + 100 * DAY)
    expect(chart.options.plugins.annotation.annotations.today.xMin).toBe(now.getTime())
  })

  it('plots past values, the plan from baseline to target, and the target line', () => {
    const chart = buildTrendChart(weight, goal, observations, progress, now)

    expect(dataset(chart, 'Values').data.map((point) => point.y)).toEqual([200, 195, 190])
    expect(dataset(chart, 'Plan').data).toEqual([{ x: start, y: 200 }, { x: start + 100 * DAY, y: 180 }])
    expect(dataset(chart, 'Plan').borderDash).toEqual([6, 4])
    expect(chart.options.plugins.annotation.annotations.target.yMin).toBe(180)
    expect(chart.options.plugins.annotation.annotations.target.label.content).toBe('Target 180 lb')
    expect(chart.options.plugins.legend.display).toBe(true)
  })

  it('draws the fit solid over the data and dashed to the end', () => {
    const chart = buildTrendChart(weight, goal, observations, progress, now)
    const trend = dataset(chart, 'Trend')

    expect(trend.data).toEqual([{ x: start, y: 200 }, { x: start + 50 * DAY, y: 190 }, { x: start + 100 * DAY, y: 180 }])
    expect(trend.segment.borderDash({ p0: { parsed: { x: start } } })).toBeUndefined()
    expect(trend.segment.borderDash({ p0: { parsed: { x: start + 50 * DAY } } })).toEqual([6, 4])
  })

  it('leaves out the fit when there is none and the plan when there is no target', () => {
    const chart = buildTrendChart({ type: 'numeric' }, goal, observations, null, now)

    expect(chart.data.datasets.map((set) => set.label)).toEqual(['Values'])
    expect(chart.options.plugins.legend.display).toBe(false)
  })

  it('shades the tolerance band for a metric to maintain', () => {
    const chart = buildTrendChart({ type: 'numeric', direction: 'maintain', target: 180, tolerance: 2 }, goal, observations, null, now)
    const band = chart.options.plugins.annotation.annotations.tolerance

    expect([band.yMin, band.yMax]).toEqual([178, 182])
    expect(dataset(chart, 'Plan')).toBeUndefined()
  })

  it('pins a scale axis to its range and plans across it by default', () => {
    const chart = buildTrendChart({ type: 'scale', direction: 'increase', scaleMin: 1, scaleMax: 5 }, goal, [{ observedAt: day(10), value: 3 }], null, now)

    expect([chart.options.scales.y.min, chart.options.scales.y.max]).toEqual([1, 5])
    expect(dataset(chart, 'Plan').data.map((point) => point.y)).toEqual([1, 5])
  })
})

describe('adherence', () => {
  const walked = { type: 'boolean', frequency: 'daily', target: 0.8 }
  const answers = (byDay) => Object.entries(byDay).map(([offset, yes]) => ({ observedAt: day(Number(offset)), value: yes ? 1 : 0 }))

  it('rolls adherence day by day over recorded days only', () => {
    const series = rollingAdherence(walked, goal, answers({ 46: true, 47: false, 49: true }), now)

    expect(series.map((point) => Math.round(point.y))).toEqual([100, 50, 50, 67])
    expect(series[series.length - 1].x).toBe(start + 49 * DAY)
  })

  it('includes today once it is answered and ignores future answers', () => {
    const series = rollingAdherence(walked, goal, answers({ 49: true, 50: false, 60: true }), now)

    expect(series.map((point) => point.y)).toEqual([100, 50])
  })

  it('charts the rolling line against the target', () => {
    const chart = buildAdherenceChart(walked, goal, answers({ 48: true, 49: true }), now)

    expect(chart.data.datasets[0].data).toHaveLength(2)
    expect(chart.options.plugins.annotation.annotations.target.yMin).toBe(80)
    expect([chart.options.scales.y.min, chart.options.scales.y.max]).toEqual([0, 100])
  })
})

describe('choice chart and dispatch', () => {
  const meals = { type: 'choice', choices: [{ value: 'poor', label: 'Poor' }, { value: 'ok', label: 'OK' }, { value: 'good', label: 'Good' }] }

  it('labels the y axis with the choices, worst to best', () => {
    const chart = buildChoiceChart(meals, goal, [{ observedAt: day(10), value: 2 }], now)

    expect(chart.options.scales.y.ticks.callback(2)).toBe('Good')
    expect(chart.options.scales.y.max).toBe(2)
  })

  it('picks the chart by metric type and draws none for text', () => {
    expect(buildMetricChart({ type: 'text' }, goal, [], null, now)).toBeNull()
    expect(buildMetricChart(meals, goal, [], null, now).data.datasets[0].label).toBe('Answers')
  })
})

describe('per-period targets', () => {
  const running = { name: 'Running', type: 'boolean', frequency: 'daily', timesPerWeek: 3 }
  const monday = Date.UTC(2027, 1, 15)
  const on = (offset, yes) => ({ observedAt: new Date(monday + offset * DAY).toISOString(), value: yes ? 1 : 0 })

  it('counts yes days per week for the weekly chart', () => {
    const observations = [on(-7, true), on(-5, true), on(-4, false), on(0, true), on(10, true)]

    expect(weeklyYesCounts(observations, new Date(monday + 3 * DAY))).toEqual([{ x: monday - 7 * DAY, y: 2 }, { x: monday, y: 1 }])
  })

  it('charts weekly yes counts against the times per week', () => {
    const chart = buildMetricChart(running, goal, [on(-7, true)], null, new Date(monday + 3 * DAY))

    expect(chart.data.datasets[0].label).toBe('Yes days')
    expect(chart.options.plugins.annotation.annotations.target.yMin).toBe(3)
    expect(chart.options.plugins.annotation.annotations.target.label.content).toBe('3× per week')
    expect([chart.options.scales.y.min, chart.options.scales.y.max]).toEqual([0, 7])
  })

  it('describes weeks met, this week and the weekly streak', () => {
    const progress = { score: 0.75, adherence: 0.75, answeredPeriods: 4, windowPeriods: 4, currentPeriodCount: 2, currentStreak: 2, bestStreak: 3 }

    expect(describeScore(running, 0.75)).toBe('75% of weeks met')
    expect(describeMetricProgress(running, progress)).toEqual(['3 of 4 weeks met', 'this week 2 of 3', 'streak 2 weeks (best 3)'])
    expect(describeHeadline({ goal: { endDate: day(100) }, progress: {}, headlineMetric: running, headlineMetricProgress: progress })).toBe('Running: 3 of 4 weeks met (3× per week)')
  })

  it('draws only a target line for at least and at most metrics', () => {
    const meals = { type: 'numeric', direction: 'at_least', target: 4, baseline: 0, unit: 'meals' }
    const chart = buildTrendChart(meals, goal, [{ observedAt: day(10), value: 5 }], null, now)

    expect(chart.data.datasets.map((dataset) => dataset.label)).toEqual(['Values'])
    expect(chart.options.plugins.annotation.annotations.target.yMin).toBe(4)
    expect(describeScore(meals, 0.5)).toBe('50% of recent values at target')
  })
})

describe('progress facts', () => {
  it('describes a trend projection', () => {
    const facts = describeMetricProgress({ type: 'numeric', unit: 'lb' }, { projectedEnd: 181.25, projectedTargetDate: day(110), fitR2: 0.91, fitCount: 12 })

    expect(facts[0]).toBe('on this trend, 181.3 lb by the end')
    expect(facts[1]).toContain('target reached around')
    expect(facts[2]).toBe('fit r² 0.91 over 12 values')
  })

  it('describes the required and recent rates, and drops the projection when the trend has stalled', () => {
    const weight = { type: 'numeric', unit: 'lb' }

    expect(describeMetricProgress(weight, { requiredRate: -1.3, recentRate: -0.04, projectedEnd: 207.1, stalled: true }))
      .toEqual(['needs −1.3 lb/wk', 'recent trend 0.0 lb/wk'])
    expect(describeMetricProgress(weight, { requiredRate: -1.3, recentRate: -1.5, projectedEnd: 199, stalled: false }))
      .toEqual(['needs −1.3 lb/wk', 'recent trend −1.5 lb/wk', 'on this trend, 199 lb by the end'])
  })

  it('describes adherence and streaks', () => {
    expect(describeMetricProgress({ type: 'boolean', frequency: 'daily' }, { adherence: 0.75, answeredPeriods: 20, windowPeriods: 28, currentStreak: 3, bestStreak: 5 }))
      .toEqual(['75% yes on 20 of 28 days recorded', 'streak 3 (best 5)'])
    expect(describeMetricProgress({ type: 'boolean', frequency: 'weekly' }, { adherence: 1, answeredPeriods: 1, windowPeriods: 4 }))
      .toEqual(['100% yes on 1 of 4 weeks recorded (partial data)'])
    expect(describeMetricProgress({ type: 'numeric' }, null)).toEqual([])
  })
})

describe('score wording', () => {
  it('describes the score in terms of what each metric type aims for', () => {
    expect(describeScore({ type: 'numeric', direction: 'decrease' }, 0.21)).toBe('21% of the way to target')
    expect(describeScore({ type: 'numeric', direction: 'maintain' }, 0.6)).toBe('60% within tolerance')
    expect(describeScore({ type: 'boolean' }, 1)).toBe('100% of target')
    expect(describeScore({ type: 'choice' }, 0.67)).toBe('67% of best')
    expect(describeScore({ type: 'numeric' }, null)).toBe('')
  })

  it('keeps the target label clear of the plan line and leaves headroom', () => {
    const decrease = buildTrendChart({ type: 'numeric', direction: 'decrease', baseline: 200, target: 180 }, goal, [], null, now)
    const increase = buildTrendChart({ type: 'numeric', direction: 'increase', baseline: 0, target: 10 }, goal, [], null, now)

    expect(decrease.options.plugins.annotation.annotations.target.label.yAdjust).toBe(10)
    expect(increase.options.plugins.annotation.annotations.target.label.yAdjust).toBe(-10)
    expect(decrease.options.scales.y.grace).toBe('10%')
  })
})

describe('adjustments', () => {
  const segments = [
    { metricId: 'w', adjustmentId: null, startDate: day(0), count: 30, slope: -0.1 },
    { metricId: 'w', adjustmentId: 'a', adjustmentTitle: 'Started walking after lunch every day', startDate: day(30), count: 20, slope: -0.3 }
  ]

  it('groups segments by metric', () => {
    expect(progressById({ segments }).segments.w).toHaveLength(2)
  })

  it('marks each adjustment on the chart with a shortened label', () => {
    const markers = adjustmentMarkers(segments)

    expect(Object.keys(markers)).toEqual(['adjustment-a'])
    expect(markers['adjustment-a'].xMin).toBe(start + 30 * DAY)
    expect(markers['adjustment-a'].label.content).toBe('Started walking after l…')
    const chart = buildTrendChart({ type: 'numeric' }, goal, [{ observedAt: day(10), value: 1 }], null, now, segments)
    expect(chart.options.plugins.annotation.annotations['adjustment-a']).toBeDefined()
    expect(chart.options.plugins.annotation.annotations.today).toBeDefined()
  })

  it('compares the weekly rate before and after an adjustment', () => {
    expect(describeSegments({ type: 'numeric', unit: 'lb' }, segments)).toEqual(['−0.7 → −2.1 lb/wk after “Started walking after lunch every day”'])
  })

  it('says how much data an adjustment still needs', () => {
    const early = [segments[0], { ...segments[1], count: 3, slope: null }]
    expect(describeSegments({ type: 'numeric' }, early)).toEqual(['after “Started walking after lunch every day”: 3 of 5 values so far'])
    const noBefore = [{ ...segments[0], slope: null }, segments[1]]
    expect(describeSegments({ type: 'numeric', unit: 'lb' }, noBefore)).toEqual(['−2.1 lb/wk since “Started walking after lunch every day”'])
  })

  it('compares adherence for yes/no metrics', () => {
    const habit = [{ adjustmentId: null, adherence: 0.5, count: 10 }, { adjustmentId: 'a', adjustmentTitle: 'Alarm', adherence: 0.9, count: 10 }]
    expect(describeSegments({ type: 'boolean', frequency: 'daily' }, habit)).toEqual(['50% → 90% yes after “Alarm”'])
    expect(describeSegments({ type: 'boolean', frequency: 'weekly' }, [habit[0], { ...habit[1], adherence: null, count: 2 }])).toEqual(['after “Alarm”: 2 of 5 weeks so far'])
    expect(describeSegments({ type: 'numeric' }, [])).toEqual([])
  })

  it('adds markers to the adherence chart too', () => {
    const chart = buildAdherenceChart({ type: 'boolean', frequency: 'daily' }, goal, [], now, segments)
    expect(chart.options.plugins.annotation.annotations['adjustment-a']).toBeDefined()
  })
})

describe('dashboard headline', () => {
  const goalRow = (metric, metricProgress, progress = {}) => ({ goal: { endDate: day(100) }, progress, headlineMetric: metric, headlineMetricProgress: metricProgress })

  it('describes a habit headline by its share of yes answers', () => {
    expect(describeHeadline(goalRow({ name: 'Walked', type: 'boolean', target: 0.85 }, { adherence: 0.7, score: 0.82 }))).toBe('Walked: 70% yes on recorded days (target 85%)')
    expect(describeHeadline(goalRow({ name: 'Worked on it', type: 'boolean', frequency: 'weekly' }, { adherence: 0.75, score: 0.75 }))).toBe('Worked on it: 75% yes on recorded weeks')
  })

  it('names the required rate instead of a projection when the recent trend has stalled', () => {
    const weight = { name: 'Weight', type: 'numeric', unit: 'lb', target: 200, direction: 'decrease' }
    const stalled = { score: 0.3, projectedEnd: 207.1, requiredRate: -1.3, recentRate: 0, stalled: true }

    expect(describeHeadline(goalRow(weight, stalled))).toBe('Weight: needs −1.3 lb/wk to reach 200 lb by Apr 11, 2027, recent trend 0.0 lb/wk')
    expect(describeHeadline(goalRow(weight, { ...stalled, stalled: false }))).toContain('on this trend, 207.1 lb')
    expect(describeHeadline(goalRow({ ...weight, target: null }, stalled))).toContain('on this trend')
  })

  it('falls back to the score, then to effort, then to a hint', () => {
    expect(describeHeadline(goalRow({ name: 'Energy', type: 'scale', direction: 'increase' }, { score: 0.5 }))).toBe('Energy: 50% of the way to target')
    expect(describeHeadline(goalRow(null, null, { effortProgress: 0.4 }))).toBe('Effort 40% across sub-goals and habits')
    expect(describeHeadline(goalRow(null, null, {}))).toBe('Add a metric with a target to track this goal')
  })
})
