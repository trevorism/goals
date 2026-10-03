import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import axios from 'axios'
import GoalList from '../src/views/GoalList.vue'
import { button, labelled, stubs } from './stubs.js'

vi.mock('axios', () => ({ default: { get: vi.fn(), post: vi.fn() } }))
const push = vi.fn()
vi.mock('vue-router', () => ({ useRouter: () => ({ push }) }))

beforeEach(() => {
  vi.clearAllMocks()
})

function mountList() {
  return mount(GoalList, { global: { stubs } })
}

describe('GoalList', () => {
  it('lists root goals with their status and dates', async () => {
    axios.get.mockResolvedValue({
      data: [{ id: '1', title: 'Improve healthspan', status: 'active', startDate: '2026-10-01T00:00:00Z', endDate: '2027-10-01T00:00:00Z' }]
    })

    const wrapper = mountList()
    await flushPromises()

    expect(axios.get).toHaveBeenCalledWith('/api/goal')
    expect(wrapper.findAll('.goal-card')).toHaveLength(1)
    expect(wrapper.text()).toContain('Improve healthspan')
    expect(wrapper.text()).toContain('2026')
  })

  it('shows an empty state when there are no goals', async () => {
    axios.get.mockResolvedValue({ data: [] })

    const wrapper = mountList()
    await flushPromises()

    expect(wrapper.find('.empty-state').exists()).toBe(true)
  })

  it('requires dates before creating a root goal', async () => {
    axios.get.mockResolvedValue({ data: [] })
    axios.post.mockResolvedValue({ data: { id: '42' } })
    const wrapper = mountList()
    await flushPromises()

    await button(wrapper, 'New goal').trigger('click')
    await labelled(wrapper, '.va-input', 'Title').setValue('Get paying customers')
    await button(wrapper, 'Create goal').trigger('click')
    await flushPromises()

    expect(axios.post).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Choose a start and end date')
  })

  it('shows the server error when loading fails', async () => {
    axios.get.mockRejectedValue({ response: { data: { message: 'Forbidden' } } })

    const wrapper = mountList()
    await flushPromises()

    expect(wrapper.find('.page-error').text()).toBe('Forbidden')
  })
})
