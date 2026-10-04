import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import GoalForm from '../src/components/GoalForm.vue'
import { button, labelled, stubs } from './stubs.js'

const existing = {
  title: 'Cardio base',
  description: 'Build an aerobic base',
  status: 'active',
  startDate: '2026-10-01T00:00:00Z',
  endDate: '2027-01-01T00:00:00Z',
  definitionOfDone: ''
}

describe('GoalForm', () => {
  it('emits the edited goal as a payload', async () => {
    const wrapper = mount(GoalForm, { props: { goal: existing, showStatus: true }, global: { stubs } })

    await labelled(wrapper, '.va-input', 'Title').setValue('Cardio base, phase 1')
    await button(wrapper, 'Save').trigger('click')

    expect(wrapper.emitted('save')[0][0]).toEqual({
      title: 'Cardio base, phase 1',
      description: 'Build an aerobic base',
      status: 'active',
      startDate: '2026-10-01',
      endDate: '2027-01-01',
      definitionOfDone: null
    })
  })

  it('lets a sub-goal leave its dates empty', async () => {
    const wrapper = mount(GoalForm, { global: { stubs } })

    await labelled(wrapper, '.va-input', 'Title').setValue('Sleep')
    await button(wrapper, 'Save').trigger('click')

    expect(wrapper.emitted('save')[0][0]).toMatchObject({ title: 'Sleep', startDate: null, endDate: null })
  })

  it('shows a validation error instead of emitting', async () => {
    const wrapper = mount(GoalForm, { props: { requireDates: true }, global: { stubs } })

    await button(wrapper, 'Save').trigger('click')

    expect(wrapper.emitted('save')).toBeUndefined()
    expect(wrapper.find('.form-error').text()).toBe('Enter a title')
  })

  it('offers the status only when asked', () => {
    expect(labelled(mount(GoalForm, { props: { showStatus: true }, global: { stubs } }), '.va-select', 'Status')).toBeTruthy()
    expect(labelled(mount(GoalForm, { global: { stubs } }), '.va-select', 'Status')).toBeUndefined()
  })

  it('shows the parent\u2019s range and stops dates outside it', async () => {
    const bounds = { startDate: '2026-10-03T00:00:00Z', endDate: '2026-11-07T00:00:00Z' }
    const wrapper = mount(GoalForm, { props: { goal: { title: 'Zone 2', endDate: '2026-12-01T00:00:00Z' }, bounds }, global: { stubs } })

    expect(wrapper.find('.date-bounds').text()).toContain('Oct 3, 2026 \u2013 Nov 7, 2026')
    await button(wrapper, 'Save').trigger('click')

    expect(wrapper.emitted('save')).toBeUndefined()
    expect(wrapper.find('.form-error').text()).toContain('must fall within its parent')
  })

  it('cancels', async () => {
    const wrapper = mount(GoalForm, { global: { stubs } })

    await button(wrapper, 'Cancel').trigger('click')

    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })
})
