// Tafakkur AI — Today (customizable block board) + AI Coach (Murabbiy)

// ── Salah day ring ───────────────────────────────────────────────────────────
function SalahRing() {
  const SIZE = 300, CX = 150, CY = 150, R = 110;
  const A = (m) => (m/1440)*Math.PI*2 - Math.PI/2;
  const P = (m, r) => ({ x: CX + r*Math.cos(A(m)), y: CY + r*Math.sin(A(m)) });
  const arc = (m1, m2, r, large) => { const a=P(m1,r), b=P(m2,r); return `M ${a.x} ${a.y} A ${r} ${r} 0 ${large} 1 ${b.x} ${b.y}`; };
  const now = P(TNOW.min, R);
  return (
    <div style={{ position:'relative', width:SIZE, height:SIZE }}>
      <svg width={SIZE} height={SIZE} viewBox={`0 0 ${SIZE} ${SIZE}`} style={{ position:'absolute', inset:0 }}>
        <defs>
          <linearGradient id="night" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stopColor="#1E8C9A"/><stop offset="1" stopColor="#16324A"/>
          </linearGradient>
        </defs>
        <circle cx={CX} cy={CY} r={R} fill="none" stroke="rgba(235,235,245,0.10)" strokeWidth="2"/>
        {/* daytime arc Fajr → Maghrib */}
        <path d={arc(275, 1242, R, 1)} fill="none" stroke="rgba(236,232,220,0.55)" strokeWidth="2.5" strokeLinecap="round"/>
        {/* night arc Maghrib → Fajr */}
        <path d={arc(1242, 275, R, 0)} fill="none" stroke="url(#night)" strokeWidth="3" strokeLinecap="round"/>
        {/* prayer dots */}
        {TPRAYERS.map(p => {
          const pt = P(p.min, R); const passed = p.min < TNOW.min; const next = p.name === TNEXT.name;
          return <circle key={p.key} cx={pt.x} cy={pt.y} r={next?5:3.5} fill={next?'#C9A24E':passed?'#6b6357':'#B8C0CE'} stroke={next?'rgba(201,162,78,0.3)':'none'} strokeWidth={next?5:0}/>;
        })}
        {/* now marker */}
        <circle cx={now.x} cy={now.y} r="9" fill="rgba(201,162,78,0.18)"/>
        <circle cx={now.x} cy={now.y} r="4.5" fill="#E6CF8A"/>
      </svg>

      {/* prayer labels + icons (HTML overlay for crisp icons) */}
      {TPRAYERS.map(p => {
        const pt = P(p.min, R + 30); const passed = p.min < TNOW.min; const next = p.name === TNEXT.name;
        const col = next ? '#C9A24E' : p.icon === 'moon' ? '#7FA8E8' : passed ? C.fg3 : C.gold;
        return (
          <div key={p.key} style={{ position:'absolute', left:pt.x, top:pt.y, transform:'translate(-50%,-50%)', display:'flex', flexDirection:'column', alignItems:'center', gap:1, opacity:passed&&!next?0.6:1 }}>
            <TIcon name={p.icon} size={15} color={col} sw={1.6}/>
            <span style={{ fontFamily:FONT, fontSize:11, fontWeight:next?600:500, color: next?'#E6CF8A':C.fg2 }}>{p.short}</span>
          </div>
        );
      })}

      {/* center */}
      <div style={{ position:'absolute', inset:0, display:'flex', flexDirection:'column', alignItems:'center', justifyContent:'center', textAlign:'center' }}>
        <div style={{ fontFamily:MONO, fontSize:11, letterSpacing:'0.16em', color:C.fg3, textTransform:'uppercase' }}>Next · {TNEXT.name}</div>
        <div style={{ fontFamily:DISPLAY, fontSize:42, fontWeight:700, color:C.fg, letterSpacing:'-0.01em', lineHeight:1.1, margin:'4px 0 2px' }}>{TNEXT.in}</div>
        <div style={{ fontFamily:MONO, fontSize:11, letterSpacing:'0.14em', color:C.fg3 }}>AT {TNEXT.at}</div>
      </div>
    </div>
  );
}

