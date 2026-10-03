import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import MetricForm from '../src/components/MetricForm.vue'
import { button, labelled, stubs } from './stubs.js'

function mountForm(props = {}) {
  return mount(MetricForm, { props, global: { stubs } })
}

describe('MetricForm', () => {
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

  it('shows the question field only when prompt collects the metric', () => {
    expect(mountForm().find('.prompt-text').exists()).toBe(false)

    const asked = mountForm({ metric: { id: 'm', name: 'Walked', type: 'boolean', source: 'prompt', promptText: 'Did you walk?', frequency: 'weekly' } })

    expect(asked.find('.prompt-text').element.value).toBe('Did you walk?')
    expect(asked.find('.prompt-settings').text()).toContain('once per week')
  })

  it('shows validation errors instead of emitting', async () => {
    const wrapper = mountForm()

    await button(wrapper, 'Add metric').trigger('click')

    expect(wrapper.emitted('save')).toBeUndefined()
    expect(wrapper.find('.form-error').text()).toBe('Enter a name')
  })
})
