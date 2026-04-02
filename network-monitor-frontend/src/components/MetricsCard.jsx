import './MetricsCard.css'

function MetricsCard({ title, value, unit, accent = 'blue', note }) {
  const normalizedValue = typeof value === 'number'
    ? value.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
    : value

  return (
    <article className={`metrics-card accent-${accent}`}>
      <p className="metrics-label">{title}</p>
      <div className="metrics-main">
        <span className="metrics-value">{normalizedValue}</span>
        {unit ? <span className="metrics-unit">{unit}</span> : null}
      </div>
      {note ? <p className="metrics-note">{note}</p> : null}
    </article>
  )
}

export default MetricsCard
