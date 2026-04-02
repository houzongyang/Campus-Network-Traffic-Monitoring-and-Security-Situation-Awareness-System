import { create } from 'zustand'
import { dashboardAPI, flowAPI, securityAPI } from '../api/client'

const failMessage = (error) => error?.message || '请求失败'

let dashboardOverviewInFlight = null
let dashboardMetricsSocket = null
let dashboardWsShouldReconnect = false
let dashboardWsReconnectTimer = null
let dashboardWsReconnectAttempts = 0
let dashboardWsReconnectTotal = 0

const DASHBOARD_WS_BASE_DELAY_MS = 2000
const DASHBOARD_WS_MAX_DELAY_MS = 15000

const resolveDashboardWsUrl = () => {
  const configuredApiUrl = import.meta.env.VITE_API_URL?.trim()
  if (configuredApiUrl && /^https?:\/\//i.test(configuredApiUrl)) {
    const protocol = configuredApiUrl.startsWith('https') ? 'wss' : 'ws'
    const normalized = configuredApiUrl.replace(/\/api\/?$/i, '')
    return `${normalized.replace(/^https?/i, protocol)}/ws/dashboard/metrics`
  }

  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
  return `${protocol}://${window.location.host}/ws/dashboard/metrics`
}

const clearDashboardReconnectTimer = () => {
  if (!dashboardWsReconnectTimer) {
    return
  }
  window.clearTimeout(dashboardWsReconnectTimer)
  dashboardWsReconnectTimer = null
}

export const useDashboardStore = create((set, get) => ({
  metrics: {
    throughputMbps: 0,
    pps: 0,
    activeIps: 0,
    appDistributionBytes: {},
    appDistributionPackets: {},
    timestamp: null
  },
  topFlows: [],
  regionTraffic: {},
  throughputTrend: [],
  wsConnected: false,
  wsState: 'disconnected',
  wsReconnectStreak: 0,
  wsReconnectTotal: 0,
  loading: false,
  error: null,

  fetchOverview: async (metricMode = 'bytes', options = {}) => {
    const { silent = false } = options

    if (dashboardOverviewInFlight) {
      return dashboardOverviewInFlight
    }

    if (!silent) {
      set({ loading: true, error: null })
    }

    dashboardOverviewInFlight = dashboardAPI.getOverview({
      metricsMinutesAgo: -5,
      topLimit: 10,
      topMinutesAgo: -5,
      topMetric: metricMode,
      regionMinutesAgo: -30,
      trendMinutesAgo: -60,
      trendBucketMinutes: 5
    })

    try {
      const overview = await dashboardOverviewInFlight
      if (overview.status !== 'success') {
        throw new Error('仪表盘数据加载失败')
      }

      set({
        metrics: overview.metrics || {},
        topFlows: overview.topFlows || [],
        regionTraffic: overview.regionTraffic || {},
        throughputTrend: overview.throughputTrend || [],
        loading: false,
        error: null
      })
    } catch (error) {
      set({ loading: false, error: failMessage(error) })
    } finally {
      dashboardOverviewInFlight = null
    }
  },

  connectRealtimeMetrics: () => {
    dashboardWsShouldReconnect = true
    clearDashboardReconnectTimer()

    if (
      dashboardMetricsSocket
      && (dashboardMetricsSocket.readyState === WebSocket.OPEN || dashboardMetricsSocket.readyState === WebSocket.CONNECTING)
    ) {
      return
    }

    const scheduleReconnect = () => {
      if (!dashboardWsShouldReconnect || dashboardWsReconnectTimer) {
        return
      }

      dashboardWsReconnectAttempts += 1
      dashboardWsReconnectTotal += 1
      const delay = Math.min(
        DASHBOARD_WS_BASE_DELAY_MS * (2 ** (dashboardWsReconnectAttempts - 1)),
        DASHBOARD_WS_MAX_DELAY_MS
      )

      set({
        wsConnected: false,
        wsState: 'reconnecting',
        wsReconnectStreak: dashboardWsReconnectAttempts,
        wsReconnectTotal: dashboardWsReconnectTotal
      })

      dashboardWsReconnectTimer = window.setTimeout(() => {
        dashboardWsReconnectTimer = null
        if (dashboardWsShouldReconnect) {
          get().connectRealtimeMetrics()
        }
      }, delay)
    }

    set({ wsState: 'connecting' })
    try {
      dashboardMetricsSocket = new WebSocket(resolveDashboardWsUrl())
    } catch (error) {
      set({ wsConnected: false, wsState: 'reconnecting', error: failMessage(error) })
      dashboardMetricsSocket = null
      scheduleReconnect()
      return
    }

    dashboardMetricsSocket.onopen = () => {
      dashboardWsReconnectAttempts = 0
      set({
        wsConnected: true,
        wsState: 'connected',
        wsReconnectStreak: 0,
        wsReconnectTotal: dashboardWsReconnectTotal,
        error: null
      })
    }

    dashboardMetricsSocket.onmessage = (event) => {
      try {
        const payload = JSON.parse(event.data)
        if (payload.type !== 'dashboard_metrics' || !payload.metrics) {
          return
        }
        set((state) => ({
          metrics: { ...state.metrics, ...payload.metrics },
          error: null
        }))
      } catch (_error) {
      }
    }

    dashboardMetricsSocket.onerror = () => {
      set({ wsConnected: false, wsState: 'reconnecting' })
    }

    dashboardMetricsSocket.onclose = () => {
      dashboardMetricsSocket = null
      if (!dashboardWsShouldReconnect) {
        set({ wsConnected: false, wsState: 'disconnected', wsReconnectStreak: 0 })
        return
      }
      scheduleReconnect()
    }
  },

  disconnectRealtimeMetrics: () => {
    dashboardWsShouldReconnect = false
    dashboardWsReconnectAttempts = 0
    clearDashboardReconnectTimer()

    if (dashboardMetricsSocket) {
      dashboardMetricsSocket.close()
      dashboardMetricsSocket = null
    }

    set({
      wsConnected: false,
      wsState: 'disconnected',
      wsReconnectStreak: 0,
      wsReconnectTotal: dashboardWsReconnectTotal
    })
  }
}))

