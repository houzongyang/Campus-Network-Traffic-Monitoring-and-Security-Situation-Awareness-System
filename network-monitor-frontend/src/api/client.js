import axios from 'axios'

const API_BASE_URL = import.meta.env.VITE_API_URL?.trim() || '/api'

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

apiClient.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const message = error.response?.data?.message || error.message || 'Request failed'
    return Promise.reject(new Error(message))
  }
)

const compactParams = (params = {}) =>
  Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== '' && value !== null && value !== undefined)
  )

export const dashboardAPI = {
  getOverview: ({
    metricsMinutesAgo = -5,
    topLimit = 10,
    topMinutesAgo = -5,
    topMetric = 'bytes',
    regionMinutesAgo = -30,
    trendMinutesAgo = -60,
    trendBucketMinutes = 5
  } = {}) =>
    apiClient.get('/dashboard/overview', {
      params: {
        metricsMinutesAgo,
        topLimit,
        topMinutesAgo,
        topMetric,
        regionMinutesAgo,
        trendMinutesAgo,
        trendBucketMinutes
      }
    }),

  getMetrics: (minutesAgo = -5) =>
    apiClient.get('/dashboard/metrics', { params: { minutesAgo } }),

  getTopFlows: (limit = 10, minutesAgo = -5, metric = 'bytes') =>
    apiClient.get('/dashboard/top-flows', { params: { limit, minutesAgo, metric } }),

  getRegionTraffic: (minutesAgo = -30) =>
    apiClient.get('/dashboard/region-traffic', { params: { minutesAgo } }),

  getRegionHierarchy: (params = {}) =>
    apiClient.get('/dashboard/region-hierarchy', { params: compactParams(params) }),

  getThroughputTrend: (minutesAgo = -60, bucketMinutes = 5) =>
    apiClient.get('/dashboard/throughput-trend', { params: { minutesAgo, bucketMinutes } }),

  getThreatStatistics: (minutesAgo = -60) =>
    apiClient.get('/dashboard/threat-statistics', { params: { minutesAgo } }),

  getSupportedProtocols: () =>
    apiClient.get('/dashboard/supported-protocols'),

  health: () =>
    apiClient.get('/dashboard/health')
}

export const flowAPI = {
  search: (params) =>
    apiClient.post('/flows/search', null, { params: compactParams(params) }),

  exportSearch: (params) =>
    apiClient.get('/flows/search/export', {
      params: compactParams(params),
      responseType: 'blob'
    }),

  getFlowDetail: (flowId) =>
    apiClient.get(`/flows/${flowId}`),

  getFlowsByIp: (ip, minutesAgo = -60) =>
    apiClient.get(`/flows/by-ip/${ip}`, { params: { minutesAgo } }),

  getFlowsByProtocol: (protocol) =>
    apiClient.get(`/flows/by-protocol/${protocol}`),

  getIpProfile: (ip, minutesAgo = -60) =>
    apiClient.get(`/flows/ip-profile/${ip}`, { params: { minutesAgo } })
}

export const securityAPI = {
  getAlerts: (params = {}) =>
    apiClient.get('/security/alerts', { params: compactParams(params) }),

  exportAlerts: (params = {}) =>
    apiClient.get('/security/alerts/export', {
      params: compactParams(params),
      responseType: 'blob'
    }),

  getAlertsForIp: (ip) =>
    apiClient.get(`/security/alerts/${ip}`),

  getCriticalAlerts: (minutesAgo = -60) =>
    apiClient.get('/security/critical-alerts', { params: { minutesAgo } }),

  getAlertStatistics: (minutesAgo = -60) =>
    apiClient.get('/security/alert-statistics', { params: { minutesAgo } }),

  runDetection: (minutesAgo = -60) =>
    apiClient.post('/security/run-detection', null, { params: { minutesAgo } }),

  getGeoDistribution: (minutesAgo = -60) =>
    apiClient.get('/security/geo-distribution', { params: { minutesAgo } })
}

export default apiClient
