import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ObservationEntry from '../src/components/ObservationEntry.vue'
import { toDay } from '../src/forms.js'
import { button, stubs } from './stubs.js'

const yesNo = [{ value: 'yes', label: 'Yes' }, { value: 'no', label: 'No' }]

function mountEntry(metric) {
  return mount(ObservationEntry, { props: { metric }, global: { stubs } })
}

function recorded(wrapper) {
  return wrapper.emitted('record')?.[0]?.[0]
}

describe('ObservationEntry', () => {
  it('records a number for a numeric metric', async () => {
    const wrapper = mountEntry({ type: 'numeric', unit: 'bpm' })

    await wrapper.find('.value-input').setValue('58')
    await wrapper.find('.note-input').setValue('after a rest day')
    await button(wrapper, 'Record').trigger('click')

    expect(recorded(wrapper)).toEqual({ observedAt: toDay(new Date()), note: 'after a rest day', value: 58 })
  })

  it('records the chosen answer for a yes/no metric and resets', async () => {
    const wrapper = mountEntry({ type: 'boolean', choices: yesNo })

    await button(wrapper, 'Yes').trigger('click')
    expect(button(wrapper, 'Yes').attributes('data-preset')).toBe('primary')
    await button(wrapper, 'Record').trigger('click')

    expect(recorded(wrapper)).toMatchObject({ choice: 'yes' })
    expect(button(wrapper, 'Yes').attributes('data-preset')).toBe('secondary')
  })

  it('offers every scale point', async () => {
    const wrapper = mountEntry({ type: 'scale', scaleMin: 1, scaleMax: 5 })

    expect(wrapper.findAll('.point-button').map((point) => point.text())).toEqual(['1', '2', '3', '4', '5'])
    await button(wrapper, '4').trigger('click')
    await button(wrapper, 'Record').trigger('click')

    expect(recorded(wrapper)).toMatchObject({ value: 4 })
  })

  it('offers the metric choices', async () => {
    const wrapper = mountEntry({ type: 'choice', choices: [{ value: 'poor', label: 'Poor' }, { value: 'good', label: 'Good' }] })

    await button(wrapper, 'Good').trigger('click')
    await button(wrapper, 'Record').trigger('click')

    expect(recorded(wrapper)).toMatchObject({ choice: 'good' })
  })

  it('records a text entry', async () => {
    const wrapper = mountEntry({ type: 'text' })

    await wrapper.find('.text-input').setValue('Slept badly, late meal')
    await button(wrapper, 'Record').trigger('click')

    expect(recorded(wrapper)).toMatchObject({ label: 'Slept badly, late meal' })
  })

  it('refuses to record without an answer', async () => {
    const wrapper = mountEntry({ type: 'boolean', choices: yesNo })

    await button(wrapper, 'Record').trigger('click')

    expect(recorded(wrapper)).toBeUndefined()
    expect(wrapper.find('.form-error').text()).toBe('Choose an answer')
  })
})