export const useSecurityStore = create((set, get) => ({
  alerts: [],
  total: 0,
  page: 0,
  size: 100,
  totalPages: 0,
  lastQuery: null,
  threatStats: {
    totalAlerts: 0,
    byType: {},
    bySeverity: {},
    timeline: []
  },
  geoPoints: [],
  loading: false,
  error: null,

  fetchOverview: async (params = {}, options = {}) => {
    const { silent = false } = options
    const query = {
      minutesAgo: params.minutesAgo ?? -60,
      startTime: params.startTime,
      endTime: params.endTime,
      alertType: params.alertType,
      severity: params.severity,
      srcIp: params.srcIp,
      dstIp: params.dstIp,
      keyword: params.keyword,
      sortBy: params.sortBy ?? 'detectedTime',
      sortOrder: params.sortOrder ?? 'desc',
      page: Math.max(0, params.page ?? 0),
      size: Math.max(1, Math.min(params.size ?? 100, 500))
    }

    if (!silent) {
      set({ loading: true, error: null })
    }

    try {
      const [alerts, threatStats, geo] = await Promise.all([
        securityAPI.getAlerts(query),
        securityAPI.getAlertStatistics(query.minutesAgo),
        securityAPI.getGeoDistribution(query.minutesAgo)
      ])

      if ([alerts, threatStats, geo].some((item) => item.status !== 'success')) {
        throw new Error('安全态势数据加载失败')
      }

      set({
        alerts: alerts.alerts || [],
        total: alerts.total || 0,
        page: alerts.page || 0,
        size: alerts.size || query.size,
        totalPages: alerts.totalPages || 0,
        lastQuery: query,
        threatStats,
        geoPoints: geo.points || [],
        loading: false,
        error: null
      })
    } catch (error) {
      set({ loading: false, error: failMessage(error) })
    }
  },

  exportAlertsResult: async () => {
    const { lastQuery } = get()
    if (!lastQuery) {
      return { status: 'error', message: '请先执行查询后再导出' }
    }

    try {
      const blob = await securityAPI.exportAlerts({ ...lastQuery, page: undefined, size: undefined })
      const blobUrl = window.URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = blobUrl
      anchor.download = 'security-alerts-export.csv'
      document.body.appendChild(anchor)
      anchor.click()
      anchor.remove()
      window.URL.revokeObjectURL(blobUrl)
      return { status: 'success' }
    } catch (error) {
      return { status: 'error', message: failMessage(error) }
    }
  },

  runThreatDetection: async () => {
    set({ loading: true, error: null })
    try {
      const result = await securityAPI.runDetection()
      if (result.status !== 'success') {
        throw new Error(result.message || '威胁检测失败')
      }
      set({ loading: false })
      const { lastQuery, fetchOverview } = get()
      await fetchOverview(lastQuery || { minutesAgo: -60, page: 0, size: 100 }, { silent: false })
      return result
    } catch (error) {
      set({ loading: false, error: failMessage(error) })
      return { status: 'error', message: failMessage(error) }
    }
  }
}))

