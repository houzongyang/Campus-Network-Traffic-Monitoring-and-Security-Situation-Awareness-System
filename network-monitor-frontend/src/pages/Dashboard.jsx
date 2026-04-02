import { useCallback, useEffect, useMemo, useState } from 'react'
import ChartCard from '../components/ChartCard'
import MetricsCard from '../components/MetricsCard'
import TopFlowsTable from '../components/TopFlowsTable'
import { dashboardAPI } from '../api/client'
import { useAppStore, useDashboardStore } from '../store'
import './Dashboard.css'

const chartTextStyle = { color: '#e7ecff', fontSize: 12 }
const defaultHierarchy = { level: 'region', nodes: [] }

const formatDecimal = (value) => Number(value || 0).toLocaleString(undefined, {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
})

const sortEntries = (source = {}) =>
  Object.entries(source).sort(([leftName], [rightName]) => leftName.localeCompare(rightName))

const regionLevelLabel = (level) => {
  switch (level) {
    case 'building':
      return '楼宇层'
    case 'switch':
      return '交换机层'
    case 'port':
      return '端口层'
    default:
      return '区域层'
  }
}

const wsStateLabel = (state) => {
  switch (state) {
    case 'connected':
      return '已连接'
    case 'connecting':
      return '连接中'
    case 'reconnecting':
      return '重连中'
    default:
      return '未连接'
  }
}