// ── Balance radar ────────────────────────────────────────────────────────────
function BalanceRadar() {
  const SIZE = 240, CX = 120, CY = 120, MAX = 84;
  const n = TBALANCE.length;
  const ang = (i) => -Math.PI/2 + i*(Math.PI*2/n);
  const pt = (i, r) => ({ x: CX + r*Math.cos(ang(i)), y: CY + r*Math.sin(ang(i)) });
  const poly = (r) => TBALANCE.map((_,i)=>{ const p=pt(i, typeof r==='function'?r(i):r); return `${p.x},${p.y}`; }).join(' ');
  const valPoly = poly((i)=>TBALANCE[i].value*MAX);
  return (
    <div style={{ position:'relative', width:SIZE, height:SIZE, margin:'0 auto' }}>
      <svg width={SIZE} height={SIZE} viewBox={`0 0 ${SIZE} ${SIZE}`}>
        <defs>
          <radialGradient id="rfill" cx="50%" cy="50%" r="50%">
            <stop offset="0" stopColor="rgba(236,239,245,0.22)"/><stop offset="1" stopColor="rgba(236,239,245,0.04)"/>
          </radialGradient>
        </defs>
        {/* sector tints */}
        {TBALANCE.map((d,i)=>{ const a0=ang(i)-Math.PI/n, a1=ang(i)+Math.PI/n; const p0={x:CX+MAX*Math.cos(a0),y:CY+MAX*Math.sin(a0)}, p1={x:CX+MAX*Math.cos(a1),y:CY+MAX*Math.sin(a1)};
          return <path key={i} d={`M ${CX} ${CY} L ${p0.x} ${p0.y} A ${MAX} ${MAX} 0 0 1 ${p1.x} ${p1.y} Z`} fill={d.color} opacity="0.06"/>; })}
        {/* grid rings */}
        {[0.25,0.5,0.75,1].map((g,i)=>(<polygon key={i} points={poly(MAX*g)} fill="none" stroke="rgba(235,235,245,0.10)" strokeWidth="1"/>))}
        {/* spokes */}
        {TBALANCE.map((_,i)=>{ const p=pt(i,MAX); return <line key={i} x1={CX} y1={CY} x2={p.x} y2={p.y} stroke="rgba(235,235,245,0.08)" strokeWidth="1"/>; })}
        {/* value polygon */}
        <polygon points={valPoly} fill="url(#rfill)" stroke="rgba(236,239,245,0.7)" strokeWidth="1.5"/>
        {/* vertices */}
        {TBALANCE.map((d,i)=>{ const p=pt(i,d.value*MAX); return <circle key={i} cx={p.x} cy={p.y} r="4" fill={d.color} stroke="#0B0F17" strokeWidth="1.5"/>; })}
      </svg>
      {TBALANCE.map((d,i)=>{ const p=pt(i, MAX+20); return (
        <div key={i} style={{ position:'absolute', left:p.x, top:p.y, transform:'translate(-50%,-50%)', fontFamily:MONO, fontSize:10.5, fontWeight:600, color:d.color, whiteSpace:'nowrap' }}>{d.label}</div>
      ); })}
    </div>
  );
}

// ── AI recommendation card ───────────────────────────────────────────────────
function RecCard({ rec }) {
  const col = named(rec.tone);
  return (
    <div style={{ display:'flex', gap:12, padding:'13px 15px', background:C.card, border:`0.5px solid ${C.sep}`, borderRadius:15, alignItems:'flex-start' }}>
      <div style={{ width:30, height:30, borderRadius:'50%', background:`linear-gradient(135deg, ${col}, ${C.purple})`, display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0, boxShadow:`0 2px 10px ${softOf(col,0.5)}` }}>
        <TIcon name="sparkles" size={15} color="#fff" sw={1.8}/>
      </div>
      <div style={{ flex:1, minWidth:0 }}>
        <div style={{ fontFamily:MONO, fontSize:9.5, letterSpacing:'0.08em', textTransform:'uppercase', color:col, fontWeight:600, marginBottom:4 }}>Murabbiy · {rec.tag}</div>
        <div style={{ fontFamily:FONT, fontSize:13.5, color:C.fg, lineHeight:1.5 }}>{rec.text}</div>
      </div>
    </div>
  );
}

// ── Salah-anchored day plan ──────────────────────────────────────────────────
const CAT_COL = { physical:'#A8C36A', social:'#4CC2FF', financial:'#C9A24E', educational:'#A78BFA', spiritual:'#E6E1D6' };

