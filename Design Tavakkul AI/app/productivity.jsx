// Tafakkur AI — Productivity (tasks, habits, focus/Pomodoro, week)

const PRIO = { high:{c:'#E5484D',l:'High'}, med:{c:'#E0A93B',l:'Med'}, low:{c:'#7E8799',l:'Low'} };

function TaskRow({ t, onToggle }) {
  return (
    <div style={{ display:'flex', alignItems:'center', gap:12, padding:'12px 14px', borderBottom:`0.5px solid ${C.sep}` }}>
      <button onClick={()=>onToggle(t.id)} style={{ background:'none', border:'none', cursor:'pointer', padding:0, flexShrink:0 }}>
        {t.done
          ? <div style={{ width:22, height:22, borderRadius:'50%', background:C.green, display:'flex', alignItems:'center', justifyContent:'center' }}><TIcon name="check" size={13} color="#fff" sw={3}/></div>
          : <div style={{ width:22, height:22, borderRadius:'50%', border:`1.8px solid ${PRIO[t.priority].c}` }}/>}
      </button>
      <div style={{ flex:1, minWidth:0 }}>
        <div style={{ fontFamily:FONT, fontSize:14.5, color:t.done?C.fg3:C.fg, textDecoration:t.done?'line-through':'none' }}>{t.title}</div>
        <div style={{ display:'flex', alignItems:'center', gap:8, marginTop:3 }}>
          <span style={{ fontFamily:MONO, fontSize:10.5, color:C.fg3 }}>{t.tag}</span>
          <span style={{ width:3, height:3, borderRadius:2, background:C.fg3 }}/>
          <span style={{ fontFamily:FONT, fontSize:11.5, color:t.due==='Today'?named('blue'):C.fg3 }}>{t.due}</span>
        </div>
      </div>
      <span style={{ width:7, height:7, borderRadius:4, background:PRIO[t.priority].c, flexShrink:0 }}/>
    </div>
  );
}

function FocusTimer() {
  const { accent } = useApp();
  const TOTAL = 25*60;
  const [left, setLeft] = React.useState(TOTAL);
  const [running, setRunning] = React.useState(false);
  React.useEffect(() => {
    if (!running) return;
    const id = setInterval(() => setLeft(l => l<=1 ? (clearInterval(id), 0) : l-1), 1000);
    return () => clearInterval(id);
  }, [running]);
  const mm = String(Math.floor(left/60)).padStart(2,'0'), ss = String(left%60).padStart(2,'0');
  const frac = 1 - left/TOTAL, r = 86, circ = 2*Math.PI*r;
  return (
    <div style={{ position:'relative', display:'flex', flexDirection:'column', alignItems:'center', padding:'10px 0 4px' }}>
      <AmbientGlow color={accent} top={-20} opacity={0.5}/>
      <div style={{ position:'relative', width:208, height:208, zIndex:1 }}>
        <svg width="208" height="208" style={{ transform:'rotate(-90deg)' }}>
          <circle cx="104" cy="104" r={r} fill="none" stroke="rgba(120,130,150,0.16)" strokeWidth="10"/>
          <circle cx="104" cy="104" r={r} fill="none" stroke={accent} strokeWidth="10" strokeLinecap="round" strokeDasharray={circ} strokeDashoffset={circ*(1-frac)} style={{ transition:'stroke-dashoffset 1s linear', filter:`drop-shadow(0 0 6px ${softOf(accent,0.6)})` }}/>
        </svg>
        <div style={{ position:'absolute', inset:0, display:'flex', flexDirection:'column', alignItems:'center', justifyContent:'center' }}>
          <div style={{ fontFamily:MONO, fontSize:46, fontWeight:600, color:C.fg, letterSpacing:'-0.02em' }}>{mm}:{ss}</div>
          <div style={{ fontFamily:MONO, fontSize:11, letterSpacing:'0.1em', textTransform:'uppercase', color:C.fg3, marginTop:2 }}>API refactor</div>
        </div>
      </div>
      <div style={{ display:'flex', gap:12, marginTop:22, zIndex:1 }}>
        <button onClick={()=>{ setRunning(false); setLeft(TOTAL); }} style={{ width:52, height:52, borderRadius:'50%', border:`0.5px solid ${C.line}`, background:C.card, cursor:'pointer', display:'flex', alignItems:'center', justifyContent:'center' }}><TIcon name="repeat" size={20} color={C.fg2}/></button>
        <button onClick={()=>setRunning(r=>!r)} style={{ width:64, height:64, borderRadius:'50%', border:'none', background:accent, cursor:'pointer', display:'flex', alignItems:'center', justifyContent:'center', boxShadow:`0 6px 20px ${softOf(accent,0.6)}` }}>
          <TIcon name={running?'pause':'play'} size={26} color="#fff" sw={2}/>
        </button>
        <button style={{ width:52, height:52, borderRadius:'50%', border:`0.5px solid ${C.line}`, background:C.card, cursor:'pointer', display:'flex', alignItems:'center', justifyContent:'center' }}><TIcon name="coffee" size={20} color={C.fg2}/></button>
      </div>
      <div style={{ fontFamily:FONT, fontSize:12.5, color:C.fg3, marginTop:16, zIndex:1 }}>3 of 4 sessions today · 1h 15m focused</div>
    </div>
  );
}