function Dashboard() {
  const {
    metrics,
    topFlows,
    regionTraffic,
    throughputTrend,
    wsConnected,
    wsState,
    wsReconnectStreak,
    wsReconnectTotal,
    loading,
    error,
    fetchOverview,
    connectRealtimeMetrics,
    disconnectRealtimeMetrics
  } = useDashboardStore()

  const { metricMode, setMetricMode, openIpDetails } = useAppStore()

  const [autoRefresh, setAutoRefresh] = useState(true)
  const [regionLoading, setRegionLoading] = useState(false)
  const [hierarchy, setHierarchy] = useState(defaultHierarchy)
  const [drillContext, setDrillContext] = useState({ region: '', building: '', switchId: '' })

  const loadRegionHierarchy = useCallback(async (context, options = {}) => {
    const { silent = false } = options
    if (!silent) {
      setRegionLoading(true)
    }

    try {
      const data = await dashboardAPI.getRegionHierarchy({
        minutesAgo: -30,
        metric: metricMode,
        region: context.region || undefined,
        building: context.building || undefined,
        switchId: context.switchId || undefined
      })
      if (data.status === 'success') {
        setHierarchy({ level: data.level || 'region', nodes: data.nodes || [] })
      }
    } finally {
      if (!silent) {
        setRegionLoading(false)
      }
    }
  }, [metricMode])

  useEffect(() => {
    fetchOverview(metricMode)
    loadRegionHierarchy(drillContext)
  }, [fetchOverview, loadRegionHierarchy, metricMode])

  useEffect(() => {
    loadRegionHierarchy(drillContext)
  }, [drillContext, loadRegionHierarchy])

  useEffect(() => {
    if (!autoRefresh) {
      disconnectRealtimeMetrics()
      return undefined
    }

    connectRealtimeMetrics()
    const timer = window.setInterval(() => {
      fetchOverview(metricMode, { silent: true })
      loadRegionHierarchy(drillContext, { silent: true })
    }, 15000)

    return () => {
      window.clearInterval(timer)
      disconnectRealtimeMetrics()
    }
  }, [autoRefresh, connectRealtimeMetrics, disconnectRealtimeMetrics, drillContext, fetchOverview, loadRegionHierarchy, metricMode])

  const appDistribution = metricMode === 'packets'
    ? metrics.appDistributionPackets || {}
    : metrics.appDistributionBytes || {}

  const protocolEntries = useMemo(() => sortEntries(appDistribution), [appDistribution])
  const hierarchyEntries = hierarchy.nodes || []
  const regionHeatEntries = useMemo(() => sortEntries(regionTraffic || {}), [regionTraffic])

  const throughputOption = useMemo(() => ({
    animationDurationUpdate: 220,
    animationEasingUpdate: 'linear',
    tooltip: {
      trigger: 'axis',
      valueFormatter: (value) => formatDecimal(value)
    },
    legend: { textStyle: chartTextStyle },
    grid: { left: 24, right: 16, top: 48, bottom: 24, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      axisLabel: chartTextStyle,
      data: throughputTrend.map((item) => new Date(item.time).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }))
    },
    yAxis: [
      {
        type: 'value',
        name: 'Mbps',
        axisLabel: { ...chartTextStyle, formatter: (value) => formatDecimal(value) },
        splitLine: { lineStyle: { color: 'rgba(122, 145, 200, 0.15)' } }
      },
      {
        type: 'value',
        name: 'PPS',
        axisLabel: { ...chartTextStyle, formatter: (value) => formatDecimal(value) },
        splitLine: { show: false }
      }
    ],
    series: [
      {
        id: 'throughput-series',
        name: '吞吐量',
        type: 'line',
        smooth: true,
        symbol: 'none',
        data: throughputTrend.map((item) => item.throughputMbps),
        lineStyle: { color: '#53d7c2', width: 3 },
        areaStyle: { color: 'rgba(83, 215, 194, 0.18)' }
      },
      {
        id: 'pps-series',
        name: '包速率',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        symbol: 'none',
        data: throughputTrend.map((item) => item.pps),
        lineStyle: { color: '#ffd166', width: 2 }
      }
    ]
  }), [throughputTrend])

  const protocolOption = useMemo(() => ({
    animationDurationUpdate: 220,
    animationEasingUpdate: 'linear',
    tooltip: {
      trigger: 'item',
      formatter: (params) => `${params.name}: ${formatDecimal(params.value)}`
    },
    legend: { bottom: 0, textStyle: chartTextStyle },
    series: [
      {
        id: 'protocol-pie',
        name: '应用协议',
        type: 'pie',
        radius: ['38%', '68%'],
        center: ['50%', '46%'],
        label: { color: '#cfd7ff' },
        itemStyle: { borderColor: '#08101f', borderWidth: 2 },
        data: protocolEntries.map(([name, value]) => ({ name, value }))
      }
    ]
  }), [protocolEntries])

  const regionOption = useMemo(() => ({
    animationDurationUpdate: 220,
    animationEasingUpdate: 'linear',
    tooltip: {
      trigger: 'axis',
      valueFormatter: (value) => formatDecimal(value)
    },
    grid: { left: 20, right: 16, top: 36, bottom: 16, containLabel: true },
    xAxis: {
      type: 'value',
      axisLabel: { ...chartTextStyle, formatter: (value) => formatDecimal(value) },
      splitLine: { lineStyle: { color: 'rgba(122, 145, 200, 0.15)' } }
    },
    yAxis: {
      type: 'category',
      axisLabel: chartTextStyle,
      data: hierarchyEntries.map((item) => item.name)
    },
    series: [
      {
        id: 'region-hierarchy-bar',
        type: 'bar',
        data: hierarchyEntries.map((item) => metricMode === 'packets' ? item.packets : item.bytes / 1024 / 1024),
        borderRadius: 999,
        itemStyle: {
          color: metricMode === 'packets' ? '#ff8c69' : '#6ea8fe'
        }
      }
    ]
  }), [hierarchyEntries, metricMode])

  const maxHeatValue = useMemo(() => {
    if (!regionHeatEntries.length) {
      return 0
    }
    return Math.max(
      ...regionHeatEntries.map(([, value]) => (metricMode === 'packets' ? value.packets : value.bytes / 1024 / 1024))
    )
  }, [metricMode, regionHeatEntries])

  const heatmapOption = useMemo(() => ({
    animationDurationUpdate: 220,
    animationEasingUpdate: 'linear',
    tooltip: {
      formatter: (params) => `${params.name}: ${formatDecimal(params.value[2])}`
    },
    grid: { left: 18, right: 16, top: 36, bottom: 24, containLabel: true },
    xAxis: {
      type: 'category',
      axisLabel: { ...chartTextStyle, interval: 0, rotate: 25 },
      data: regionHeatEntries.map(([name]) => name)
    },
    yAxis: {
      type: 'category',
      axisLabel: chartTextStyle,
      data: ['流量热度']
    },
    visualMap: {
      dimension: 2,
      min: 0,
      max: maxHeatValue || 1,
      calculable: true,
      orient: 'horizontal',
      left: 'center',
      bottom: 0,
      textStyle: chartTextStyle,
      inRange: {
        color: ['#0f2c6e', '#1f6fff', '#34d399', '#f59e0b', '#ef4444']
      }
    },
    series: [
      {
        type: 'heatmap',
        encode: {
          x: 0,
          y: 1,
          value: 2
        },
        itemStyle: {
          borderColor: 'rgba(255,255,255,0.08)',
          borderWidth: 1
        },
        emphasis: {
          itemStyle: {
            borderColor: 'rgba(255,255,255,0.35)',
            borderWidth: 1
          }
        },
        data: regionHeatEntries.map(([name, value], index) => [
          index,
          0,
          metricMode === 'packets' ? value.packets : value.bytes / 1024 / 1024,
          name
        ]),
        label: {
          show: true,
          color: '#f4f8ff',
          formatter: (params) => formatDecimal(params.value[2])
        }
      }
    ]
  }), [maxHeatValue, metricMode, regionHeatEntries])

  const handleHierarchyClick = (event) => {
    const target = hierarchyEntries[event?.dataIndex]
    if (!target || !target.drillable) {
      return
    }

    if (hierarchy.level === 'region') {
      setDrillContext({ region: target.key, building: '', switchId: '' })
      return
    }
    if (hierarchy.level === 'building') {
      setDrillContext((previous) => ({ ...previous, building: target.key, switchId: '' }))
      return
    }
    if (hierarchy.level === 'switch') {
      setDrillContext((previous) => ({ ...previous, switchId: target.key }))
    }
  }

  const handleDrillBack = () => {
    if (drillContext.switchId) {
      setDrillContext((previous) => ({ ...previous, switchId: '' }))
      return
    }
    if (drillContext.building) {
      setDrillContext((previous) => ({ ...previous, building: '' }))
      return
    }
    if (drillContext.region) {
      setDrillContext({ region: '', building: '', switchId: '' })
    }
  }

  const drillPathLabel = [
    drillContext.region ? `区域:${drillContext.region}` : null,
    drillContext.building ? `楼宇:${drillContext.building}` : null,
    drillContext.switchId ? `交换机:${drillContext.switchId}` : null
  ].filter(Boolean).join(' / ')

  return (
    <div className="page-layout">
      <section className="page-intro">
        <div>
          <p className="section-kicker">Campus-wide Overview</p>
          <h2>实时全景画像</h2>
          <p>核心指标使用 WebSocket 秒级推送，趋势与大流按周期增量刷新，支持区域多层级下钻。</p>
        </div>
        <div className="toolbar">
          <div className="ws-status-group">
            <span className={`ws-badge ws-${wsState}`}>
              {wsStateLabel(wsState)}
            </span>
            <span className="ws-count">
              重连次数 {formatDecimal(wsReconnectTotal)}{wsReconnectStreak > 0 ? `（当前第 ${formatDecimal(wsReconnectStreak)} 次）` : ''}
            </span>
          </div>
          <div className="segmented">
            <button className={metricMode === 'bytes' ? 'active' : ''} onClick={() => setMetricMode('bytes')}>字节视图</button>
            <button className={metricMode === 'packets' ? 'active' : ''} onClick={() => setMetricMode('packets')}>包数视图</button>
          </div>
          <label className="toggle">
            <input type="checkbox" checked={autoRefresh} onChange={(event) => setAutoRefresh(event.target.checked)} />
            <span>自动刷新</span>
          </label>
          <button onClick={() => { fetchOverview(metricMode); loadRegionHierarchy(drillContext) }}>立即刷新</button>
        </div>
      </section>

      {error ? <div className="error-panel">{error}</div> : null}

      <section className="metrics-grid">
        <MetricsCard title="实时吞吐量" value={metrics.throughputMbps || 0} unit="Mbps" accent="teal" note="校园出口秒级吞吐" />
        <MetricsCard title="包速率" value={metrics.pps || 0} unit="PPS" accent="amber" note="支持 Bytes / Packets 双维观测" />
        <MetricsCard title="活跃 IP 数" value={metrics.activeIps || 0} unit="个" accent="violet" note="全网双向通信去重统计" />
        <MetricsCard
          title="监控状态"
          value={loading ? '采集中' : (wsConnected ? '稳定' : '轮询')}
          unit=""
          accent="green"
          note={metrics.timestamp ? `更新时间 ${new Date(metrics.timestamp).toLocaleTimeString()}` : '等待数据'}
        />
      </section>

      <section className="charts-grid">
        <ChartCard title="链路吞吐变化" subtitle="同时展示 Mbps 与 PPS，适合识别微突发与异常尖峰。" option={throughputOption} height={320} />
        <ChartCard
          title="应用协议分布"
          subtitle={metricMode === 'packets' ? '按包数统计业务构成。' : '按字节量统计业务构成。'}
          option={protocolOption}
          height={320}
        />
        <ChartCard
          title={`区域 / 楼宇透视（${regionLevelLabel(hierarchy.level)}）`}
          subtitle={drillPathLabel || (regionLoading ? '加载中...' : '点击柱条可下钻到下一层。')}
          option={regionOption}
          onChartClick={handleHierarchyClick}
          height={340}
        />
        <ChartCard
          title="区域热力图"
          subtitle={metricMode === 'packets' ? '按包数显示区域热度分布。' : '按流量显示区域热度分布。'}
          option={heatmapOption}
          height={320}
        />
      </section>

      <div className="toolbar">
        <button type="button" className="secondary-btn" onClick={handleDrillBack} disabled={!drillPathLabel}>
          返回上一级
        </button>
        <span>{drillPathLabel || '当前为根层级：全区域'}</span>
      </div>

      <TopFlowsTable flows={topFlows} metricMode={metricMode} onSelectIp={openIpDetails} />
    </div>
  )
}

export default Dashboard
