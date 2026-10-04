import axios from 'axios'

const data = (request) => request.then((response) => response.data)

export const goalsApi = {
  listRoots: () => data(axios.get('/api/goal')),
  get: (id) => data(axios.get(`/api/goal/${id}`)),
  tree: (id) => data(axios.get(`/api/goal/${id}/tree`)),
  progress: (id) => data(axios.get(`/api/goal/${id}/progress`)),
  createRoot: (goal) => data(axios.post('/api/goal', goal)),
  createChild: (parentId, goal) => data(axios.post(`/api/goal/${parentId}/child`, goal)),
  update: (id, goal) => data(axios.put(`/api/goal/${id}`, goal)),
  remove: (id) => data(axios.delete(`/api/goal/${id}`))
}

export const metricsApi = {
  create: (goalId, metric) => data(axios.post(`/api/goal/${goalId}/metric`, metric)),
  update: (id, metric) => data(axios.put(`/api/metric/${id}`, metric)),
  remove: (id) => data(axios.delete(`/api/metric/${id}`))
}

export const observationsApi = {
  list: (metricId) => data(axios.get(`/api/metric/${metricId}/observation`)),
  create: (metricId, observation) => data(axios.post(`/api/metric/${metricId}/observation`, observation)),
  remove: (id) => data(axios.delete(`/api/observation/${id}`))
}

export const adjustmentsApi = {
  list: (goalId) => data(axios.get(`/api/goal/${goalId}/adjustment`)),
  create: (goalId, adjustment) => data(axios.post(`/api/goal/${goalId}/adjustment`, adjustment)),
  remove: (id) => data(axios.delete(`/api/adjustment/${id}`))
}

export const profileApi = {
  get: () => data(axios.get('/api/profile')),
  update: (profile) => data(axios.put('/api/profile', profile))
}

const TIMEZONE_SYNCED = 'goals.timezoneSynced'

export async function syncTimezone(storage = window.sessionStorage) {
  const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone
  if (!timezone || storage.getItem(TIMEZONE_SYNCED) === timezone) {
    return false
  }
  const profile = await profileApi.get()
  if (profile.timezone !== timezone) {
    await profileApi.update({ timezone })
  }
  storage.setItem(TIMEZONE_SYNCED, timezone)
  return true
}

export const dashboardApi = {
  get: (date) => data(axios.get('/api/dashboard', { params: { date } }))
}

export const todayApi = {
  due: (date) => data(axios.get('/api/today', { params: { date } }))
}

export function errorMessage(error) {
  return error?.response?.data?._embedded?.errors?.[0]?.message || error?.response?.data?.message || error?.message || 'Request failed'
}