function HabitTrackRow({ h, onToggle }) {
  const col = named(h.accent);
  const days = ['M','T','W','T','F','S','S'];
  return (
    <Card pad={14} style={{ display:'flex', flexDirection:'column', gap:11 }}>
      <div style={{ display:'flex', alignItems:'center', gap:11 }}>
        <IconBadge name={h.icon} color={col} size={36} isz={18}/>
        <div style={{ flex:1 }}>
          <div style={{ fontFamily:FONT, fontSize:14.5, fontWeight:600, color:C.fg }}>{h.name}</div>
          <div style={{ fontFamily:MONO, fontSize:11, color:col, display:'flex', alignItems:'center', gap:4, marginTop:1 }}><TIcon name="flame" size={11} color={col}/>{h.streak} day streak</div>
        </div>
        <button onClick={()=>onToggle(h.id)} style={{ background:'none', border:'none', cursor:'pointer', padding:0 }}>
          {h.done ? <div style={{ width:28, height:28, borderRadius:'50%', background:col, display:'flex', alignItems:'center', justifyContent:'center' }}><TIcon name="check" size={16} color="#fff" sw={3}/></div>
          : <div style={{ width:28, height:28, borderRadius:'50%', border:`1.8px solid ${C.fg3}` }}/>}
        </button>
      </div>
      <div style={{ display:'flex', gap:6 }}>
        {h.week.map((d,i) => (
          <div key={i} style={{ flex:1, display:'flex', flexDirection:'column', alignItems:'center', gap:4 }}>
            <div style={{ width:'100%', height:6, borderRadius:3, background:d?col:'rgba(120,130,150,0.18)' }}/>
            <span style={{ fontFamily:MONO, fontSize:9, color:C.fg3 }}>{days[i]}</span>
          </div>
        ))}
      </div>
    </Card>
  );
}

function ProductivityScreen() {
  const { accent } = useApp();
  const [tab, setTab] = React.useState('tasks');
  const [tasks, setTasks] = React.useState(TTASKS);
  const [habits, setHabits] = React.useState(THABITS);
  const toggleTask = id => setTasks(ts => ts.map(t => t.id===id?{...t,done:!t.done}:t));
  const toggleHabit = id => setHabits(hs => hs.map(h => h.id===id?{...h,done:!h.done,streak:h.done?h.streak-1:h.streak+1}:h));
  const open = tasks.filter(t=>!t.done), done = tasks.filter(t=>t.done);
  return (
    <div>
      <LargeTitle title="Productivity" subtitle="Bugun · stay in flow"/>
      <div style={{ padding:'8px 16px 14px' }}>
        <Segmented tabs={[['tasks','Tasks'],['habits','Habits'],['focus','Focus']]} value={tab} onChange={setTab}/>
      </div>

      {tab === 'tasks' && (
        <div style={{ padding:'0 16px 120px' }}>
          <ProactiveCard text="You have 2 high-priority tasks tied to Thursday's interview. Want me to schedule them into your morning focus blocks?" action="Schedule them" compact/>
          <div style={{ height:14 }}/>
          <div style={{ fontFamily:MONO, fontSize:10.5, letterSpacing:'0.07em', textTransform:'uppercase', color:C.fg3, padding:'4px 4px 8px' }}>To do · {open.length}</div>
          <Card pad={0} style={{ overflow:'hidden' }}>
            {open.map(t => <TaskRow key={t.id} t={t} onToggle={toggleTask}/>)}
            <button style={{ width:'100%', display:'flex', alignItems:'center', gap:10, padding:'13px 14px', background:'none', border:'none', cursor:'pointer', color:accent }}>
              <TIcon name="plus" size={18} color={accent}/><span style={{ fontFamily:FONT, fontSize:14 }}>Add task</span>
            </button>
          </Card>
          <div style={{ fontFamily:MONO, fontSize:10.5, letterSpacing:'0.07em', textTransform:'uppercase', color:C.fg3, padding:'18px 4px 8px' }}>Done · {done.length}</div>
          <Card pad={0} style={{ overflow:'hidden' }}>
            {done.map(t => <TaskRow key={t.id} t={t} onToggle={toggleTask}/>)}
          </Card>
        </div>
      )}

      {tab === 'habits' && (
        <div style={{ padding:'0 16px 120px', display:'flex', flexDirection:'column', gap:11 }}>
          {habits.map(h => <HabitTrackRow key={h.id} h={h} onToggle={toggleHabit}/>)}
          <button style={{ width:'100%', border:`1px dashed ${C.line}`, background:'transparent', borderRadius:14, padding:'13px', color:accent, fontFamily:FONT, fontSize:14, cursor:'pointer', display:'flex', alignItems:'center', justifyContent:'center', gap:8 }}>
            <TIcon name="plus" size={17} color={accent}/> New habit
          </button>
        </div>
      )}

      {tab === 'focus' && (
        <div style={{ padding:'0 16px 120px' }}>
          <FocusTimer/>
          <div style={{ marginTop:22 }}>
            <div style={{ fontFamily:MONO, fontSize:10.5, letterSpacing:'0.07em', textTransform:'uppercase', color:C.fg3, padding:'4px 4px 10px' }}>Up next today</div>
            <div style={{ display:'flex', flexDirection:'column', gap:9 }}>
              {[['TypeScript — generics','09:30','cyan','bookOpen'],['Interview answers','12:00','blue','briefcase']].map(([t,tm,col,ic],i)=>(
                <Card key={i} pad={13} style={{ display:'flex', alignItems:'center', gap:11 }}>
                  <IconBadge name={ic} color={named(col)} size={34} isz={17}/>
                  <span style={{ flex:1, fontFamily:FONT, fontSize:14, color:C.fg }}>{t}</span>
                  <span style={{ fontFamily:MONO, fontSize:12, color:C.fg2 }}>{tm}</span>
                </Card>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

Object.assign(window, { ProductivityScreen });
