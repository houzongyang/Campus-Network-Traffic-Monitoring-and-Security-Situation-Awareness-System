import './TopFlowsTable.css'

const formatDecimal = (value) => Number(value || 0).toLocaleString(undefined, {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
})

function TopFlowsTable({ flows, metricMode, onSelectIp }) {
  const metricLabel = metricMode === 'packets' ? '总包数' : '总字节数'

  return (
    <section className="table-card">
      <div className="table-head">
        <div>
          <h3>Top-K 大流监控</h3>
          <p>支持从宏观异常点下钻到单 IP 详情页面。</p>
        </div>
      </div>
      <div className="table-scroll">
        <table className="flows-table">
          <thead>
            <tr>
              <th>源 IP</th>
              <th>目的 IP</th>
              <th>端口</th>
              <th>应用协议</th>
              <th>区域</th>
              <th>{metricLabel}</th>
              <th>总包数</th>
              <th>持续时间(s)</th>
            </tr>
          </thead>
          <tbody>
            {flows.length > 0 ? (
              flows.map((flow) => (
                <tr key={flow.id}>
                  <td>
                    <button className="ip-link" type="button" onClick={() => onSelectIp(flow.srcIp)}>
                      {flow.srcIp}
                    </button>
                  </td>
                  <td>
                    <button className="ip-link" type="button" onClick={() => onSelectIp(flow.dstIp)}>
                      {flow.dstIp}
                    </button>
                  </td>
                  <td>{flow.srcPort} → {flow.dstPort}</td>
                  <td>{flow.appProtocol || 'Unknown'}</td>
                  <td>{flow.region}</td>
                  <td>{metricMode === 'packets' ? formatDecimal(flow.packets) : `${formatDecimal((flow.bytes || 0) / 1024 / 1024)} MB`}</td>
                  <td>{formatDecimal(flow.packets)}</td>
                  <td>{formatDecimal(flow.durationSeconds)}</td>
                </tr>
              ))
            ) : (
              <tr>
                <td colSpan="8" className="empty-row">暂无流量数据</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </section>
  )
}

export default TopFlowsTable
