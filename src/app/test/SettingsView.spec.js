import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import axios from 'axios'
import SettingsView from '../src/views/SettingsView.vue'
import { button, labelled, stubs } from './stubs.js'

vi.mock('axios', () => ({ default: { get: vi.fn(), post: vi.fn(), delete: vi.fn() } }))

const claude = { id: 'd1', delegateId: '4856675479584768', label: 'Claude', access: 'edit', createdDate: '2026-10-04T00:00:00Z' }

beforeEach(() => {
  vi.clearAllMocks()
})

async function mountSettings(delegates) {
  axios.get.mockResolvedValue({ data: delegates })
  const wrapper = mount(SettingsView, { global: { stubs } })
  await flushPromises()
  return wrapper
}

describe('SettingsView', () => {
  it('lists who can act on your goals', async () => {
    const wrapper = await mountSettings([claude])

    expect(axios.get).toHaveBeenCalledWith('/api/delegate')
    expect(wrapper.find('.delegate').text()).toContain('Claude')
    expect(wrapper.find('.delegate').text()).toContain('4856675479584768')
    expect(wrapper.find('.delegate').text()).toContain('read and edit')
  })

  it('says when only you can act on your goals', async () => {
    const wrapper = await mountSettings([])

    expect(wrapper.find('.no-delegates').exists()).toBe(true)
  })

  it('gives access with a trimmed id and name', async () => {
    axios.post.mockResolvedValue({ data: claude })
    const wrapper = await mountSettings([])

    await labelled(wrapper, '.va-input', 'Identity id').setValue(' 4856675479584768 ')
    await labelled(wrapper, '.va-input', 'Name').setValue('Claude ')
    await button(wrapper, 'Give access').trigger('click')
    await flushPromises()

    expect(axios.post).toHaveBeenCalledWith('/api/delegate', { delegateId: '4856675479584768', label: 'Claude', access: 'edit' })
    expect(axios.get).toHaveBeenCalledTimes(2)
  })

  it('requires an id and a name', async () => {
    const wrapper = await mountSettings([])

    await button(wrapper, 'Give access').trigger('click')

    expect(axios.post).not.toHaveBeenCalled()
    expect(wrapper.find('.settings-error').text()).toBe('Enter the identity id and a name for it')
  })

  it('revokes access after confirmation', async () => {
    axios.delete.mockResolvedValue({ data: claude })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const wrapper = await mountSettings([claude])

    await button(wrapper, 'Revoke').trigger('click')
    await flushPromises()

    expect(axios.delete).toHaveBeenCalledWith('/api/delegate/d1')
  })
})
