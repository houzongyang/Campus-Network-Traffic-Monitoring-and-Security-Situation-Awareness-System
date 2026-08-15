import { useEffect, useMemo } from 'react'
import ChartCard from '../components/ChartCard'
import MetricsCard from '../components/MetricsCard'
import { useAppStore, useFlowStore } from '../store'
import './IpDetails.css'

const chartTextStyle = { color: '#e7ecff', fontSize: 12 }
const formatDecimal = (value) => Number(value || 0).toLocaleString(undefined, {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
})

function IpDetails() {
  const { selectedIp: _selectedIp, metricMode, openIpDetails, goDashboard } = useAppStore()
  const selectedIp = _selectedIp || '192.168.1.100' // 临时默认值用于截图
  const { ipProfile, loading, error, fetchIpProfile, clearIpProfile } = useFlowStore()

  useEffect(() => {
    if (!selectedIp) {
      return undefined
    }

    fetchIpProfile(selectedIp)
    return () => {
      clearIpProfile()
    }
  }, [clearIpProfile, fetchIpProfile, selectedIp])

  const trendOption = useMemo(() => {
    const trend = ipProfile?.trend || []
    return {
      animationDurationUpdate: 220,
      animationEasingUpdate: 'linear',
      tooltip: {
        trigger: 'axis',
        valueFormatter: (value) => formatDecimal(value)
      },
      grid: { left: 20, right: 16, top: 30, bottom: 20, containLabel: true },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        axisLabel: chartTextStyle,
        data: trend.map((item) => new Date(item.time).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }))
      },
      yAxis: {
        type: 'value',
        axisLabel: { ...chartTextStyle, formatter: (value) => formatDecimal(value) },
        splitLine: { lineStyle: { color: 'rgba(122, 145, 200, 0.15)' } }
      },
      series: [
        {
          id: 'ip-trend-series',
          type: 'line',
          smooth: true,
          symbol: 'none',
          data: trend.map((item) => (metricMode === 'packets' ? item.packets : item.bytes / 1024 / 1024)),
          lineStyle: { width: 3, color: metricMode === 'packets' ? '#ff9f43' : '#5dd6ff' },
          areaStyle: { color: metricMode === 'packets' ? 'rgba(255, 159, 67, 0.18)' : 'rgba(93, 214, 255, 0.16)' }
        }
      ]
    }
  }, [ipProfile?.trend, metricMode])

  if (!selectedIp) {
    return (
      <div className="page-layout">
        <div className="empty-panel">
          <h2>尚未选择 IP</h2>
          <p>请从全景画像或流检索页面点击任意 IP，进入单 IP 通信画像页面。</p>
          <button type="button" onClick={goDashboard}>返回首页</button>
        </div>
      </div>
    )
  }

  const summary = ipProfile?.summary || {}
  const peers = ipProfile?.peers || []
  const flows = ipProfile?.flows || []

  return (
    <div className="page-layout">
      <section className="page-intro">
        <div>
          <p className="section-kicker">Single IP Drill-down</p>
          <h2>单 IP 详情 - {selectedIp}</h2>
          <p>展示通信对端、时间趋势和最近流记录，支持从宏观概览快速回溯到单主机层面。</p>
        </div>
        <div className="toolbar">
          <button type="button" onClick={() => fetchIpProfile(selectedIp)} disabled={loading}>刷新画像</button>
          <button type="button" className="secondary-btn" onClick={goDashboard}>返回全景</button>
        </div>
      </section>

      {error ? <div className="error-panel">{error}</div> : null}

      <section className="metrics-grid">
        <MetricsCard title="通信总字节" value={(summary.totalBytes || 0) / 1024 / 1024} unit="MB" accent="teal" />
        <MetricsCard title="通信总包数" value={summary.totalPackets || 0} unit="包" accent="amber" />
        <MetricsCard title="通信对端数量" value={summary.peerCount || 0} unit="个" accent="violet" />
        <MetricsCard title="流记录数量" value={summary.flowCount || 0} unit="条" accent="green" />
      </section>

      <section className="charts-grid ip-grid">
        <ChartCard
          title="对端变化趋势"
          subtitle={metricMode === 'packets' ? '按包数观察该 IP 的通信活跃度。' : '按字节观察该 IP 的通信趋势。'}
          option={trendOption}
          height={320}
        />
      </section>

      <section className="ip-columns">
        <section className="table-card">
          <div className="table-head">
            <div>
              <h3>通信对端排名</h3>
              <p>点击对端可继续下钻到下一层单 IP 画像。</p>
            </div>
          </div>
          <div className="table-scroll">
            <table className="flows-table">
              <thead>
                <tr>
                  <th>对端 IP</th>
                  <th>总字节</th>
                  <th>总包数</th>
                  <th>通信次数</th>
                  <th>最近时间</th>
                </tr>
              </thead>
              <tbody>
                {peers.length > 0 ? (
                  peers.map((peer) => (
                    <tr key={peer.peerIp}>
                      <td>
                        <button className="ip-link" type="button" onClick={() => openIpDetails(peer.peerIp)}>
                          {peer.peerIp}
                        </button>
                      </td>
                      <td>{formatDecimal((peer.bytes || 0) / 1024 / 1024)} MB</td>
                      <td>{formatDecimal(peer.packets)}</td>
                      <td>{formatDecimal(peer.flowCount)}</td>
                      <td>{new Date(peer.lastSeen).toLocaleString()}</td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan="5" className="empty-row">暂无通信对端数据</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </section>

        <section className="table-card">
          <div className="table-head">
            <div>
              <h3>最近流记录</h3>
              <p>帮助管理员回溯单 IP 在细粒度时间窗内的具体通信行为。</p>
            </div>
          </div>
          <div className="table-scroll">
            <table className="flows-table">
              <thead>
                <tr>
                  <th>时间</th>
                  <th>源 IP</th>
                  <th>目的 IP</th>
                  <th>端口</th>
                  <th>协议</th>
                  <th>总字节</th>
                </tr>
              </thead>
              <tbody>
                {flows.length > 0 ? (
                  flows.map((flow) => (
                    <tr key={flow.id}>
                      <td>{new Date(flow.timestamp).toLocaleString()}</td>
                      <td>{flow.srcIp}</td>
                      <td>{flow.dstIp}</td>
                      <td>{flow.srcPort} → {flow.dstPort}</td>
                      <td>{flow.appProtocol || 'Unknown'}</td>
                      <td>{formatDecimal((flow.bytesSent + flow.bytesRecv) / 1024)} KB</td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan="6" className="empty-row">暂无流记录</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </section>
      </section>
    </div>
  )
}

export default IpDetails
