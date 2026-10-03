import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import GoalTree from '../src/components/GoalTree.vue'
import { stubs } from './stubs.js'

const node = (id, title, children = [], status = 'active') => ({ goal: { id, title, status }, metrics: [], children })

const tree = node('root', 'Improve healthspan', [
  node('cardio', 'Cardio base', [node('walk', 'Walk 30 minutes')]),
  node('sleep', 'Sleep', [], 'completed')
])

function titles(wrapper) {
  return wrapper.findAll('.tree-title').map((title) => title.text())
}

describe('GoalTree', () => {
  it('renders the whole tree expanded', () => {
    const wrapper = mount(GoalTree, { props: { node: tree, selectedId: 'root' }, global: { stubs } })

    expect(titles(wrapper)).toEqual(['Improve healthspan', 'Cardio base', 'Walk 30 minutes', 'Sleep'])
  })

  it('marks non-active goals with their status', () => {
    const wrapper = mount(GoalTree, { props: { node: tree }, global: { stubs } })

    expect(wrapper.findAll('.va-chip').map((chip) => chip.text())).toEqual(['completed'])
  })

  it('emits the id of a selected nested goal', async () => {
    const wrapper = mount(GoalTree, { props: { node: tree }, global: { stubs } })

    await wrapper.findAll('.tree-row')[2].trigger('click')

    expect(wrapper.emitted('select')[0]).toEqual(['walk'])
  })

  it('collapses a branch without selecting it', async () => {
    const wrapper = mount(GoalTree, { props: { node: tree }, global: { stubs } })

    await wrapper.findAll('.tree-toggle')[1].trigger('click')

    expect(titles(wrapper)).toEqual(['Improve healthspan', 'Cardio base', 'Sleep'])
    expect(wrapper.emitted('select')).toBeUndefined()
  })

  it('renders a leaf whose empty children list was left out of the JSON', () => {
    const wrapper = mount(GoalTree, { props: { node: { goal: { id: 'leaf', title: 'Leaf', status: 'active' } } }, global: { stubs } })

    expect(titles(wrapper)).toEqual(['Leaf'])
    expect(wrapper.find('.tree-toggle').exists()).toBe(false)
  })
})
