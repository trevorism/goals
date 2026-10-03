import { describe, it, expect } from 'vitest'
import {
  buildGoalPayload,
  buildMetricPayload,
  buildObservationPayload,
  countNodes,
  describeObservation,
  describeTarget,
  findNode,
  fromDay,
  metricFormFrom,
  scalePoints,
  toDay,
  validateGoal,
  validateMetric,
  validateObservation
} from '../src/forms.js'

describe('dates', () => {
  it('sends calendar days and reads them back without shifting the day', () => {
    expect(toDay(new Date(2027, 0, 5))).toBe('2027-01-05')
    expect(toDay(null)).toBeNull()
    const day = fromDay('2027-01-05T00:00:00Z')
    expect([day.getFullYear(), day.getMonth(), day.getDate()]).toEqual([2027, 0, 5])
    expect(fromDay(Date.UTC(2027, 0, 5))).toEqual(new Date(2027, 0, 5))
    expect(fromDay(null)).toBeNull()
  })
})

describe('goals', () => {
  const form = { title: '  Improve healthspan ', description: '', status: 'active', startDate: new Date(2026, 9, 1), endDate: new Date(2027, 9, 1), definitionOfDone: ' ' }

  it('builds a trimmed payload with day strings and nulls for blanks', () => {
    expect(buildGoalPayload(form)).toEqual({
      title: 'Improve healthspan',
      description: null,
      status: 'active',
      startDate: '2026-10-01',
      endDate: '2027-10-01',
      definitionOfDone: null
    })
  })

  it('requires a title, dates for a root goal, and an end after the start', () => {
    expect(validateGoal({ ...form, title: ' ' }, { requireDates: false })).toBe('Enter a title')
    expect(validateGoal({ ...form, endDate: null }, { requireDates: true })).toBe('Choose a start and end date')
    expect(validateGoal({ ...form, endDate: null, startDate: null }, { requireDates: false })).toBe('')
    expect(validateGoal({ ...form, endDate: new Date(2026, 9, 1) }, { requireDates: true })).toBe('The end date must be after the start date')
    expect(validateGoal(form, { requireDates: true })).toBe('')
  })
})

describe('metrics', () => {
  it('sends numeric fields only for numeric metrics', () => {
    const form = { ...metricFormFrom(null), name: 'Resting heart rate', unit: 'bpm', direction: 'decrease', baseline: '62', target: '55' }

    expect(buildMetricPayload(form)).toEqual({
      name: 'Resting heart rate',
      description: null,
      type: 'numeric',
      measures: 'outcome',
      frequency: 'daily',
      direction: 'decrease',
      baseline: 62,
      target: 55,
      unit: 'bpm'
    })
  })

  it('turns a yes/no target percentage into a share', () => {
    const form = { ...metricFormFrom(null), name: 'Walked', type: 'boolean', measures: 'effort', targetPercent: '85' }

    const payload = buildMetricPayload(form)

    expect(payload.target).toBe(0.85)
    expect(payload).not.toHaveProperty('direction')
    expect(payload.measures).toBe('effort')
  })

  it('sends label-only choices so the server derives values, dropping blank rows', () => {
    const form = { ...metricFormFrom(null), name: 'Meal quality', type: 'choice', choices: ['Poor', ' ', 'Good'] }

    expect(buildMetricPayload(form).choices).toEqual([{ label: 'Poor' }, { label: 'Good' }])
  })

  it('sends scale bounds and a tolerance for maintain', () => {
    const form = { ...metricFormFrom(null), name: 'Energy', type: 'scale', direction: 'maintain', tolerance: '0.5', target: '4' }

    expect(buildMetricPayload(form)).toMatchObject({ scaleMin: 1, scaleMax: 5, tolerance: 0.5, target: 4, direction: 'maintain' })
  })

  it('validates per type', () => {
    const base = metricFormFrom(null)
    expect(validateMetric({ ...base, name: '' })).toBe('Enter a name')
    expect(validateMetric({ ...base, name: 'x', type: 'scale', scaleMin: 5, scaleMax: 5 })).toBe('The scale minimum must be below its maximum')
    expect(validateMetric({ ...base, name: 'x', type: 'choice', choices: ['one', ''] })).toBe('Enter at least two choices')
    expect(validateMetric({ ...base, name: 'x', type: 'boolean', targetPercent: '150' })).toBe('The target must be between 1% and 100%')
    expect(validateMetric({ ...base, name: 'x', direction: 'maintain', tolerance: '' })).toBe('A metric to maintain needs a tolerance')
    expect(validateMetric({ ...base, name: 'x' })).toBe('')
  })

  it('prefills an edit form from a stored metric', () => {
    const form = metricFormFrom({ name: 'Walked', type: 'boolean', target: 0.857, choices: [{ value: 'yes', label: 'Yes' }, { value: 'no', label: 'No' }] })

    expect(form.targetPercent).toBe(86)
    expect(form.target).toBe('')
    expect(form.choices).toEqual(['Yes', 'No'])
  })

  it('describes targets for display', () => {
    expect(describeTarget({ type: 'numeric', baseline: 62, target: 55, unit: 'bpm' })).toBe('62 bpm → 55 bpm')
    expect(describeTarget({ type: 'boolean', target: 0.85 })).toBe('Yes 85% of the time')
    expect(describeTarget({ type: 'numeric' })).toBe('')
  })

  it('lists every scale point', () => {
    expect(scalePoints({ scaleMin: 1, scaleMax: 5 })).toEqual([1, 2, 3, 4, 5])
  })
})