function DayPlanList() {
  const [items, setItems] = React.useState(TDAYPLAN);
  const toggle = (i) => setItems(arr => arr.map((it,j)=> j===i&&it.type==='task'?{...it,done:!it.done}:it));
  return (
    <div style={{ margin:'0 16px', background:C.card, border:`0.5px solid ${C.sep}`, borderRadius:16, overflow:'hidden' }}>
      <div style={{ display:'flex', alignItems:'center', justifyContent:'space-between', padding:'13px 15px', borderBottom:`0.5px solid ${C.sep}` }}>
        <div style={{ fontFamily:FONT, fontSize:14, color:C.fg }}><b style={{ fontWeight:600 }}>Today</b> <span style={{ color:C.fg3, fontFamily:MONO, fontSize:12 }}>· {THIJRI} · {TCITY}</span></div>
        <span style={{ fontFamily:MONO, fontSize:10.5, letterSpacing:'0.1em', color:C.gold, display:'flex', alignItems:'center', gap:5 }}><span style={{ width:6, height:6, borderRadius:3, background:C.gold }} className="tf-breathe"/>LIVE</span>
      </div>
      {items.map((it,i) => it.type === 'salah' ? (
        <div key={i} style={{ display:'flex', alignItems:'center', gap:12, padding:'12px 15px', borderBottom:i<items.length-1?`0.5px solid ${C.sep}`:'none', background: it.state==='next'?softOf(C.gold,0.07):'transparent' }}>
          <div style={{ width:26, height:26, borderRadius:8, background:it.state==='next'?softOf(C.gold,0.18):'rgba(120,130,150,0.12)', display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
            <TIcon name={TPRAYERS.find(p=>p.name===it.name).icon} size={15} color={it.state==='next'?C.gold:it.state==='done'?C.fg3:C.fg2}/>
          </div>
          <span style={{ flex:1, fontFamily:FONT, fontSize:14.5, fontWeight:600, color:it.state==='done'?C.fg3:C.fg }}>{it.name}</span>
          {it.state==='next' && <span style={{ fontFamily:MONO, fontSize:9, letterSpacing:'0.06em', color:C.gold, background:softOf(C.gold,0.16), padding:'2px 6px', borderRadius:5 }}>NEXT</span>}
          <span style={{ fontFamily:MONO, fontSize:13, color:it.state==='done'?C.fg3:C.fg2 }}>{it.time}</span>
        </div>
      ) : (
        <div key={i} style={{ display:'flex', alignItems:'center', gap:12, padding:'11px 15px', borderBottom:i<items.length-1?`0.5px solid ${C.sep}`:'none' }}>
          <button onClick={()=>toggle(i)} style={{ background:'none', border:'none', cursor:'pointer', padding:0, flexShrink:0 }}>
            {it.done
              ? <div style={{ width:22, height:22, borderRadius:6, background:CAT_COL[it.cat], display:'flex', alignItems:'center', justifyContent:'center' }}><TIcon name="check" size={13} color="#0B0F17" sw={3}/></div>
              : <div style={{ width:22, height:22, borderRadius:6, border:`1.6px solid ${C.fg3}` }}/>}
          </button>
          <div style={{ flex:1, minWidth:0 }}>
            <div style={{ fontFamily:FONT, fontSize:14, color:it.done?C.fg3:C.fg, textDecoration:it.done?'line-through':'none' }}>{it.title}</div>
            <div style={{ fontFamily:MONO, fontSize:11, color:C.fg3, marginTop:2, display:'flex', alignItems:'center', gap:6 }}>
              <span>{it.time} · {it.dur}</span><span style={{ width:5, height:5, borderRadius:3, background:CAT_COL[it.cat] }}/><span>{it.cat}</span>
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}

// ── Habit ring (compact) ─────────────────────────────────────────────────────
function HabitRing({ h }) {
  const col = named(h.accent);
  const r = 17, circ = 2*Math.PI*r, done = h.week.filter(Boolean).length, frac = done/7;
  return (
    <div style={{ minWidth:74, background:C.card, border:`0.5px solid ${C.sep}`, borderRadius:14, padding:'12px 8px', display:'flex', flexDirection:'column', alignItems:'center', gap:7 }}>
      <div style={{ position:'relative', width:44, height:44 }}>
        <svg width="44" height="44" style={{ transform:'rotate(-90deg)' }}>
          <circle cx="22" cy="22" r={r} fill="none" stroke="rgba(120,130,150,0.2)" strokeWidth="3.5"/>
          <circle cx="22" cy="22" r={r} fill="none" stroke={col} strokeWidth="3.5" strokeLinecap="round" strokeDasharray={circ} strokeDashoffset={circ*(1-frac)}/>
        </svg>
        <div style={{ position:'absolute', inset:0, display:'flex', alignItems:'center', justifyContent:'center' }}><TIcon name={h.icon} size={16} color={col}/></div>
      </div>
      <div style={{ fontFamily:FONT, fontSize:11, color:C.fg2, textAlign:'center', lineHeight:1.2, height:26, overflow:'hidden' }}>{h.name}</div>
      <div style={{ fontFamily:MONO, fontSize:10, color:col, fontWeight:600, display:'flex', alignItems:'center', gap:3 }}><TIcon name="flame" size={10} color={col}/>{h.streak}</div>
    </div>
  );
}

// ── Aspect registry (the 5 dimensions + an overview bucket) ───────────────────
const ASPECTS = {
  spiritual:   { label:'Spiritual',   native:'Maʼnaviy',  color:'#E6E1D6', icon:'moon' },
  physical:    { label:'Physical',    native:'Jismoniy',  color:'#A8C36A', icon:'activity' },
  educational: { label:'Educational', native:'Taʼlim',    color:'#A78BFA', icon:'bookOpen' },
  social:      { label:'Social',      native:'Ijtimoiy',  color:'#4CC2FF', icon:'users' },
  financial:   { label:'Financial',   native:'Moliyaviy', color:'#C9A24E', icon:'wallet' },
  overview:    { label:'Overview',    native:'Umumiy',    color:'#9BB4E8', icon:'sparkles' },
};
const ASPECT_ORDER = ['spiritual','physical','educational','social','financial','overview'];

function BlockHeading({ aspect, title, action, onAction }) {
  const a = ASPECTS[aspect];
  return (
    <div style={{ display:'flex', alignItems:'center', gap:9, padding:'0 20px 12px' }}>
      <span style={{ width:8, height:8, borderRadius:3, background:a.color, flexShrink:0 }}/>
      <h2 style={{ fontFamily:DISPLAY, fontSize:20, fontWeight:700, color:C.fg, letterSpacing:'0.2px', margin:0, flex:1 }}>{title}</h2>
      {action && <button onClick={onAction} style={{ background:'none', border:'none', fontFamily:FONT, fontSize:14, color:a.color, cursor:'pointer', padding:0, whiteSpace:'nowrap' }}>{action}</button>}
    </div>
  );
}

// ── Optional block contents (added from the gallery) ─────────────────────────
function GratitudeBlock() {
  return (
    <div style={{ padding:'0 16px' }}>
      <Card pad={16}>
        <div style={{ fontFamily:FONT, fontSize:13, color:C.fg2, marginBottom:11 }}>Three things you're grateful for today</div>
        <div style={{ display:'flex', flexDirection:'column', gap:8 }}>
          {TGRATITUDE_TODAY.map((g,i)=>(
            <div key={i} style={{ display:'flex', alignItems:'center', gap:10, padding:'9px 11px', background:softOf('#8FBF6F',0.12), borderRadius:10 }}>
              <TIcon name="leaf" size={14} color="#A8C36A"/>
              <span style={{ fontFamily:FONT, fontSize:13.5, color:C.fg }}>{g}</span>
            </div>
          ))}
        </div>
      </Card>
    </div>
  );
}
function LearnBlock({ onGo }) {
  const s = TSUBJECTS[1]; const col = '#A78BFA';
  return (
    <div style={{ padding:'0 16px' }}>
      <Card pad={16} onClick={onGo} style={{ display:'flex', alignItems:'center', gap:13 }}>
        <div style={{ width:46, height:46, borderRadius:13, background:`linear-gradient(140deg, ${col}, ${softOf(col,0.3)})`, display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
          <TIcon name="terminal" size={22} color="#fff" sw={1.8}/>
        </div>
        <div style={{ flex:1, minWidth:0 }}>
          <div style={{ fontFamily:MONO, fontSize:10, letterSpacing:'0.06em', textTransform:'uppercase', color:col, marginBottom:3 }}>Next · {s.name}</div>
          <div style={{ fontFamily:FONT, fontSize:14.5, fontWeight:600, color:C.fg }}>{s.next}</div>
          <div style={{ display:'flex', alignItems:'center', gap:9, marginTop:8 }}>
            <ProgressBar value={s.progress} color={col} h={5}/>
            <span style={{ fontFamily:MONO, fontSize:11, color:C.fg2 }}>{Math.round(s.progress*100)}%</span>
          </div>
        </div>
        <div style={{ width:40, height:40, borderRadius:'50%', background:softOf(col,0.16), display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
          <TIcon name="play" size={16} color={col}/>
        </div>
      </Card>
    </div>
  );
}
function ThoughtPreviewBlock({ onGo }) {
  const p = TSOCIAL[0]; const col = '#4CC2FF';
  return (
    <div style={{ padding:'0 16px' }}>
      <Card pad={16} onClick={onGo}>
        <div style={{ display:'flex', alignItems:'center', gap:10, marginBottom:9 }}>
          <Avatar name={p.author} seed={p.avatarSeed} size={32}/>
          <div style={{ flex:1, minWidth:0 }}>
            <div style={{ fontFamily:FONT, fontSize:13.5, fontWeight:600, color:C.fg }}>{p.author}</div>
            <div style={{ fontFamily:FONT, fontSize:11.5, color:C.fg3 }}>{p.handle} · {p.time}</div>
          </div>
          <Pill color={col} soft={softOf(col)}><TIcon name="hash" size={10} color={col}/>{p.tag}</Pill>
        </div>
        <p style={{ fontFamily:FONT, fontSize:13.5, color:C.fg, lineHeight:1.55, margin:0, display:'-webkit-box', WebkitLineClamp:3, WebkitBoxOrient:'vertical', overflow:'hidden' }}>{p.text}</p>
      </Card>
    </div>
  );
}
function FinanceBlock({ onGo }) {
  const D = TFINANCE;
  return (
    <div style={{ padding:'0 16px' }}>
      <Card pad={16} onClick={onGo}>
        <div style={{ display:'flex', alignItems:'baseline', justifyContent:'space-between' }}>
          <div>
            <div style={{ fontFamily:MONO, fontSize:10, letterSpacing:'0.08em', textTransform:'uppercase', color:C.fg3, marginBottom:4 }}>Net worth</div>
            <div style={{ fontFamily:DISPLAY, fontSize:28, fontWeight:700, color:C.fg }}>${D.net.toLocaleString()}</div>
          </div>
          <div style={{ display:'flex', alignItems:'center', gap:3, color:C.green }}>
            <TIcon name="trendingUp" size={15} color={C.green}/>
            <span style={{ fontFamily:MONO, fontSize:13, fontWeight:600 }}>{D.change}%</span>
          </div>
        </div>
        <div style={{ display:'flex', height:9, borderRadius:5, overflow:'hidden', marginTop:13 }}>
          {D.allocations.map((a,i)=><div key={i} style={{ width:`${a.pct}%`, background:named(a.color) }}/>)}
        </div>
      </Card>
    </div>
  );
}
function SleepBlock() {
  const col = '#A8C36A';
  return (
    <div style={{ padding:'0 16px' }}>
      <Card pad={16} style={{ display:'flex', alignItems:'center', gap:14 }}>
        <div style={{ width:46, height:46, borderRadius:13, background:softOf(col,0.14), display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
          <TIcon name="moon" size={22} color={col}/>
        </div>
        <div style={{ flex:1 }}>
          <div style={{ fontFamily:DISPLAY, fontSize:22, fontWeight:700, color:C.fg }}>7h 10m</div>
          <div style={{ fontFamily:FONT, fontSize:12.5, color:C.fg2 }}>Well rested · synced from Health</div>
        </div>
        <Pill color={C.green} soft={softOf(C.green)}>Good</Pill>
      </Card>
    </div>
  );
}

// ── Block registry ───────────────────────────────────────────────────────────
// Each block: aspect, title, icon, optional action link, render(ctx).
const BLOCKS = {
  salah: {
    aspect:'spiritual', title:'Prayer times', icon:'sunrise', hero:true,
    render: () => (
      <div style={{ display:'flex', flexDirection:'column', alignItems:'center', padding:'4px 0 0' }}>
        <SalahRing/>
        <div style={{ fontFamily:FONT, fontSize:13.5, color:C.fg2, marginTop:8, textAlign:'center', maxWidth:280, lineHeight:1.5 }}>Your day starts at <span style={{ color:C.gold, fontWeight:600 }}>Maghrib</span> — plan mindful of Salah.</div>
      </div>
    ),
  },
  guidance: {
    aspect:'overview', title:'AI guidance', icon:'sparkles',
    render: ({ onCoach, accent }) => (
      <div style={{ padding:'0 16px', display:'flex', flexDirection:'column', gap:10 }}>
        {TRECS.map((r,i) => <RecCard key={i} rec={r}/>)}
        <button onClick={onCoach} style={{ width:'100%', background:'transparent', border:`1px solid ${C.line}`, borderRadius:13, padding:'11px', color:accent, fontFamily:FONT, fontSize:14, fontWeight:600, cursor:'pointer', display:'flex', alignItems:'center', justifyContent:'center', gap:8 }}>
          <TIcon name="sparkles" size={15} color={accent}/> Ask Murabbiy
        </button>
      </div>
    ),
  },
  plan: {
    aspect:'overview', title:"Today's plan", icon:'list', action:'Edit day',
    onAction: ({ onOpen }) => onOpen({ module:'Productivity' }),
    render: () => <DayPlanList/>,
  },
  balance: {
    aspect:'overview', title:'The month, balanced', icon:'target',
    render: () => (
      <div style={{ padding:'0 16px' }}>
        <Card pad={18}>
          <BalanceRadar/>
          <div style={{ display:'grid', gridTemplateColumns:'1fr 1fr', gap:9, marginTop:14 }}>
            {TBALANCE.map(d => (
              <div key={d.key} style={{ display:'flex', gap:9, alignItems:'flex-start' }}>
                <span style={{ width:9, height:9, borderRadius:3, background:d.color, marginTop:4, flexShrink:0 }}/>
                <div style={{ minWidth:0 }}>
                  <div style={{ fontFamily:FONT, fontSize:13, fontWeight:600, color:C.fg }}>{d.label}</div>
                  <div style={{ fontFamily:MONO, fontSize:10.5, color:C.fg3, lineHeight:1.4 }}>{d.desc}</div>
                </div>
              </div>
            ))}
          </div>
        </Card>
      </div>
    ),
  },
  habits: {
    aspect:'physical', title:'Habits', icon:'repeat', action:'All habits',
    onAction: ({ onOpen }) => onOpen({ module:'Productivity' }),
    render: () => (
      <div style={{ display:'flex', gap:10, overflowX:'auto', padding:'0 16px 4px' }}>
        {THABITS.map(h => <HabitRing key={h.id} h={h}/>)}
      </div>
    ),
  },
  gratitude: { aspect:'spiritual', title:'Gratitude', icon:'leaf', render: () => <GratitudeBlock/> },
  learn: {
    aspect:'educational', title:'Continue learning', icon:'terminal', action:'All subjects',
    onAction: ({ onOpen }) => onOpen({ module:'Learn' }),
    render: ({ onOpen }) => <LearnBlock onGo={()=>onOpen({ module:'Learn' })}/>,
  },
  thoughts: {
    aspect:'social', title:'From your circle', icon:'messageCircle', action:'Open',
    onAction: ({ onOpen }) => onOpen({ module:'Thoughts' }),
    render: ({ onOpen }) => <ThoughtPreviewBlock onGo={()=>onOpen({ module:'Thoughts' })}/>,
  },
  finance: {
    aspect:'financial', title:'Portfolio', icon:'wallet', action:'Details',
    onAction: ({ onOpen }) => onOpen({ module:'Finance' }),
    render: ({ onOpen }) => <FinanceBlock onGo={()=>onOpen({ module:'Finance' })}/>,
  },
  sleep: { aspect:'physical', title:'Sleep & rest', icon:'moon', render: () => <SleepBlock/> },
};

const DEFAULT_BLOCKS = ['salah','guidance','plan','balance','habits'];
const BLOCKS_LS = 'tafakkur.today.blocks.v1';
function loadBlocks() {
  try {
    const raw = localStorage.getItem(BLOCKS_LS);
    if (raw) { const arr = JSON.parse(raw); if (Array.isArray(arr)) { const f = arr.filter(id=>BLOCKS[id]); if (f.length) return f; } }
  } catch (e) {}
  return DEFAULT_BLOCKS.slice();
}

// ── Manage rows (edit mode) ──────────────────────────────────────────────────
function ctlBtn(disabled) {
  return { width:30, height:26, borderRadius:7, border:'none', background:disabled?'transparent':'rgba(120,130,150,0.14)', cursor:disabled?'default':'pointer', display:'flex', alignItems:'center', justifyContent:'center' };
}
function ManageRow({ block, isFirst, isLast, onUp, onDown, onRemove }) {
  const a = ASPECTS[block.aspect];
  return (
    <div style={{ display:'flex', alignItems:'center', gap:11, padding:'10px 11px', background:C.card, border:`0.5px solid ${C.sep}`, borderRadius:13 }}>
      <div style={{ width:32, height:32, borderRadius:9, background:softOf(a.color,0.16), display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
        <TIcon name={block.icon} size={16} color={a.color}/>
      </div>
      <div style={{ flex:1, minWidth:0 }}>
        <div style={{ fontFamily:FONT, fontSize:14, fontWeight:600, color:C.fg }}>{block.title}</div>
        <div style={{ fontFamily:MONO, fontSize:9.5, letterSpacing:'0.06em', textTransform:'uppercase', color:a.color }}>{a.label}</div>
      </div>
      <div style={{ display:'flex', flexDirection:'column', gap:2 }}>
        <button onClick={onUp} disabled={isFirst} style={ctlBtn(isFirst)}><TIcon name="chevronUp" size={15} color={isFirst?C.fg4:C.fg2}/></button>
        <button onClick={onDown} disabled={isLast} style={ctlBtn(isLast)}><TIcon name="chevronDown" size={15} color={isLast?C.fg4:C.fg2}/></button>
      </div>
      <button onClick={onRemove} aria-label="Remove block" style={{ width:32, height:32, borderRadius:8, border:'none', background:softOf(C.danger,0.16), cursor:'pointer', display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
        <TIcon name="x" size={15} color={C.danger}/>
      </button>
    </div>
  );
}
function AddRow({ block, onAdd }) {
  const a = ASPECTS[block.aspect];
  return (
    <button onClick={onAdd} style={{ width:'100%', textAlign:'left', display:'flex', alignItems:'center', gap:11, padding:'10px 11px', background:C.card, border:`0.5px solid ${C.sep}`, borderRadius:13, cursor:'pointer' }}>
      <div style={{ width:32, height:32, borderRadius:9, background:softOf(a.color,0.16), display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
        <TIcon name={block.icon} size={16} color={a.color}/>
      </div>
      <div style={{ flex:1, minWidth:0 }}>
        <div style={{ fontFamily:FONT, fontSize:14, fontWeight:600, color:C.fg }}>{block.title}</div>
        <div style={{ fontFamily:MONO, fontSize:9.5, letterSpacing:'0.06em', textTransform:'uppercase', color:a.color }}>{a.label}</div>
      </div>
      <div style={{ width:30, height:30, borderRadius:'50%', background:softOf(a.color,0.18), display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
        <TIcon name="plus" size={16} color={a.color} sw={2.2}/>
      </div>
    </button>
  );
}

// ── Today screen ─────────────────────────────────────────────────────────────
function TodayScreen({ onOpen, onCoach }) {
  const { accent } = useApp();
  const [order, setOrder] = React.useState(loadBlocks);
  const [editing, setEditing] = React.useState(false);
  const persist = (next) => { setOrder(next); try { localStorage.setItem(BLOCKS_LS, JSON.stringify(next)); } catch (e) {} };
  const remove = (id) => persist(order.filter(x => x !== id));
  const add = (id) => persist([...order, id]);
  const move = (i, dir) => { const j = i+dir; if (j<0||j>=order.length) return; const next = order.slice(); [next[i],next[j]]=[next[j],next[i]]; persist(next); };
  const ctx = { onOpen, onCoach, accent };
  const inactive = Object.keys(BLOCKS).filter(id => !order.includes(id));

  return (
    <div style={{ position:'relative' }}>
      <div style={{ position:'absolute', top:-30, left:'50%', transform:'translateX(-50%)', width:460, height:380, borderRadius:'50%', background:'radial-gradient(closest-side, rgba(201,162,78,0.16), rgba(124,60,30,0.05) 55%, transparent 75%)', pointerEvents:'none', zIndex:0 }}/>

      {/* header */}
      <div style={{ position:'relative', zIndex:1, padding:'6px 16px 0', display:'flex', alignItems:'center', justifyContent:'space-between' }}>
        <div>
          <div style={{ fontFamily:MONO, fontSize:11, letterSpacing:'0.18em', color:C.gold }}>BISMILLAH.</div>
          <div style={{ fontFamily:FONT, fontSize:13, color:C.fg2, marginTop:3 }}>{TUSER.greetingUz}, Aziz · {TCITY}</div>
        </div>
        <div style={{ display:'flex', alignItems:'center', gap:9 }}>
          <button onClick={()=>setEditing(e=>!e)} style={{ display:'flex', alignItems:'center', gap:6, padding:'8px 12px', borderRadius:999, border:`1px solid ${editing?accent:C.line}`, background:editing?accent:'transparent', cursor:'pointer' }}>
            <TIcon name={editing?'check':'sliders'} size={14} color={editing?'#fff':C.fg2} sw={2}/>
            <span style={{ fontFamily:FONT, fontSize:12.5, fontWeight:600, color:editing?'#fff':C.fg2 }}>{editing?'Done':'Customize'}</span>
          </button>
          {!editing && <div style={{ width:34, height:34, borderRadius:'50%', background:`linear-gradient(135deg, ${C.gold}, ${C.rose})`, display:'flex', alignItems:'center', justifyContent:'center', fontFamily:FONT, fontSize:14, fontWeight:600, color:'#fff' }}>A</div>}
        </div>
      </div>

      {editing ? (
        <div style={{ position:'relative', zIndex:1, padding:'16px 16px 0' }}>
          <p style={{ fontFamily:FONT, fontSize:13.5, color:C.fg2, lineHeight:1.55, margin:'0 0 16px' }}>Reorder, remove, or add blocks. Shape your home around all five aspects of your growth.</p>

          <div style={{ fontFamily:MONO, fontSize:10.5, letterSpacing:'0.08em', textTransform:'uppercase', color:C.fg3, padding:'0 2px 9px' }}>On your home · {order.length}</div>
          <div style={{ display:'flex', flexDirection:'column', gap:9 }}>
            {order.map((id,i) => (
              <ManageRow key={id} block={BLOCKS[id]} isFirst={i===0} isLast={i===order.length-1}
                onUp={()=>move(i,-1)} onDown={()=>move(i,1)} onRemove={()=>remove(id)}/>
            ))}
          </div>

          <div style={{ fontFamily:MONO, fontSize:10.5, letterSpacing:'0.08em', textTransform:'uppercase', color:C.fg3, padding:'24px 2px 4px' }}>Add blocks</div>
          {ASPECT_ORDER.map(asp => {
            const items = inactive.filter(id => BLOCKS[id].aspect === asp);
            const a = ASPECTS[asp];
            return (
              <div key={asp} style={{ marginTop:14 }}>
                <div style={{ display:'flex', alignItems:'center', gap:8, padding:'0 2px 9px' }}>
                  <span style={{ width:7, height:7, borderRadius:3, background:a.color }}/>
                  <span style={{ fontFamily:FONT, fontSize:13, fontWeight:600, color:C.fg }}>{a.label}</span>
                  <span style={{ fontFamily:MONO, fontSize:10.5, color:C.fg3 }}>{a.native}</span>
                </div>
                {items.length ? (
                  <div style={{ display:'flex', flexDirection:'column', gap:9 }}>
                    {items.map(id => <AddRow key={id} block={BLOCKS[id]} onAdd={()=>add(id)}/>)}
                  </div>
                ) : (
                  <div style={{ display:'flex', alignItems:'center', gap:8, padding:'10px 11px', borderRadius:12, border:`1px dashed ${C.line}` }}>
                    <TIcon name="check" size={14} color={C.green}/>
                    <span style={{ fontFamily:FONT, fontSize:12.5, color:C.fg3 }}>All {a.label.toLowerCase()} blocks are on your home</span>
                  </div>
                )}
              </div>
            );
          })}

          <button onClick={()=>persist(DEFAULT_BLOCKS.slice())} style={{ width:'100%', marginTop:22, background:'transparent', border:`1px solid ${C.line}`, borderRadius:12, padding:'12px', color:C.fg2, fontFamily:FONT, fontSize:13.5, cursor:'pointer', display:'flex', alignItems:'center', justifyContent:'center', gap:8 }}>
            <TIcon name="repeat" size={15} color={C.fg2}/> Reset to default layout
          </button>
          <div style={{ height:120 }}/>
        </div>
      ) : (
        <>
          {order.length === 0 ? (
            <div style={{ position:'relative', zIndex:1, padding:'60px 30px', textAlign:'center' }}>
              <div style={{ fontFamily:FONT, fontSize:15, color:C.fg2, lineHeight:1.6, marginBottom:18 }}>Your home is empty. Add blocks to track what matters across your five aspects.</div>
              <button onClick={()=>setEditing(true)} style={{ background:accent, color:'#fff', border:'none', borderRadius:12, padding:'12px 22px', fontFamily:FONT, fontSize:14.5, fontWeight:600, cursor:'pointer' }}>Add blocks</button>
            </div>
          ) : order.map(id => {
            const b = BLOCKS[id];
            return (
              <div key={id} style={{ position:'relative', zIndex:1, paddingTop: b.hero?10:24 }}>
                {!b.hero && <BlockHeading aspect={b.aspect} title={b.title} action={b.action} onAction={()=>b.onAction && b.onAction(ctx)}/>}
                {b.render(ctx)}
              </div>
            );
          })}
          <div style={{ height:120 }}/>
        </>
      )}
    </div>
  );
}

// ── Coach (full screen) ──────────────────────────────────────────────────────
function CoachScreen() {
  const { accent } = useApp();
  const [msgs, setMsgs] = React.useState(TCHAT);
  const [draft, setDraft] = React.useState('');
  const [tab, setTab] = React.useState('chat');
  const scroller = React.useRef(null);
  React.useEffect(() => { if (scroller.current && tab==='chat') scroller.current.scrollTop = scroller.current.scrollHeight; }, [msgs, tab]);
  const send = (txt) => {
    const t = (txt ?? draft).trim(); if (!t) return;
    setMsgs(m => [...m, { from:'me', text:t, time:'now' }]); setDraft('');
    setTimeout(() => setMsgs(m => [...m, { from:'ai', text: aiReply(t), time:'now' }]), 650);
  };
  return (
    <div style={{ display:'flex', flexDirection:'column', height:'100%' }}>
      <div style={{ position:'relative', padding:'10px 20px 0', overflow:'hidden' }}>
        <AmbientGlow color={accent} top={-90} opacity={0.6}/>
        <div style={{ position:'relative', zIndex:1, display:'flex', alignItems:'center', gap:14, paddingBottom:6 }}>
          <BreathOrb size={62} color={accent}/>
          <div>
            <h1 style={{ fontFamily:DISPLAY, fontSize:26, fontWeight:700, color:C.fg, margin:0 }}>Murabbiy</h1>
            <div style={{ fontFamily:FONT, fontSize:13, color:C.green, display:'flex', alignItems:'center', gap:6, marginTop:2 }}><span style={{ width:6, height:6, borderRadius:3, background:C.green }}/>On-device · your data stays private</div>
          </div>
        </div>
        <div style={{ position:'relative', zIndex:1, padding:'10px 0 12px' }}>
          <Segmented tabs={[['chat','Chat'],['memory','What I remember']]} value={tab} onChange={setTab}/>
        </div>
      </div>

      {tab === 'chat' && (
        <>
          <div ref={scroller} style={{ flex:1, overflowY:'auto', padding:'8px 16px 14px', display:'flex', flexDirection:'column', gap:10 }}>
            {msgs.map((m,i) => <ChatBubble key={i} m={m} accent={accent}/>)}
          </div>
          <div style={{ padding:'0 14px 8px', display:'flex', gap:7, overflowX:'auto' }}>
            {TCHAT_SUGGEST.map(s => <button key={s} onClick={()=>send(s)} style={{ whiteSpace:'nowrap', background:C.card2, border:`0.5px solid ${C.sep}`, borderRadius:999, padding:'7px 12px', fontFamily:FONT, fontSize:12.5, color:C.fg2, cursor:'pointer' }}>{s}</button>)}
          </div>
          <ChatInput draft={draft} setDraft={setDraft} onSend={()=>send()} accent={accent}/>
        </>
      )}

      {tab === 'memory' && (
        <div style={{ flex:1, overflowY:'auto', padding:'4px 16px 110px' }}>
          <p style={{ fontFamily:FONT, fontSize:13.5, color:C.fg2, lineHeight:1.6, padding:'0 4px 14px' }}>Murabbiy keeps a private, on-device model of who you are. It powers your plan, lessons and nudges. You're in control — edit or forget anything.</p>
          <div style={{ display:'flex', flexDirection:'column', gap:10 }}>
            {TMEMORY.map((m,i) => (
              <Card key={i} pad={14} style={{ display:'flex', alignItems:'center', gap:12 }}>
                <IconBadge name={m.icon} color={accent}/>
                <div style={{ flex:1 }}>
                  <div style={{ fontFamily:MONO, fontSize:10, letterSpacing:'0.07em', textTransform:'uppercase', color:C.fg3, marginBottom:3 }}>{m.label}</div>
                  <div style={{ fontFamily:FONT, fontSize:14, color:C.fg }}>{m.text}</div>
                </div>
                <button style={{ background:'none', border:'none', cursor:'pointer' }}><TIcon name="more" size={18} color={C.fg3}/></button>
              </Card>
            ))}
          </div>
          <div style={{ marginTop:14, padding:'13px 14px', borderRadius:14, border:`0.5px solid ${C.sep}`, display:'flex', alignItems:'center', gap:10 }}>
            <TIcon name="lock" size={16} color={C.green}/>
            <span style={{ fontFamily:FONT, fontSize:12.5, color:C.fg2, flex:1 }}>Processed on-device with Apple Intelligence. Nothing leaves your phone unless you turn on sync.</span>
          </div>
        </div>
      )}
    </div>
  );
}

Object.assign(window, { TodayScreen, CoachScreen, SalahRing, BalanceRadar, ASPECTS });
