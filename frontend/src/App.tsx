import { FormEvent, useMemo, useState } from 'react'

type NavItem = 'CONTROL' | 'AGENTS' | 'RUNS' | 'ASSETS' | 'OBSERVE'

const navItems: NavItem[] = ['CONTROL', 'AGENTS', 'RUNS', 'ASSETS', 'OBSERVE']

const telemetry = [
  ['ACTIVE RUNS', '24', '+8.3%', 'up'],
  ['TOKENS / MIN', '18.4K', 'NOMINAL', 'flat'],
  ['TOOL SUCCESS', '99.2%', '+0.4%', 'up'],
  ['P95 LATENCY', '1.84s', '−0.31s', 'down'],
]

const initialEvents = [
  ['12:41:03.221', 'MODEL', 'qwen-plus · generation stream attached'],
  ['12:41:05.992', 'TOOL', 'crm.search_customer · completed in 281ms'],
  ['12:41:06.334', 'MEMORY', 'context compression checkpoint skipped'],
  ['12:41:07.018', 'OUTPUT', 'response channel flushed · 1,248 tokens'],
]

function SparkLine() {
  return <svg className="spark" viewBox="0 0 250 56" aria-label="token trend">
    <defs><linearGradient id="area" x1="0" x2="0" y1="0" y2="1"><stop stopColor="#2489f4" stopOpacity=".32"/><stop offset="1" stopColor="#2489f4" stopOpacity="0"/></linearGradient></defs>
    <path d="M0 49 L17 42 L31 45 L47 30 L63 37 L79 22 L95 30 L111 18 L127 24 L143 11 L159 20 L175 15 L191 27 L207 17 L223 21 L250 4 V56 H0Z" fill="url(#area)" />
    <path d="M0 49 L17 42 L31 45 L47 30 L63 37 L79 22 L95 30 L111 18 L127 24 L143 11 L159 20 L175 15 L191 27 L207 17 L223 21 L250 4" fill="none" stroke="#2489f4" strokeWidth="2" />
  </svg>
}