describe('observations', () => {
  const day = new Date(2027, 1, 3)

  it('builds the payload each metric type expects', () => {
    expect(buildObservationPayload({ type: 'numeric' }, { value: '61.5', observedAt: day, note: '' })).toEqual({ observedAt: '2027-02-03', note: null, value: 61.5 })
    expect(buildObservationPayload({ type: 'boolean' }, { choice: 'yes', observedAt: day })).toMatchObject({ choice: 'yes' })
    expect(buildObservationPayload({ type: 'choice' }, { choice: 'good', observedAt: day })).toMatchObject({ choice: 'good' })
    expect(buildObservationPayload({ type: 'scale' }, { value: 4, observedAt: day })).toMatchObject({ value: 4 })
    expect(buildObservationPayload({ type: 'text' }, { text: ' Slept badly ', observedAt: day, note: 'travel' })).toEqual({ observedAt: '2027-02-03', note: 'travel', label: 'Slept badly' })
  })

  it('defaults the date to today', () => {
    expect(buildObservationPayload({ type: 'numeric' }, { value: 1 }).observedAt).toBe(toDay(new Date()))
  })

  it('validates per type', () => {
    expect(validateObservation({ type: 'numeric' }, { value: '' })).toBe('Enter a number')
    expect(validateObservation({ type: 'scale', scaleMin: 1, scaleMax: 5 }, { value: 6 })).toBe('Choose a value from 1 to 5')
    expect(validateObservation({ type: 'boolean' }, { choice: null })).toBe('Choose an answer')
    expect(validateObservation({ type: 'text' }, { text: ' ' })).toBe('Write an entry')
    expect(validateObservation({ type: 'numeric' }, { value: '3' })).toBe('')
  })

  it('describes recorded values', () => {
    expect(describeObservation({ type: 'numeric', unit: 'bpm' }, { value: 58 })).toBe('58 bpm')
    expect(describeObservation({ type: 'scale', scaleMax: 5 }, { value: 4 })).toBe('4 / 5')
    expect(describeObservation({ type: 'boolean' }, { label: 'Yes' })).toBe('Yes')
    expect(describeObservation({ type: 'boolean' }, { missed: true })).toBe('Missed')
  })
})

describe('trees', () => {
  const tree = {
    goal: { id: 'root' },
    children: [
      { goal: { id: 'a' }, children: [{ goal: { id: 'a1' }, children: [] }] },
      { goal: { id: 'b' }, children: [] }
    ]
  }

  it('finds nodes and counts them', () => {
    expect(findNode(tree, 'a1').goal.id).toBe('a1')
    expect(findNode(tree, 'missing')).toBeNull()
    expect(countNodes(tree)).toBe(4)
  })
})
