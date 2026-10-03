import { describe, it, expect, vi, beforeEach } from 'vitest'
import axios from 'axios'
import { syncTimezone } from '../src/api.js'

vi.mock('axios', () => ({ default: { get: vi.fn(), put: vi.fn() } }))

function memoryStorage() {
  const values = new Map()
  return { getItem: (key) => values.get(key) ?? null, setItem: (key, value) => values.set(key, value) }
}

const browserZone = Intl.DateTimeFormat().resolvedOptions().timeZone

beforeEach(() => {
  vi.clearAllMocks()
})

describe('syncTimezone', () => {
  it('saves the browser timezone when the profile differs, once per session', async () => {
    axios.get.mockResolvedValue({ data: { timezone: 'Mars/Olympus' } })
    axios.put.mockResolvedValue({ data: {} })
    const storage = memoryStorage()

    expect(await syncTimezone(storage)).toBe(true)
    expect(await syncTimezone(storage)).toBe(false)

    expect(axios.put).toHaveBeenCalledTimes(1)
    expect(axios.put).toHaveBeenCalledWith('/api/profile', { timezone: browserZone })
  })

  it('leaves a matching profile alone', async () => {
    axios.get.mockResolvedValue({ data: { timezone: browserZone } })

    expect(await syncTimezone(memoryStorage())).toBe(true)
    expect(axios.put).not.toHaveBeenCalled()
  })
})
