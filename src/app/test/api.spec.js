import { describe, it, expect, vi, beforeEach } from 'vitest'
import axios from 'axios'
import { errorMessage, syncTimezone } from '../src/api.js'

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

describe('errorMessage', () => {
  it('prefers the specific reason over the generic status text', () => {
    const error = { response: { data: { message: 'Bad Request', _embedded: { errors: [{ message: 'The end date must be after the start date' }] } } } }

    expect(errorMessage(error)).toBe('The end date must be after the start date')
    expect(errorMessage({ response: { data: { message: 'Forbidden' } } })).toBe('Forbidden')
    expect(errorMessage(new Error('Network Error'))).toBe('Network Error')
  })
})
