import { useEffect, useMemo, useState } from 'react'
import ChartCard from '../components/ChartCard'
import MetricsCard from '../components/MetricsCard'
import { useSecurityStore } from '../store'
import './SecurityCenter.css'

const chartTextStyle = { color: '#e7ecff', fontSize: 12 }
const severityWeight = { low: 12, medium: 18, high: 26, critical: 34 }
const severityOrder = ['low', 'medium', 'high', 'critical']
const threatTypeOrder = ['DDoS', 'PortScan', 'SlowPortScan', 'WormPropagation', 'Phishing', 'DataExfiltration', 'ArpSpoofing']

const FILTERS_STORAGE_KEY = 'network_monitor_security_filters_v1'

const defaultFilters = {
  alertType: '',
  severity: '',
  srcIp: '',
  dstIp: '',
  keyword: '',
  minutesAgo: '-60',
  size: '100',
  sortBy: 'detectedTime',
  sortOrder: 'desc'
}

const loadPersistedFilters = () => {
  try {
    const raw = window.localStorage.getItem(FILTERS_STORAGE_KEY)
    if (!raw) {
      return defaultFilters
    }
    const parsed = JSON.parse(raw)
    return { ...defaultFilters, ...parsed }
  } catch (_error) {
    return defaultFilters
  }
}

const formatDecimal = (value) => Number(value || 0).toLocaleString(undefined, {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
})

const sortEntries = (source = {}, preferredOrder = []) => {
  const orderMap = new Map(preferredOrder.map((item, index) => [item, index]))
  return Object.entries(source).sort(([leftName], [rightName]) => {
    const leftIndex = orderMap.has(leftName) ? orderMap.get(leftName) : Number.MAX_SAFE_INTEGER
    const rightIndex = orderMap.has(rightName) ? orderMap.get(rightName) : Number.MAX_SAFE_INTEGER
    if (leftIndex !== rightIndex) {
      return leftIndex - rightIndex
    }
    return leftName.localeCompare(rightName)
  })
}

