import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import axios from 'axios'
import GoalDetail from '../src/views/GoalDetail.vue'
import { button, stubs } from './stubs.js'

vi.mock('axios', () => ({ default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() } }))
const push = vi.fn()
vi.mock('vue-router', () => ({ useRouter: () => ({ push }) }))
vi.mock('vue-chartjs', () => ({ Scatter: { name: 'Scatter', props: ['data', 'options'], template: '<div class="scatter-stub"></div>' } }))

const walked = { id: 'm1', name: 'Walked 30 minutes', type: 'boolean', measures: 'effort', frequency: 'daily', target: 0.85, choices: [{ value: 'yes', label: 'Yes' }, { value: 'no', label: 'No' }] }

const tree = {
  goal: { id: 'root', title: 'Improve healthspan', status: 'active', startDate: '2026-10-01T00:00:00Z', endDate: '2027-10-01T00:00:00Z' },
  metrics: [],
  children: [
    {
      goal: { id: 'cardio', parentId: 'root', title: 'Cardio base', status: 'active', definitionOfDone: 'Zone 2 for 45 minutes', startDate: '2026-10-01T00:00:00Z', endDate: '2027-01-01T00:00:00Z' },
      metrics: [walked],
      children: []
    }
  ]
}

function routeGets() {
  axios.get.mockImplementation((url) => {
    if (url === '/api/goal/root/tree') return Promise.resolve({ data: tree })
    return Promise.resolve({ data: [] })
  })
}

async function mountDetail() {
  const wrapper = mount(GoalDetail, { props: { id: 'root' }, global: { stubs } })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.clearAllMocks()
  routeGets()
})