export const useFlowStore = create((set, get) => ({
  searchResults: [],
  ipProfile: null,
  total: 0,
  page: 0,
  size: 100,
  totalPages: 0,
  lastSearchParams: null,
  loading: false,
  error: null,

  searchFlows: async (params) => {
    set({ loading: true, error: null })
    try {
      const data = await flowAPI.search(params)
      if (data.status !== 'success') {
        throw new Error(data.message || '流检索失败')
      }

      set({
        searchResults: data.flows || [],
        total: data.total || 0,
        page: data.page || 0,
        size: data.size || params.size || 100,
        totalPages: data.totalPages || 0,
        lastSearchParams: params,
        loading: false,
        error: null
      })
    } catch (error) {
      set({ loading: false, error: failMessage(error) })
    }
  },

  exportSearchResult: async () => {
    const { lastSearchParams } = get()
    if (!lastSearchParams) {
      return { status: 'error', message: '请先执行检索后再导出' }
    }

    try {
      const blob = await flowAPI.exportSearch({ ...lastSearchParams, page: undefined, size: undefined })
      const blobUrl = window.URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = blobUrl
      anchor.download = 'flow-search-export.csv'
      document.body.appendChild(anchor)
      anchor.click()
      anchor.remove()
      window.URL.revokeObjectURL(blobUrl)
      return { status: 'success' }
    } catch (error) {
      return { status: 'error', message: failMessage(error) }
    }
  },

  fetchIpProfile: async (ip, minutesAgo = -60) => {
    set({ loading: true, error: null })
    try {
      const data = await flowAPI.getIpProfile(ip, minutesAgo)
      if (data.status !== 'success') {
        throw new Error(data.message || 'IP画像加载失败')
      }
      set({ ipProfile: data, loading: false, error: null })
    } catch (error) {
      set({ loading: false, error: failMessage(error) })
    }
  },

  clearIpProfile: () => set({ ipProfile: null, error: null })
}))

export const useAppStore = create((set) => ({
  currentPage: 'dashboard',
  selectedIp: '',
  metricMode: 'bytes',

  setPage: (page) => set({ currentPage: page }),
  setMetricMode: (metricMode) => set({ metricMode }),
  openIpDetails: (ip) => set({ selectedIp: ip, currentPage: 'ip-details' }),
  goDashboard: () => set({ currentPage: 'dashboard' })
}))
