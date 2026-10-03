import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ProgressMeter from '../src/components/ProgressMeter.vue'
import ProgressStatus from '../src/components/ProgressStatus.vue'
import MetricChart from '../src/components/MetricChart.vue'

vi.mock('vue-chartjs', () => ({
  Scatter: { name: 'Scatter', props: ['data', 'options'], template: '<div class="scatter-stub" :data-count="data.datasets.length"></div>' }
}))

describe('ProgressStatus', () => {
  it('shows an icon and a label for each status', () => {
    const wrapper = mount(ProgressStatus, { props: { status: 'at_risk' } })

    expect(wrapper.find('.status-label').text()).toBe('At risk')
    expect(wrapper.find('.status-icon').text()).toBe('!')
  })

  it('keeps the label for screen readers when compact', () => {
    const wrapper = mount(ProgressStatus, { props: { status: 'behind', compact: true } })

    expect(wrapper.find('.sr-only').text()).toBe('Behind')
  })
})

describe('ProgressMeter', () => {
  it('fills to the value and marks the plan', () => {
    const wrapper = mount(ProgressMeter, { props: { label: 'Outcome', value: 0.42, expected: 0.5 } })

    expect(wrapper.find('.meter-value').text()).toBe('42%')
    expect(wrapper.find('.meter-fill').attributes('style')).toContain('width: 42%')
    expect(wrapper.find('.meter-marker').attributes('style')).toContain('left: 50%')
    expect(wrapper.find('[role="meter"]').attributes('aria-valuenow')).toBe('42')
  })

  it('omits the marker without a plan', () => {
    const wrapper = mount(ProgressMeter, { props: { label: 'Effort', value: 1 } })

    expect(wrapper.find('.meter-marker').exists()).toBe(false)
  })
})

describe('MetricChart', () => {
  const goal = { startDate: '2027-01-01T00:00:00Z', endDate: '2027-04-11T00:00:00Z' }

  it('renders a chart once there are values', () => {
    const wrapper = mount(MetricChart, {
      props: { metric: { type: 'numeric', target: 10, baseline: 0 }, goal, observations: [{ observedAt: '2027-01-10T00:00:00Z', value: 3 }], now: '2027-02-01T00:00:00Z' }
    })

    expect(wrapper.find('.scatter-stub').attributes('data-count')).toBe('2')
  })

  it('renders nothing without values or for text metrics', () => {
    expect(mount(MetricChart, { props: { metric: { type: 'numeric' }, goal, observations: [] } }).find('.metric-chart').exists()).toBe(false)
    expect(mount(MetricChart, { props: { metric: { type: 'text' }, goal, observations: [{ observedAt: '2027-01-10T00:00:00Z', label: 'x' }] } }).find('.metric-chart').exists()).toBe(false)
  })
})