describe('GoalDetail', () => {
  it('loads the tree and shows the root goal', async () => {
    const wrapper = await mountDetail()

    expect(axios.get).toHaveBeenCalledWith('/api/goal/root/tree')
    expect(wrapper.find('.goal-title').text()).toBe('Improve healthspan')
    expect(wrapper.find('.no-metrics').exists()).toBe(true)
  })

  it('shows a selected sub-goal with its metrics and definition of done', async () => {
    const wrapper = await mountDetail()

    await wrapper.findAll('.tree-row')[1].trigger('click')
    await flushPromises()

    expect(wrapper.find('.goal-title').text()).toBe('Cardio base')
    expect(wrapper.find('.definition-of-done').text()).toContain('Zone 2 for 45 minutes')
    expect(wrapper.find('.metric-name').text()).toBe('Walked 30 minutes')
    expect(axios.get).toHaveBeenCalledWith('/api/metric/m1/observation')
    expect(axios.get).toHaveBeenCalledWith('/api/goal/cardio/adjustment')
  })

  it('marks the selected goal completed and reloads', async () => {
    axios.put.mockResolvedValue({ data: {} })
    const wrapper = await mountDetail()

    await button(wrapper, 'Mark completed').trigger('click')
    await flushPromises()

    expect(axios.put).toHaveBeenCalledWith('/api/goal/root', { status: 'completed' })
    expect(axios.get.mock.calls.filter(([url]) => url === '/api/goal/root/tree')).toHaveLength(2)
  })

  it('deletes a sub-goal after confirmation and selects its parent', async () => {
    axios.delete.mockResolvedValue({ data: {} })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const wrapper = await mountDetail()
    await wrapper.findAll('.tree-row')[1].trigger('click')

    await button(wrapper, 'Delete').trigger('click')
    await flushPromises()

    expect(axios.delete).toHaveBeenCalledWith('/api/goal/cardio')
    expect(wrapper.find('.goal-title').text()).toBe('Improve healthspan')
    expect(push).not.toHaveBeenCalled()
  })

  it('returns to the list after deleting the root goal', async () => {
    axios.delete.mockResolvedValue({ data: {} })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const wrapper = await mountDetail()

    await button(wrapper, 'Delete').trigger('click')
    await flushPromises()

    expect(axios.delete).toHaveBeenCalledWith('/api/goal/root')
    expect(push).toHaveBeenCalledWith({ name: 'goals' })
  })

  it('does nothing when the delete is not confirmed', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    const wrapper = await mountDetail()

    await button(wrapper, 'Delete').trigger('click')

    expect(axios.delete).not.toHaveBeenCalled()
  })

  it('records a value for a metric', async () => {
    axios.post.mockResolvedValue({ data: {} })
    const wrapper = await mountDetail()
    await wrapper.findAll('.tree-row')[1].trigger('click')
    await flushPromises()

    await button(wrapper, 'Yes').trigger('click')
    await button(wrapper, 'Record').trigger('click')
    await flushPromises()

    expect(axios.post).toHaveBeenCalledWith('/api/metric/m1/observation', expect.objectContaining({ choice: 'yes' }))
  })

  it('shows a goal whose empty lists were left out of the JSON', async () => {
    axios.get.mockImplementation((url) =>
      Promise.resolve({ data: url === '/api/goal/root/tree' ? { goal: { id: 'root', title: 'Bare goal', status: 'active' } } : [] })
    )

    const wrapper = await mountDetail()

    expect(wrapper.find('.goal-title').text()).toBe('Bare goal')
    expect(wrapper.find('.no-metrics').exists()).toBe(true)
  })

  it('shows the status, meters and time elapsed for the selected goal', async () => {
    axios.get.mockImplementation((url) => {
      if (url === '/api/goal/root/tree') return Promise.resolve({ data: tree })
      if (url === '/api/goal/root/progress') {
        return Promise.resolve({
          data: {
            asOf: '2027-01-15T00:00:00Z',
            goals: [
              { goalId: 'root', progress: 0.4, outcomeProgress: null, effortProgress: 0.4, expected: 0.3, pace: -0.1, status: 'behind' },
              { goalId: 'cardio', progress: 0.9, outcomeProgress: 0.9, effortProgress: null, expected: 0.95, pace: -0.05, status: 'on_track' }
            ],
            metrics: [{ metricId: 'm1', score: 0.9, status: 'on_track', adherence: 0.72, currentStreak: 2, bestStreak: 6 }]
          }
        })
      }
      return Promise.resolve({ data: [] })
    })
    const wrapper = await mountDetail()

    expect(wrapper.find('.goal-progress .status-label').text()).toBe('Behind')
    expect(wrapper.find('.time-elapsed').text()).toBe('30% of the time has passed')
    expect(wrapper.find('.outcome-meter').exists()).toBe(false)
    expect(wrapper.find('.effort-meter .meter-value').text()).toBe('40%')
    expect(wrapper.find('.effort-meter .meter-marker').attributes('style')).toContain('left: 50%')
    expect(wrapper.findAll('.tree-progress').map((row) => row.text())).toEqual(['40%▼Behind', '90%✓On track'])

    await wrapper.findAll('.tree-row')[1].trigger('click')
    await flushPromises()

    expect(wrapper.find('.outcome-meter .meter-value').text()).toBe('90%')
    expect(wrapper.find('.metric-score').text()).toBe('90% of target')
    expect(wrapper.findAll('.metric-fact').map((fact) => fact.text())).toEqual(['· 72% yes, last 4 weeks', '· streak 2 (best 6)'])
  })

  it('still shows the goal when progress fails to load', async () => {
    axios.get.mockImplementation((url) => {
      if (url === '/api/goal/root/tree') return Promise.resolve({ data: tree })
      if (url === '/api/goal/root/progress') return Promise.reject(new Error('boom'))
      return Promise.resolve({ data: [] })
    })

    const wrapper = await mountDetail()

    expect(wrapper.find('.goal-title').text()).toBe('Improve healthspan')
    expect(wrapper.find('.goal-progress').exists()).toBe(false)
    expect(wrapper.find('.page-error').exists()).toBe(false)
  })

  it('reloads progress after a value is recorded', async () => {
    axios.post.mockResolvedValue({ data: {} })
    const wrapper = await mountDetail()
    await wrapper.findAll('.tree-row')[1].trigger('click')
    await flushPromises()

    await button(wrapper, 'Yes').trigger('click')
    await button(wrapper, 'Record').trigger('click')
    await flushPromises()

    expect(axios.get.mock.calls.filter(([url]) => url === '/api/goal/root/progress')).toHaveLength(2)
  })

  it('passes each metric its adjustment segments and reloads progress when adjustments change', async () => {
    axios.get.mockImplementation((url) => {
      if (url === '/api/goal/root/tree') return Promise.resolve({ data: tree })
      if (url === '/api/goal/root/progress') {
        return Promise.resolve({
          data: {
            asOf: '2027-01-15T00:00:00Z',
            goals: [],
            metrics: [],
            segments: [
              { metricId: 'm1', adjustmentId: null, startDate: '2026-10-01T00:00:00Z', count: 10, adherence: 0.5 },
              { metricId: 'm1', adjustmentId: 'a1', adjustmentTitle: 'Alarm', startDate: '2026-12-01T00:00:00Z', count: 10, adherence: 0.9 }
            ]
          }
        })
      }
      return Promise.resolve({ data: [] })
    })
    axios.post.mockResolvedValue({ data: {} })
    const wrapper = await mountDetail()
    await wrapper.findAll('.tree-row')[1].trigger('click')
    await flushPromises()

    expect(wrapper.find('.adjustment-effect').text()).toBe('50% → 90% yes after “Alarm”')

    await button(wrapper, 'Add adjustment').trigger('click')
    await wrapper.find('.adjustment-title').setValue('Bought a bike')
    await button(wrapper, 'Save adjustment').trigger('click')
    await flushPromises()

    expect(axios.post).toHaveBeenCalledWith('/api/goal/cardio/adjustment', expect.objectContaining({ title: 'Bought a bike' }))
    expect(axios.get.mock.calls.filter(([url]) => url === '/api/goal/root/progress')).toHaveLength(2)
  })
})
