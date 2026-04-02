import { useState } from 'react'
import { useAppStore, useFlowStore } from '../store'
import './FlowSearch.css'

const initialSearchParams = {
  srcIp: '',
  dstIp: '',
  srcCidr: '',
  dstCidr: '',
  srcPort: '',
  dstPort: '',
  dstPortRange: '',
  protocol: '',
  appProtocol: '',
  startTime: '',
  endTime: '',
  minutesAgo: '-30',
  size: '100'
}

const formatDecimal = (value) => Number(value || 0).toLocaleString(undefined, {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
})

const parseNumber = (value) => (value === '' ? undefined : Number(value))

function FlowSearch() {
  const {
    searchResults,
    total,
    page,
    size,
    totalPages,
    lastSearchParams,
    loading,
    error,
    searchFlows,
    exportSearchResult
  } = useFlowStore()
  const { openIpDetails } = useAppStore()
  const [searchParams, setSearchParams] = useState(initialSearchParams)
  const [exporting, setExporting] = useState(false)

  const handleChange = (event) => {
    const { name, value } = event.target
    setSearchParams((previous) => ({ ...previous, [name]: value }))
  }

  const buildRequestParams = (customPage = 0) => ({
    ...searchParams,
    srcPort: parseNumber(searchParams.srcPort),
    dstPort: parseNumber(searchParams.dstPort),
    minutesAgo: Number(searchParams.minutesAgo),
    page: customPage,
    size: Number(searchParams.size || 100)
  })

  const handleSubmit = (event) => {
    event.preventDefault()
    searchFlows(buildRequestParams(0))
  }

  const handleReset = () => {
    setSearchParams(initialSearchParams)
  }

  const handlePageChange = (nextPage) => {
    const request = lastSearchParams ? { ...lastSearchParams, page: nextPage } : buildRequestParams(nextPage)
    searchFlows(request)
  }

  const handleExport = async () => {
    setExporting(true)
    const result = await exportSearchResult()
    setExporting(false)
    if (result.status !== 'success') {
      window.alert(result.message || '导出失败')
    }
  }

  const hasResults = searchResults.length > 0

  return (
    <div className="page-layout">
      <section className="page-intro">
        <div>
          <p className="section-kicker">Flow Search</p>
          <h2>微观流级检索</h2>
          <p>支持源/目的 IP、CIDR、端口范围、协议与时间范围组合检索，并支持分页与 CSV 导出。</p>
        </div>
      </section>

      <section className="search-panel">
        <form className="search-form" onSubmit={handleSubmit}>
          <label>
            <span>源 IP</span>
            <input name="srcIp" value={searchParams.srcIp} onChange={handleChange} placeholder="如 10.10.10.11 或 10.10.*" />
          </label>
          <label>
            <span>目的 IP</span>
            <input name="dstIp" value={searchParams.dstIp} onChange={handleChange} placeholder="如 203.0.113.77" />
          </label>
          <label>
            <span>源 CIDR</span>
            <input name="srcCidr" value={searchParams.srcCidr} onChange={handleChange} placeholder="如 10.10.0.0/16" />
          </label>
          <label>
            <span>目的 CIDR</span>
            <input name="dstCidr" value={searchParams.dstCidr} onChange={handleChange} placeholder="如 203.0.113.0/24" />
          </label>
          <label>
            <span>源端口</span>
            <input type="number" name="srcPort" value={searchParams.srcPort} onChange={handleChange} placeholder="1024" />
          </label>
          <label>
            <span>目的端口</span>
            <input type="number" name="dstPort" value={searchParams.dstPort} onChange={handleChange} placeholder="443" />
          </label>
          <label>
            <span>目的端口范围</span>
            <input name="dstPortRange" value={searchParams.dstPortRange} onChange={handleChange} placeholder="80-443" />
          </label>
          <label>
            <span>协议</span>
            <select name="protocol" value={searchParams.protocol} onChange={handleChange}>
              <option value="">全部</option>
              <option value="TCP">TCP</option>
              <option value="UDP">UDP</option>
              <option value="ICMP">ICMP</option>
            </select>
          </label>
          <label>
            <span>应用协议</span>
            <select name="appProtocol" value={searchParams.appProtocol} onChange={handleChange}>
              <option value="">全部</option>
              <option value="HTTP">HTTP</option>
              <option value="HTTPS">HTTPS</option>
              <option value="DNS">DNS</option>
              <option value="SSH">SSH</option>
              <option value="SMTP">SMTP</option>
              <option value="SMTPS">SMTPS</option>
              <option value="IMAP">IMAP</option>
              <option value="IMAPS">IMAPS</option>
              <option value="MySQL">MySQL</option>
              <option value="PostgreSQL">PostgreSQL</option>
              <option value="RDP">RDP</option>
              <option value="SMB">SMB</option>
            </select>
          </label>
          <label>
            <span>开始时间</span>
            <input type="datetime-local" name="startTime" value={searchParams.startTime} onChange={handleChange} />
          </label>
          <label>
            <span>结束时间</span>
            <input type="datetime-local" name="endTime" value={searchParams.endTime} onChange={handleChange} />
          </label>
          <label>
            <span>相对时间(分钟)</span>
            <input type="number" name="minutesAgo" value={searchParams.minutesAgo} onChange={handleChange} />
          </label>
          <label>
            <span>每页条数</span>
            <input type="number" name="size" value={searchParams.size} onChange={handleChange} min="10" max="500" />
          </label>
          <div className="search-actions">
            <button type="submit" disabled={loading}>{loading ? '检索中...' : '开始检索'}</button>
            <button type="button" className="secondary-btn" onClick={handleReset}>重置条件</button>
          </div>
        </form>
      </section>

      {error ? <div className="error-panel">{error}</div> : null}

      <section className="table-card">
        <div className="table-head">
          <div>
            <h3>流记录列表</h3>
            <p>共 {formatDecimal(total)} 条，当前第 {formatDecimal(page + 1)} / {formatDecimal(Math.max(totalPages, 1))} 页。</p>
          </div>
          <div className="toolbar">
            <button type="button" onClick={handleExport} disabled={exporting || !lastSearchParams}>
              {exporting ? '导出中...' : '导出 CSV'}
            </button>
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
                <th>应用协议</th>
                <th>区域</th>
                <th>总字节</th>
                <th>总包数</th>
              </tr>
            </thead>
            <tbody>
              {hasResults ? (
                searchResults.map((flow) => (
                  <tr key={flow.id}>
                    <td>{new Date(flow.timestamp).toLocaleString()}</td>
                    <td>
                      <button className="ip-link" type="button" onClick={() => openIpDetails(flow.srcIp)}>
                        {flow.srcIp}
                      </button>
                    </td>
                    <td>
                      <button className="ip-link" type="button" onClick={() => openIpDetails(flow.dstIp)}>
                        {flow.dstIp}
                      </button>
                    </td>
                    <td>{flow.srcPort} → {flow.dstPort}</td>
                    <td>{flow.protocol || '-'}</td>
                    <td>{flow.appProtocol || 'Unknown'}</td>
                    <td>{flow.region}</td>
                    <td>{formatDecimal((flow.bytesSent + flow.bytesRecv) / 1024 / 1024)} MB</td>
                    <td>{formatDecimal(flow.packetsSent + flow.packetsRecv)}</td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan="9" className="empty-row">暂无检索结果</td>
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

export default FlowSearch
