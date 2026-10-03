import { describe, it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import SplashPage from '../src/components/SplashPage.vue'

vi.mock('axios', () => ({
  default: { get: vi.fn(() => Promise.resolve({ data: 'pong' })) }
}))

const stubs = { 'va-icon': true }

describe('SplashPage', () => {
  it('renders the msg prop', () => {
    const wrapper = mount(SplashPage, { props: { msg: 'Hello World' }, global: { stubs } })
    expect(wrapper.text()).toContain('Hello World')
  })

  it('shows the ping response after mount', async () => {
    const wrapper = mount(SplashPage, { props: { msg: 'x' }, global: { stubs } })
    await flushPromises()
    expect(wrapper.text()).toContain('pong')
  })
})
