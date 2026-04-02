import { useEffect, useRef } from 'react'
import * as echarts from 'echarts'
import './ChartCard.css'

function ChartCard({ title, subtitle, option, height = 320, onChartClick }) {
  const chartRef = useRef(null)
  const chartInstanceRef = useRef(null)

  useEffect(() => {
    if (!chartRef.current) {
      return undefined
    }

    if (!chartInstanceRef.current) {
      chartInstanceRef.current = echarts.init(chartRef.current)
    }

    const chart = chartInstanceRef.current
    const handleResize = () => chart.resize()
    window.addEventListener('resize', handleResize)

    return () => {
      window.removeEventListener('resize', handleResize)
      chart.dispose()
      chartInstanceRef.current = null
    }
  }, [])

  useEffect(() => {
    if (!chartInstanceRef.current) {
      return
    }

    chartInstanceRef.current.setOption(option, {
      notMerge: false,
      lazyUpdate: true,
      silent: true
    })
  }, [option])

  useEffect(() => {
    if (!chartInstanceRef.current) {
      return
    }
    const chart = chartInstanceRef.current
    chart.off('click')
    if (typeof onChartClick === 'function') {
      chart.on('click', onChartClick)
    }
    return () => {
      chart.off('click')
    }
  }, [onChartClick])

  return (
    <section className="chart-card">
      <div className="chart-card-head">
        <div>
          <h3>{title}</h3>
          {subtitle ? <p>{subtitle}</p> : null}
        </div>
      </div>
      <div className="chart-surface" ref={chartRef} style={{ height }} />
    </section>
  )
}

export default ChartCard
