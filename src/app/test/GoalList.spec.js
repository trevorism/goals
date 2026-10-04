import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import axios from 'axios'
import GoalList from '../src/views/GoalList.vue'
import { toDay } from '../src/forms.js'
import { button, labelled, stubs } from './stubs.js'

vi.mock('axios', () => ({ default: { get: vi.fn(), post: vi.fn() } }))
const push = vi.fn()
vi.mock('vue-router', () => ({ useRouter: () => ({ push }) }))

const health = {
  goal: { id: 'g1', title: 'Improve healthspan', status: 'active', startDate: '2026-07-01T00:00:00Z', endDate: '2027-06-30T00:00:00Z' },
  progress: { goalId: 'g1', progress: 0.34, expected: 0.26, pace: 0.08, status: 'ahead', effortProgress: 0.39, outcomeProgress: 0.34 },
  headlineMetric: { id: 'm1', name: 'Body weight', type: 'numeric', unit: 'lb', target: 180 },
  headlineMetricProgress: { metricId: 'm1', score: 0.21, projectedEnd: 184.3 }
}
const customers = {
  goal: { id: 'g2', title: 'Get paying customers', status: 'active', startDate: '2026-10-01T00:00:00Z', endDate: '2027-06-30T00:00:00Z' },
  progress: { goalId: 'g2', progress: null, expected: 0.01, pace: null, status: 'no_data', effortProgress: null },
  headlineMetric: null,
  headlineMetricProgress: null
}
const done = {
  goal: { id: 'g3', title: 'Ship goals phase 1', status: 'completed', startDate: '2026-10-01T00:00:00Z', endDate: '2026-10-31T00:00:00Z' },
  progress: { goalId: 'g3', progress: 1, status: 'ahead' }
}

beforeEach(() => {
  vi.clearAllMocks()
})

async function mountList(dashboard) {
  axios.get.mockResolvedValue({ data: { asOf: '2026-10-04T00:00:00Z', goals: [], needsYou: [], ...dashboard } })
  const wrapper = mount(GoalList, { global: { stubs } })
  await flushPromises()
  return wrapper
}

describe('GoalList dashboard', () => {
  it('asks for the dashboard on the local date', async () => {
    await mountList({})

    expect(axios.get).toHaveBeenCalledWith('/api/dashboard', { params: { date: toDay(new Date()) } })
  })

  it('shows each active goal with its status, headline, plan marker and time left', async () => {
    const wrapper = await mountList({ goals: [health, customers, done] })

    expect(wrapper.findAll('.goal-name').map((name) => name.text())).toEqual(['Improve healthspan', 'Get paying customers'])
    expect(wrapper.findAll('.status-label').map((label) => label.text())).toEqual(['Ahead', 'No data'])
    expect(wrapper.findAll('.headline').map((line) => line.text())).toEqual([
      'Body weight: on this trend, 184.3 lb by Jun 30, 2027 (target 180 lb)',
      'Add a metric with a target to track this goal'
    ])
    expect(wrapper.find('.time-left').text()).toBe('26% of the time has passed · ends Jun 30, 2027')
    expect(wrapper.findAll('.goal-meter')).toHaveLength(1)
    expect(wrapper.find('.goal-meter .meter-marker').attributes('style')).toContain('left: 26%')
  })

  it('lists closed goals separately and briefly', async () => {
    const wrapper = await mountList({ goals: [health, done] })

    expect(wrapper.findAll('.closed-goal').map((row) => row.text())).toEqual(['Ship goals phase 1completed'])
  })

  it('shows the needs-you strip only when something is stuck', async () => {
    const quiet = await mountList({ goals: [health] })
    expect(quiet.find('.needs-you').exists()).toBe(false)

    const stuck = await mountList({
      goals: [health],
      needsYou: [
        { type: 'unreadable_answer', rootId: 'g1', metricId: 'm1', title: 'Body weight', detail: 'Couldn’t read a number from "lots"', date: '2026-10-02T00:00:00Z' },
        { type: 'missed_questions', rootId: 'g1', metricId: 'm2', title: 'Walked', count: 4, date: '2026-10-03T00:00:00Z' },
        { type: 'past_end_date', rootId: 'g1', goalId: 'g9', title: 'Cardio base', date: '2026-09-30T00:00:00Z' }
      ]
    })
    expect(stuck.findAll('.needs-you-item').map((item) => item.text())).toEqual([
      'Couldn’t record “Body weight” for Oct 2, 2026: Couldn’t read a number from "lots"',
      '“Walked”: 4 questions in a row went unanswered',
      '“Cardio base” ended Sep 30, 2026 but is still active'
    ])
  })

  it('shows an empty state when there are no goals', async () => {
    const wrapper = await mountList({})

    expect(wrapper.find('.empty-state').exists()).toBe(true)
  })

  it('requires dates before creating a root goal', async () => {
    const wrapper = await mountList({})

    await button(wrapper, 'New goal').trigger('click')
    await labelled(wrapper, '.va-input', 'Title').setValue('Get paying customers')
    await button(wrapper, 'Create goal').trigger('click')
    await flushPromises()

    expect(axios.post).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Choose a start and end date')
  })

  it('shows the server error when loading fails', async () => {
    axios.get.mockRejectedValue({ response: { data: { message: 'Forbidden' } } })
    const wrapper = mount(GoalList, { global: { stubs } })
    await flushPromises()

    expect(wrapper.find('.page-error').text()).toBe('Forbidden')
  })
})
