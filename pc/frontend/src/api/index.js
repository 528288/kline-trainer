import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 600000,
  headers: {
    'Content-Type': 'application/json'
  }
})

api.interceptors.response.use(
  response => response.data,
  error => {
    console.error('API Error:', error)
    return Promise.reject(error)
  }
)

export const trainerApi = {
  getConfig() {
    return api.get('/kline-trainer/config')
  },
  saveConfig(config) {
    return api.post('/kline-trainer/config', config)
  },
  startSession() {
    return api.post('/kline-trainer/session')
  },
  getVip() {
    return api.get('/kline-trainer/vip')
  },
  saveVip(payload) {
    return api.post('/kline-trainer/vip', payload)
  },
  getSaveIds() {
    return api.get('/kline-trainer/save-ids')
  },
  saveRun(groupId, payload) {
    return api.post('/kline-trainer/runs', { group_id: groupId, payload })
  },
  listRuns(groupId) {
    return api.get(`/kline-trainer/runs?group_id=${encodeURIComponent(groupId)}`)
  },
  getRun(runId) {
    return api.get(`/kline-trainer/runs/${encodeURIComponent(runId)}`)
  }
}
