import { Suspense, lazy, useEffect } from 'react'
import { useAppStore } from './store'
import './App.css'

const Dashboard = lazy(() => import('./pages/Dashboard'))
const SecurityCenter = lazy(() => import('./pages/SecurityCenter'))
const FlowSearch = lazy(() => import('./pages/FlowSearch'))
const IpDetails = lazy(() => import('./pages/IpDetails'))

function App() {
  const { currentPage, selectedIp, setPage } = useAppStore()

  useEffect(() => {
    const pageName = currentPage === 'ip-details' && selectedIp ? `IP详情 ${selectedIp}` : currentPage
    document.title = `智慧校园网络态势感知系统 - ${pageName}`
  }, [currentPage, selectedIp])

  const renderPage = () => {
    switch (currentPage) {
      case 'security':
        return <SecurityCenter />
      case 'flows':
        return <FlowSearch />
      case 'ip-details':
        return <IpDetails />
      default:
        return <Dashboard />
    }
  }

  return (
    <div className="app-shell">
      <header className="app-header">
        <div className="brand-block">
          <p className="brand-kicker">Smart Campus Network Intelligence</p>
          <h1>面向智慧校园的细粒度网络流量监控与安全态势感知系统</h1>
        </div>
        <nav className="top-nav">
          <button className={currentPage === 'dashboard' ? 'active' : ''} onClick={() => setPage('dashboard')}>
            全景画像
          </button>
          <button className={currentPage === 'security' ? 'active' : ''} onClick={() => setPage('security')}>
            安全态势
          </button>
          <button className={currentPage === 'flows' ? 'active' : ''} onClick={() => setPage('flows')}>
            微观流检索
          </button>
          <button
            className={currentPage === 'ip-details' ? 'active' : ''}
            onClick={() => setPage(selectedIp ? 'ip-details' : 'dashboard')}
            disabled={!selectedIp}
          >
            单 IP 详情
          </button>
        </nav>
      </header>

      <main className="page-shell">
        <Suspense fallback={<div className="empty-panel">页面加载中...</div>}>
          {renderPage()}
        </Suspense>
      </main>
    </div>
  )
}

export default App