function SecurityCenter() {
  const {
    alerts,
    total,
    page,
    size,
    totalPages,
    threatStats,
    geoPoints,
    loading,
    error,
    fetchOverview,
    runThreatDetection,
    exportAlertsResult
  } = useSecurityStore()

  const [autoRefresh, setAutoRefresh] = useState(true)
  const [filters, setFilters] = useState(() => loadPersistedFilters())
  const [exporting, setExporting] = useState(false)

  useEffect(() => {
    window.localStorage.setItem(FILTERS_STORAGE_KEY, JSON.stringify(filters))
  }, [filters])

  const buildQuery = (customPage = 0) => ({
    alertType: filters.alertType || undefined,
    severity: filters.severity || undefined,
    srcIp: filters.srcIp || undefined,
    dstIp: filters.dstIp || undefined,
    keyword: filters.keyword || undefined,
    sortBy: filters.sortBy || 'detectedTime',
    sortOrder: filters.sortOrder || 'desc',
    minutesAgo: Number(filters.minutesAgo || -60),
    size: Number(filters.size || 100),
    page: customPage
  })

  useEffect(() => {
    fetchOverview(buildQuery(0), { silent: false })
  }, [fetchOverview])

  useEffect(() => {
    if (!autoRefresh) {
      return undefined
    }
    const timer = window.setInterval(() => {
      fetchOverview(buildQuery(page), { silent: true })
    }, 10000)
    return () => window.clearInterval(timer)
  }, [autoRefresh, fetchOverview, filters, page])

  const handleChange = (event) => {
    const { name, value } = event.target
    setFilters((previous) => ({ ...previous, [name]: value }))
  }

  const handleSearch = (event) => {
    event.preventDefault()
    fetchOverview(buildQuery(0), { silent: false })
  }

  const handleReset = () => {
    setFilters(defaultFilters)
    fetchOverview({
      minutesAgo: Number(defaultFilters.minutesAgo),
      size: Number(defaultFilters.size),
      sortBy: defaultFilters.sortBy,
      sortOrder: defaultFilters.sortOrder,
      page: 0
    }, { silent: false })
  }

  const handleRunDetection = async () => {
    await runThreatDetection()
  }

  const handlePageChange = (nextPage) => {
    fetchOverview(buildQuery(nextPage), { silent: false })
  }

  const handleExport = async () => {
    setExporting(true)
    const result = await exportAlertsResult()
    setExporting(false)
    if (result.status !== 'success') {
      window.alert(result.message || '导出失败')
    }
  }

  const typeEntries = useMemo(() => sortEntries(threatStats.byType || {}, threatTypeOrder), [threatStats.byType])
  const severityEntries = useMemo(() => sortEntries(threatStats.bySeverity || {}, severityOrder), [threatStats.bySeverity])

  const typeOption = useMemo(() => ({
    animationDurationUpdate: 220,
    animationEasingUpdate: 'linear',
    tooltip: {
      trigger: 'item',
      formatter: (params) => `${params.name}: ${formatDecimal(params.value)}`
    },
    legend: { bottom: 0, textStyle: chartTextStyle },
    series: [
      {
        id: 'threat-type-pie',
        type: 'pie',
        radius: ['36%', '68%'],
        center: ['50%', '46%'],
        label: { color: '#d6ddff' },
        itemStyle: { borderWidth: 2, borderColor: '#08101f' },
        data: typeEntries.map(([name, value]) => ({ name, value }))
      }
    ]
  }), [typeEntries])

  const severityOption = useMemo(() => ({
    animationDurationUpdate: 220,
    animationEasingUpdate: 'linear',
    tooltip: {
      trigger: 'axis',
      valueFormatter: (value) => formatDecimal(value)
    },
    grid: { left: 20, right: 16, top: 24, bottom: 18, containLabel: true },
    xAxis: {
      type: 'category',
      axisLabel: chartTextStyle,
      data: severityEntries.map(([name]) => name)
    },
    yAxis: {
      type: 'value',
      axisLabel: { ...chartTextStyle, formatter: (value) => formatDecimal(value) },
      splitLine: { lineStyle: { color: 'rgba(122, 145, 200, 0.15)' } }
    },
    series: [
      {
        id: 'severity-bar',
        type: 'bar',
        data: severityEntries.map(([, value]) => value),
        itemStyle: { color: '#ff6b6b', borderRadius: [12, 12, 0, 0] }
      }
    ]
  }), [severityEntries])

  const geoOption = useMemo(() => ({
    animationDurationUpdate: 220,
    animationEasingUpdate: 'linear',
    tooltip: {
      formatter: (params) => {
        const meta = params.data.meta
        return `${meta.city}<br/>${meta.alertType} / ${meta.severity}<br/>${meta.srcIp}<br/>(${formatDecimal(params.data.value[0])}, ${formatDecimal(params.data.value[1])})`
      }
    },
    grid: { left: 18, right: 18, top: 32, bottom: 28, containLabel: true },
    xAxis: {
      type: 'value',
      name: 'Longitude',
      axisLabel: { ...chartTextStyle, formatter: (value) => formatDecimal(value) },
      splitLine: { lineStyle: { color: 'rgba(122, 145, 200, 0.12)' } }
    },
    yAxis: {
      type: 'value',
      name: 'Latitude',
      axisLabel: { ...chartTextStyle, formatter: (value) => formatDecimal(value) },
      splitLine: { lineStyle: { color: 'rgba(122, 145, 200, 0.12)' } }
    },
    series: [
      {
        id: 'geo-scatter',
        type: 'scatter',
        symbolSize: (value) => value[2],
        itemStyle: { color: '#4dd7ff', shadowBlur: 16, shadowColor: 'rgba(77, 215, 255, 0.35)' },
        data: geoPoints.map((point) => ({
          value: [point.longitude, point.latitude, severityWeight[point.severity] || 12],
          meta: point
        }))
      }
    ]
  }), [geoPoints])

  return (
    <div className="page-layout">
      <section className="page-intro">
        <div>
          <p className="section-kicker">Security Situational Awareness</p>
          <h2>安全威胁感知</h2>
          <p>支持安全日志筛选、排序、分页和导出，筛选条件会自动持久化保存。</p>
        </div>
        <div className="toolbar">
          <label className="toggle">
            <input type="checkbox" checked={autoRefresh} onChange={(event) => setAutoRefresh(event.target.checked)} />
            <span>自动刷新</span>
          </label>
          <button onClick={handleRunDetection} disabled={loading}>运行威胁检测</button>
        </div>
      </section>

      <section className="search-panel">
        <form className="security-filter-form" onSubmit={handleSearch}>
          <label>
            <span>威胁类型</span>
            <input name="alertType" value={filters.alertType} onChange={handleChange} placeholder="如 DDoS" />
          </label>
          <label>
            <span>严重级别</span>
            <select name="severity" value={filters.severity} onChange={handleChange}>
              <option value="">全部</option>
              <option value="critical">critical</option>
              <option value="high">high</option>
              <option value="medium">medium</option>
              <option value="low">low</option>
            </select>
          </label>
          <label>
            <span>源 IP</span>
            <input name="srcIp" value={filters.srcIp} onChange={handleChange} placeholder="如 198.51.100" />
          </label>
          <label>
            <span>目的 IP</span>
            <input name="dstIp" value={filters.dstIp} onChange={handleChange} placeholder="如 10.10.40.11" />
          </label>
          <label>
            <span>关键词</span>
            <input name="keyword" value={filters.keyword} onChange={handleChange} placeholder="描述/城市/详情关键词" />
          </label>
          <label>
            <span>排序字段</span>
            <select name="sortBy" value={filters.sortBy} onChange={handleChange}>
              <option value="detectedTime">detectedTime</option>
              <option value="severity">severity</option>
              <option value="alertType">alertType</option>
              <option value="srcIp">srcIp</option>
              <option value="dstIp">dstIp</option>
            </select>
          </label>
          <label>
            <span>排序方向</span>
            <select name="sortOrder" value={filters.sortOrder} onChange={handleChange}>
              <option value="desc">desc</option>
              <option value="asc">asc</option>
            </select>
          </label>
          <label>
            <span>时间窗(分钟)</span>
            <input type="number" name="minutesAgo" value={filters.minutesAgo} onChange={handleChange} />
          </label>
          <label>
            <span>每页条数</span>
            <input type="number" name="size" value={filters.size} onChange={handleChange} min="10" max="500" />
          </label>
          <div className="search-actions">
            <button type="submit" disabled={loading}>{loading ? '查询中...' : '查询日志'}</button>
            <button type="button" className="secondary-btn" onClick={handleReset}>重置</button>
            <button type="button" className="secondary-btn" onClick={handleExport} disabled={exporting}>
              {exporting ? '导出中...' : '导出 CSV'}
            </button>
          </div>
        </form>
      </section>

      {error ? <div className="error-panel">{error}</div> : null}

      <section className="metrics-grid">
        <MetricsCard title="告警总数" value={threatStats.totalAlerts || 0} unit="条" accent="red" note="当前时间窗内汇总" />
        <MetricsCard title="高危 / 严重" value={(threatStats.bySeverity?.high || 0) + (threatStats.bySeverity?.critical || 0)} unit="条" accent="amber" note="优先处置对象" />
        <MetricsCard title="威胁类型数" value={Object.keys(threatStats.byType || {}).length} unit="类" accent="violet" note="覆盖扫描、钓鱼、蠕虫、DDoS 等" />
        <MetricsCard title="地理点位" value={geoPoints.length} unit="个" accent="teal" note="支持攻击源空间分布观察" />
      </section>

      <section className="charts-grid security-grid">
        <ChartCard title="威胁类型占比" subtitle="按告警类型统计。" option={typeOption} height={320} />
        <ChartCard title="严重级别分布" subtitle="帮助管理员快速判断优先级。" option={severityOption} height={320} />
        <ChartCard title="攻击源地理散点" subtitle="用经纬度散点替代地图底图，保留空间分布能力。" option={geoOption} height={360} />
      </section>

      <section className="table-card">
        <div className="table-head">
          <div>
            <h3>安全日志列表</h3>
            <p>共 {formatDecimal(total)} 条，当前第 {formatDecimal(page + 1)} / {formatDecimal(Math.max(totalPages, 1))} 页。</p>
          </div>
        </div>
        <div className="table-scroll">
          <table className="flows-table">
            <thead>
              <tr>
                <th>时间</th>
                <th>威胁类型</th>
                <th>源 IP</th>
                <th>目标 IP</th>
                <th>严重级别</th>
                <th>地点</th>
                <th>描述</th>
              </tr>
            </thead>
            <tbody>
              {alerts.length > 0 ? (
                alerts.map((alert) => (
                  <tr key={alert.id}>
                    <td>{new Date(alert.detectedTime).toLocaleString()}</td>
                    <td>{alert.alertType}</td>
                    <td>{alert.srcIp}</td>
                    <td>{alert.dstIp || '-'}</td>
                    <td><span className={`severity-chip severity-${alert.severity}`}>{alert.severity}</span></td>
                    <td>{alert.city || '-'}</td>
                    <td>{alert.description}</td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan="7" className="empty-row">暂无告警</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
        <div className="pagination-bar">
          <button type="button" className="secondary-btn" disabled={loading || page <= 0} onClick={() => handlePageChange(page - 1)}>
            上一页
          </button>
          <span>第 {formatDecimal(page + 1)} / {formatDecimal(Math.max(totalPages, 1))} 页</span>
          <button
            type="button"
            className="secondary-btn"
            disabled={loading || page + 1 >= totalPages}
            onClick={() => handlePageChange(page + 1)}
          >
            下一页
          </button>
          <span>每页 {formatDecimal(size)} 条</span>
        </div>
      </section>
    </div>
  )
}

export default SecurityCenter
