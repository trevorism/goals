import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import axios from 'axios'
import TodayView from '../src/views/TodayView.vue'
import { toDay } from '../src/forms.js'
import { button, stubs } from './stubs.js'

vi.mock('axios', () => ({ default: { get: vi.fn(), post: vi.fn() } }))

const yesNo = [{ value: 'yes', label: 'Yes' }, { value: 'no', label: 'No' }]
const items = [
  { metric: { id: 'm1', name: 'Walked 30 minutes', type: 'boolean', frequency: 'daily', choices: yesNo, lastObservedAt: '2026-10-02T00:00:00Z' }, goalTitle: 'Cardio base', rootId: 'r1', rootTitle: 'Improve healthspan' },
  { metric: { id: 'm2', name: 'Body weight', type: 'numeric', frequency: 'weekly', unit: 'lb' }, goalTitle: 'Improve healthspan', rootId: 'r1', rootTitle: 'Improve healthspan' },
  { metric: { id: 'm3', name: 'Prospects contacted', type: 'numeric', frequency: 'weekly' }, goalTitle: 'Outreach', rootId: 'r2', rootTitle: 'Get paying customers' }
]

beforeEach(() => {
  vi.clearAllMocks()
})

async function mountToday() {
  const wrapper = mount(TodayView, { global: { stubs } })
  await flushPromises()
  return wrapper
}

describe('TodayView', () => {
  it('asks for what is due on the local date and groups it by goal tree', async () => {
    axios.get.mockResolvedValue({ data: items })

    const wrapper = await mountToday()

    expect(axios.get).toHaveBeenCalledWith('/api/today', { params: { date: toDay(new Date()) } })
    expect(wrapper.findAll('.group-link').map((link) => link.text())).toEqual(['Improve healthspan', 'Get paying customers'])
    expect(wrapper.findAll('.item-name').map((name) => name.text())).toEqual(['Walked 30 minutes', 'Body weight', 'Prospects contacted'])
    expect(wrapper.findAll('.item-context')[0].text()).toBe('Cardio base · due today · last Oct 2, 2026')
    expect(wrapper.findAll('.item-context')[1].text()).toBe('due this week')
  })

  it('records a value and reloads the list', async () => {
    axios.get.mockResolvedValueOnce({ data: items }).mockResolvedValueOnce({ data: items.slice(1) })
    axios.post.mockResolvedValue({ data: {} })
    const wrapper = await mountToday()

    await button(wrapper, 'Yes').trigger('click')
    await button(wrapper, 'Record').trigger('click')
    await flushPromises()

    expect(axios.post).toHaveBeenCalledWith('/api/metric/m1/observation', expect.objectContaining({ choice: 'yes', observedAt: toDay(new Date()) }))
    expect(wrapper.findAll('.item-name').map((name) => name.text())).toEqual(['Body weight', 'Prospects contacted'])
  })

  it('says when nothing is due', async () => {
    axios.get.mockResolvedValue({ data: [] })

    const wrapper = await mountToday()

    expect(wrapper.find('.all-done').exists()).toBe(true)
  })

  it('shows the server error', async () => {
    axios.get.mockRejectedValue({ response: { data: { message: 'date must be formatted yyyy-MM-dd' } } })

    const wrapper = await mountToday()

    expect(wrapper.find('.page-error').text()).toBe('date must be formatted yyyy-MM-dd')
  })
})