export default function App() {
  const [activeNav, setActiveNav] = useState<NavItem>('CONTROL')
  const [paused, setPaused] = useState(false)
  const [message, setMessage] = useState('')
  const [events, setEvents] = useState(initialEvents)
  const [pulse, setPulse] = useState(0)
  const status = paused ? 'PAUSED' : 'LIVE'
  const date = useMemo(() => new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' }).format(new Date()), [pulse])

  const sendMessage = (event: FormEvent) => {
    event.preventDefault()
    const text = message.trim()
    if (!text || paused) return
    setEvents(current => [[new Date().toTimeString().slice(0, 8) + '.000', 'INPUT', text], ...current].slice(0, 7))
    setMessage('')
    setPulse(value => value + 1)
  }

  return <main className="app-shell">
    <div className="noise" />
    <aside className="rail">
      <div className="brand"><span className="brand-mark">ok</span><span>AGENT</span></div>
      <div className="rail-line" />
      <nav>{navItems.map((item, index) => <button key={item} onClick={() => setActiveNav(item)} className={activeNav === item ? 'nav-item active' : 'nav-item'}><span className="nav-index">0{index + 1}</span>{item}</button>)}</nav>
      <div className="rail-bottom"><span className="node-dot" /> CLUSTER / CN-SH-01<br/><span className="muted">BUILD 0.1.0 · {date}</span></div>
    </aside>

    <section className="mission">
      <header className="topbar">
        <div><p className="eyebrow">{activeNav} / MISSION CONTROL</p><h1>指挥你的 <em>智能体集群</em></h1></div>
        <div className="operator"><span className="avatar">N</span><div><b>NEASON</b><small>PLATFORM OPERATOR</small></div><span className="chevron">⌄</span></div>
      </header>

      <section className="fleet-strip">
        <div className="fleet-meta"><span className="pulse-dot" /> RUNTIME FLEET <b>06 / 06</b><small>ALL SYSTEMS NOMINAL</small></div>
        <div className="fleet-track">{[0, 1, 2, 3, 4, 5].map((unit) => <span key={unit} className={`fleet-unit ${unit === 3 ? 'busy' : ''}`}><i />R-{String(unit + 1).padStart(2, '0')}</span>)}</div>
        <button className="outline-button">VIEW FLEET <span>↗</span></button>
      </section>

      <section className="metrics">{telemetry.map(([label, value, change, direction], index) => <article className="metric" key={label} style={{ animationDelay: `${index * 70}ms` }}><span>{label}</span><strong>{value}</strong><small className={direction}>{change}</small>{index === 1 && <SparkLine />}</article>)}</section>

      <section className="core-grid">
        <article className="agent-card panel">
          <div className="panel-top"><span className="eyebrow">PRIMARY AGENT / PROD</span><button className={paused ? 'state-button paused' : 'state-button'} onClick={() => setPaused(!paused)}><i /> {status}</button></div>
          <div className="agent-identity"><div className="orb"><span /><span /><b>◈</b></div><div><h2>客户服务中枢</h2><p>customer-service · snapshot <b>8f1a09c</b></p></div></div>
          <div className="signal-grid"><div><span>MODEL ROUTE</span><b>QWEN-PLUS</b></div><div><span>MEMORY SCOPE</span><b>USER / 14D</b></div><div><span>TOOL POLICY</span><b>SAFE-CRM V2</b></div><div><span>SESSION LOAD</span><b>62%</b></div></div>
          <div className="deployment"><span>DEPLOYMENT <b>PROD · 100%</b></span><div><i /><i /><i /><i /><i /><i /><i /><i /><i /><i /></div><small>release 2026.08.14.7 · healthy</small></div>
          <div className="card-actions"><button className="primary-button" onClick={() => setPulse(value => value + 1)}>OPEN CONSOLE <span>→</span></button><button className="ghost-button">CONFIGURE</button></div>
        </article>

        <article className="stream-card panel">
          <div className="panel-top"><div><span className="eyebrow">LIVE EVENT STREAM</span><h3>运行遥测</h3></div><span className="stream-live"><i /> STREAMING</span></div>
          <div className="event-list">{events.map(([time, type, text], index) => <div className="event" key={`${time}-${index}`}><time>{time}</time><span className={`event-type ${type.toLowerCase()}`}>{type}</span><p>{text}</p></div>)}</div>
          <form className="command-line" onSubmit={sendMessage}><span>›</span><input disabled={paused} value={message} onChange={event => setMessage(event.target.value)} placeholder={paused ? 'runtime paused — resume to send command' : '向运行态发送一条测试消息…'} /><button type="submit" aria-label="send command">↵</button></form>
        </article>
      </section>

      <section className="bottom-grid">
        <article className="panel trend-panel"><div className="panel-top"><div><span className="eyebrow">TOKEN THROUGHPUT</span><h3>执行脉冲</h3></div><div className="legend"><i /> INPUT <i /> OUTPUT</div></div><div className="chart"><span className="chart-value">18.4K <small>/MIN</small></span><SparkLine /><div className="axis"><span>12:00</span><span>12:10</span><span>12:20</span><span>NOW</span></div></div></article>
        <article className="panel queue-panel"><div className="panel-top"><div><span className="eyebrow">RELEASE QUEUE</span><h3>待执行变更</h3></div><button className="text-button">ALL 3 →</button></div><div className="release"><span className="release-ring">01</span><p><b>finance-analyst</b><small>v0.8.4 · awaiting policy gate</small></p><span className="release-state amber">REVIEW</span></div><div className="release"><span className="release-ring">02</span><p><b>knowledge-router</b><small>v2.1.0 · canary 10%</small></p><span className="release-state cyan">CANARY</span></div></article>
      </section>
    </section>
  </main>
}
