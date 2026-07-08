// Tafakkur AI — Focus view (immersive deep-work session, Salah-aware)

const FOCUS_PRESETS = [
  { key:'pomo', label:'Pomodoro', min:25, sub:'Classic' },
  { key:'deep', label:'Deep',     min:50, sub:'Focused' },
  { key:'flow', label:'Flow',     min:90, sub:'Long' },
];
const FOCUS_SOUNDS = [
  { key:'off',   label:'Silent',  icon:'moon' },
  { key:'rain',  label:'Rain',    icon:'activity' },
  { key:'quran', label:'Qurʼan',  icon:'bookOpen' },
  { key:'cafe',  label:'Cafe',    icon:'coffee' },
];

function FocusScreen({ onBack }) {
  const { accent } = useApp();
  const [presetMin, setPresetMin] = React.useState(50);
  const [task, setTask] = React.useState('Deep work — API refactor');
  const [sound, setSound] = React.useState('quran');
  const [left, setLeft] = React.useState(50*60);
  const [running, setRunning] = React.useState(false);
  const [started, setStarted] = React.useState(false);
  const [pickTask, setPickTask] = React.useState(false);
  const [doneCount, setDoneCount] = React.useState(3);

  // keep timer synced to preset when not started
  React.useEffect(() => { if (!started) setLeft(presetMin*60); }, [presetMin, started]);
  React.useEffect(() => {
    if (!running) return;
    const id = setInterval(() => setLeft(l => {
      if (l <= 1) { clearInterval(id); setRunning(false); setStarted(false); setDoneCount(c=>c+1); return presetMin*60; }
      return l-1;
    }), 1000);
    return () => clearInterval(id);
  }, [running, presetMin]);

  const total = presetMin*60;
  const frac = 1 - left/total;
  const mm = String(Math.floor(left/60)).padStart(2,'0');
  const ss = String(left%60).padStart(2,'0');
  const r = 130, circ = 2*Math.PI*r;
  const begin = () => { setStarted(true); setRunning(true); };
  const reset = () => { setRunning(false); setStarted(false); setLeft(presetMin*60); };

  return (
    <div style={{ position:'relative', height:'100%', display:'flex', flexDirection:'column', overflow:'hidden' }}>
      {/* ambient — intensifies while running */}
      <div style={{ position:'absolute', top:'18%', left:'50%', transform:'translateX(-50%)', width:520, height:520, borderRadius:'50%', background:`radial-gradient(closest-side, ${softOf(accent, running?0.22:0.12)}, transparent 70%)`, pointerEvents:'none', transition:'all .6s ease', zIndex:0 }}/>

      <BackBar title={started ? '' : 'Focus'} onBack={onBack} right={!started && <TIcon name="bell" size={19} color={accent}/>}/>

      <div style={{ flex:1, overflowY:'auto', position:'relative', zIndex:1, display:'flex', flexDirection:'column', alignItems:'center', padding:'8px 20px 40px' }}>

        {/* Salah-aware hint */}
        {!started && (
          <div style={{ display:'inline-flex', alignItems:'center', gap:7, background:softOf(C.gold,0.1), border:`0.5px solid ${softOf(C.gold,0.35)}`, borderRadius:999, padding:'7px 13px', marginBottom:8 }}>
            <TIcon name="sun" size={13} color={C.gold}/>
            <span style={{ fontFamily:FONT, fontSize:12.5, color:C.fg2 }}><b style={{ color:C.fg, fontWeight:600 }}>{TNEXT.in}</b> until {TNEXT.name} — time for one deep block</span>
          </div>
        )}

        {/* timer ring */}
        <div style={{ position:'relative', width:300, height:300, margin:'14px 0 6px' }}>
          {started && running && (
            <div className="tf-breathe" style={{ position:'absolute', inset:18, borderRadius:'50%', background:`radial-gradient(circle, ${softOf(accent,0.18)}, transparent 70%)` }}/>
          )}
          <svg width="300" height="300" style={{ transform:'rotate(-90deg)', position:'relative' }}>
            <circle cx="150" cy="150" r={r} fill="none" stroke="rgba(120,130,150,0.12)" strokeWidth="6"/>
            <circle cx="150" cy="150" r={r} fill="none" stroke={accent} strokeWidth="6" strokeLinecap="round" strokeDasharray={circ} strokeDashoffset={circ*(1-frac)} style={{ transition:'stroke-dashoffset 1s linear', filter:`drop-shadow(0 0 8px ${softOf(accent,0.6)})` }}/>
          </svg>
          <div style={{ position:'absolute', inset:0, display:'flex', flexDirection:'column', alignItems:'center', justifyContent:'center' }}>
            <div style={{ fontFamily:MONO, fontSize:64, fontWeight:600, color:C.fg, letterSpacing:'-0.02em', lineHeight:1 }}>{mm}:{ss}</div>
            <div style={{ fontFamily:MONO, fontSize:11, letterSpacing:'0.14em', textTransform:'uppercase', color:C.fg3, marginTop:8 }}>{started ? (running?'In focus':'Paused') : `${presetMin} min session`}</div>
          </div>
        </div>

        {/* presets (hidden once started) */}
        {!started && (
          <div style={{ display:'flex', gap:9, margin:'14px 0 4px', width:'100%' }}>
            {FOCUS_PRESETS.map(p => {
              const on = p.min === presetMin;
              return (
                <button key={p.key} onClick={()=>setPresetMin(p.min)} style={{ flex:1, padding:'12px 6px', borderRadius:14, border:`1px solid ${on?accent:C.sep}`, background:on?softOf(accent,0.14):C.card, cursor:'pointer' }}>
                  <div style={{ fontFamily:DISPLAY, fontSize:19, fontWeight:700, color:on?C.fg:C.fg2 }}>{p.min}</div>
                  <div style={{ fontFamily:FONT, fontSize:11, color:on?accent:C.fg3, fontWeight:600 }}>{p.label}</div>
                </button>
              );
            })}
          </div>
        )}

        {/* controls */}
        <div style={{ display:'flex', alignItems:'center', gap:14, margin:'18px 0 4px' }}>
          {started && (
            <button onClick={reset} style={{ width:54, height:54, borderRadius:'50%', border:`0.5px solid ${C.line}`, background:C.card, cursor:'pointer', display:'flex', alignItems:'center', justifyContent:'center' }}><TIcon name="repeat" size={20} color={C.fg2}/></button>
          )}
          <button onClick={()=> started ? setRunning(r=>!r) : begin()} style={{ width:72, height:72, borderRadius:'50%', border:'none', background:accent, cursor:'pointer', display:'flex', alignItems:'center', justifyContent:'center', boxShadow:`0 8px 24px ${softOf(accent,0.55)}` }}>
            <TIcon name={started && running ? 'pause' : 'play'} size={30} color="#fff" sw={2}/>
          </button>
          {started && (
            <button onClick={reset} style={{ width:54, height:54, borderRadius:'50%', border:`0.5px solid ${C.line}`, background:C.card, cursor:'pointer', display:'flex', alignItems:'center', justifyContent:'center' }}><TIcon name="check" size={22} color={C.green} sw={2.4}/></button>
          )}
        </div>
        {!started && <div style={{ fontFamily:FONT, fontSize:13, color:C.fg3, marginTop:2 }}>Tap to begin</div>}

        {/* focusing on (task) */}
        <div style={{ width:'100%', marginTop:24 }}>
          <div style={{ fontFamily:MONO, fontSize:10.5, letterSpacing:'0.08em', textTransform:'uppercase', color:C.fg3, padding:'0 2px 9px' }}>Focusing on</div>
          <button onClick={()=>setPickTask(p=>!p)} style={{ width:'100%', textAlign:'left', display:'flex', alignItems:'center', gap:12, padding:'14px 15px', borderRadius:14, background:C.card, border:`0.5px solid ${C.sep}`, cursor:'pointer' }}>
            <div style={{ width:36, height:36, borderRadius:10, background:softOf(accent,0.14), display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}><TIcon name="target" size={18} color={accent}/></div>
            <span style={{ flex:1, fontFamily:FONT, fontSize:14.5, fontWeight:600, color:C.fg }}>{task}</span>
            <TIcon name={pickTask?'chevronUp':'chevronDown'} size={18} color={C.fg3}/>
          </button>
          {pickTask && (
            <div style={{ marginTop:9, display:'flex', flexDirection:'column', gap:8 }}>
              {TTASKS.filter(t=>!t.done).map(t => (
                <button key={t.id} onClick={()=>{ setTask(t.title); setPickTask(false); }} style={{ textAlign:'left', display:'flex', alignItems:'center', gap:10, padding:'11px 14px', borderRadius:12, background:task===t.title?softOf(accent,0.1):C.card2, border:`0.5px solid ${task===t.title?accent:C.sep}`, cursor:'pointer' }}>
                  <span style={{ width:7, height:7, borderRadius:4, background:t.priority==='high'?C.danger:t.priority==='med'?C.gold:C.fg3, flexShrink:0 }}/>
                  <span style={{ flex:1, fontFamily:FONT, fontSize:13.5, color:C.fg }}>{t.title}</span>
                  <span style={{ fontFamily:MONO, fontSize:10, color:C.fg3 }}>{t.tag}</span>
                </button>
              ))}
            </div>
          )}
        </div>

        {/* ambient sound */}
        <div style={{ width:'100%', marginTop:22 }}>
          <div style={{ fontFamily:MONO, fontSize:10.5, letterSpacing:'0.08em', textTransform:'uppercase', color:C.fg3, padding:'0 2px 9px' }}>Ambient sound</div>
          <div style={{ display:'flex', gap:8 }}>
            {FOCUS_SOUNDS.map(s => {
              const on = s.key === sound;
              return (
                <button key={s.key} onClick={()=>setSound(s.key)} style={{ flex:1, display:'flex', flexDirection:'column', alignItems:'center', gap:6, padding:'12px 4px', borderRadius:13, background:on?softOf(accent,0.14):C.card, border:`1px solid ${on?accent:C.sep}`, cursor:'pointer' }}>
                  <TIcon name={s.icon} size={18} color={on?accent:C.fg2}/>
                  <span style={{ fontFamily:FONT, fontSize:11, fontWeight:on?600:500, color:on?C.fg:C.fg2 }}>{s.label}</span>
                </button>
              );
            })}
          </div>
        </div>

        {/* today's focus */}
        <div style={{ width:'100%', marginTop:22 }}>
          <Card pad={16} style={{ display:'flex', alignItems:'center', gap:14 }}>
            <div style={{ display:'flex', gap:5 }}>
              {[0,1,2,3].map(i => <span key={i} style={{ width:10, height:10, borderRadius:3, background:i<doneCount?accent:'rgba(120,130,150,0.2)' }}/>)}
            </div>
            <div style={{ flex:1 }}>
              <div style={{ fontFamily:FONT, fontSize:14, fontWeight:600, color:C.fg }}>{doneCount} of 4 sessions</div>
              <div style={{ fontFamily:MONO, fontSize:11.5, color:C.fg3 }}>{Math.floor(doneCount*25/60)}h {(doneCount*25)%60}m focused today</div>
            </div>
            <TIcon name="flame" size={18} color={C.gold}/>
          </Card>
        </div>
      </div>
    </div>
  );
}

Object.assign(window, { FocusScreen });
