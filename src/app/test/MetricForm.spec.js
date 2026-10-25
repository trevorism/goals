import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import axios from 'axios'
import MetricForm from '../src/components/MetricForm.vue'
import { button, labelled, stubs } from './stubs.js'

vi.mock('axios', () => ({ default: { get: vi.fn() } }))

function mountForm(props = {}) {
  return mount(MetricForm, { props, global: { stubs } })
}

describe('MetricForm', () => {
  beforeEach(() => {
    axios.get.mockReset()
    axios.get.mockResolvedValue({ data: [] })
  })

  it('shows number fields for a new numeric metric and emits it', async () => {
    const wrapper = mountForm()

    await labelled(wrapper, '.va-input', 'Name').setValue('Resting heart rate')
    await labelled(wrapper, '.va-input', 'Unit').setValue('bpm')
    await labelled(wrapper, '.va-input', 'Target').setValue('55')
    await button(wrapper, 'Add metric').trigger('click')

    expect(wrapper.emitted('save')[0][0]).toMatchObject({ name: 'Resting heart rate', type: 'numeric', unit: 'bpm', target: 55, direction: 'increase' })
  })

  it('shows a target percentage for a yes/no metric', () => {
    const wrapper = mountForm({ metric: { id: 'm', name: 'Walked', type: 'boolean', target: 0.85, measures: 'effort', frequency: 'daily' } })

    expect(wrapper.find('.target-percent').element.value).toBe('85')
    expect(labelled(wrapper, '.va-input', 'Target')).toBeUndefined()
    expect(button(wrapper, 'Save metric')).toBeTruthy()
  })

  it('locks the type when editing', () => {
    const wrapper = mountForm({ metric: { id: 'm', name: 'Weight', type: 'numeric' } })

    expect(wrapper.find('.metric-type').attributes('data-disabled')).toBe('true')
  })

  it('edits choice rows and keeps at least two', async () => {
    const wrapper = mountForm({ metric: { id: 'm', name: 'Meal quality', type: 'choice', choices: [{ value: 'poor', label: 'Poor' }, { value: 'good', label: 'Good' }] } })

    expect(wrapper.findAll('.choice-input')).toHaveLength(2)
    expect(button(wrapper, 'Remove')).toBeUndefined()

    await button(wrapper, 'Add choice').trigger('click')
    await wrapper.findAll('.choice-input')[2].setValue('Great')
    await button(wrapper, 'Save metric').trigger('click')

    expect(wrapper.emitted('save')[0][0].choices).toEqual([{ label: 'Poor' }, { label: 'Good' }, { label: 'Great' }])
  })

  it('shows validation errors instead of emitting', async () => {
    const wrapper = mountForm()

    await button(wrapper, 'Add metric').trigger('click')

    expect(wrapper.emitted('save')).toBeUndefined()
    expect(wrapper.find('.form-error').text()).toBe('Enter a name')
  })

  it('asks for times per week instead of a yes percentage once it is set', async () => {
    const wrapper = mountForm({ metric: { id: 'm', name: 'Running', type: 'boolean', frequency: 'daily', target: 0.43, timesPerWeek: 3 } })

    expect(wrapper.find('.times-per-week').element.value).toBe('3')
    expect(wrapper.find('.target-percent').exists()).toBe(false)

    await wrapper.find('.times-per-week').setValue('')
    expect(wrapper.find('.target-percent').exists()).toBe(true)
  })

  it('offers at least and at most directions and hides the baseline for them', async () => {
    const wrapper = mountForm({ metric: { id: 'm', name: 'Family meals', type: 'numeric', direction: 'at_least', target: 4 } })

    expect(selectNamed(wrapper, 'Direction').props('options').map((option) => option.value)).toEqual(['increase', 'decrease', 'maintain', 'at_least', 'at_most'])
    expect(wrapper.findAll('.va-input').some((input) => input.attributes('data-label') === 'Baseline')).toBe(false)
  })

  it('pauses and resumes a metric only when editing', async () => {
    expect(mountForm().find('.metric-enabled').exists()).toBe(false)

    const wrapper = mountForm({ metric: { id: 'm', name: 'Fees earned', type: 'numeric', enabled: true } })
    expect(wrapper.find('.paused-hint').exists()).toBe(false)

    await wrapper.find('.metric-enabled').setValue(false)
    expect(wrapper.find('.paused-hint').text()).toContain('Paused')
    await button(wrapper, 'Save metric').trigger('click')

    expect(wrapper.emitted('save')[0][0]).toMatchObject({ enabled: false })
  })
})

function selectNamed(wrapper, label) {
  return wrapper.findAllComponents(stubs['va-select']).find((select) => select.props('label') === label)
}
