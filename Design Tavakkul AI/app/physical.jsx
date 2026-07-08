// Tafakkur AI — Physical sub-views: Sleep & rest, Sport & Gym
// Sleep modeled on AutoSleep / Sleep Cycle / Apple Health (score, hypnogram, stages, trend).
// Sport collects height/weight first → BMI + MET calorie math; added activities update the analysed totals.

// ═══ SLEEP & REST ════════════════════════════════════════════════════════════
function SleepScreen({ onBack }) {
  const S = TSLEEP;
  const total = S.stages.reduce((a,s)=>a+s.m,0);
  const W = 318, padX = 4;
  const laneY = { awake:14, rem:38, core:62, deep:86 };
  let cx = padX;
  const segs = S.stages.filter(s=>s.m>0).map(s => { const w = (s.m/total)*(W-padX*2); const seg = { ...s, x:cx, w }; cx += w; return seg; });
  const scoreCol = S.score>=80 ? '#2BB673' : S.score>=60 ? '#C9A24E' : '#E5746B';
  const r = 82, circ = 2*Math.PI*r;
  const weekMax = 9;
  const avg = (S.week.reduce((a,b)=>a+b,0)/S.week.length).toFixed(1);

  return (
    <div style={{ height:'100%', display:'flex', flexDirection:'column' }}>
      <BackBar title="Sleep & rest" onBack={onBack} right={<TIcon name="moonStars" size={19} color="#7C5CFF"/>}/>
      <div style={{ flex:1, overflowY:'auto', padding:'0 0 120px' }}>

        {/* score hero */}
        <div style={{ position:'relative', overflow:'hidden', padding:'18px 20px 6px', display:'flex', flexDirection:'column', alignItems:'center' }}>
          <div style={{ position:'absolute', top:-50, left:'50%', transform:'translateX(-50%)', width:420, height:300, borderRadius:'50%', background:'radial-gradient(closest-side, rgba(124,92,255,0.18), transparent 72%)', pointerEvents:'none' }}/>
          <div style={{ position:'relative', width:200, height:200, zIndex:1 }}>
            <svg width="200" height="200" style={{ transform:'rotate(-90deg)' }}>
              <circle cx="100" cy="100" r={r} fill="none" stroke="rgba(120,130,150,0.14)" strokeWidth="9"/>
              <circle cx="100" cy="100" r={r} fill="none" stroke={scoreCol} strokeWidth="9" strokeLinecap="round" strokeDasharray={circ} strokeDashoffset={circ*(1-S.score/100)} style={{ filter:`drop-shadow(0 0 7px ${softOf(scoreCol,0.55)})` }}/>
            </svg>
            <div style={{ position:'absolute', inset:0, display:'flex', flexDirection:'column', alignItems:'center', justifyContent:'center' }}>
              <div style={{ fontFamily:DISPLAY, fontSize:52, fontWeight:700, color:C.fg, lineHeight:1 }}>{S.score}</div>
              <div style={{ fontFamily:MONO, fontSize:10.5, letterSpacing:'0.12em', textTransform:'uppercase', color:scoreCol, marginTop:4 }}>Sleep score</div>
            </div>
          </div>
          <div style={{ fontFamily:DISPLAY, fontSize:24, fontWeight:700, color:C.fg, marginTop:8, zIndex:1 }}>{S.asleep} asleep</div>
          <div style={{ display:'flex', alignItems:'center', gap:18, marginTop:8, zIndex:1 }}>
            {[['bedDouble','Bedtime',S.bedtime],['sunrise','Wake',S.wake],['clock','In bed',S.inBed]].map(([ic,l,v],i)=>(
              <div key={i} style={{ display:'flex', alignItems:'center', gap:7 }}>
                <TIcon name={ic} size={15} color={C.fg3}/>
                <div><div style={{ fontFamily:FONT, fontSize:13.5, fontWeight:600, color:C.fg }}>{v}</div><div style={{ fontFamily:MONO, fontSize:9.5, color:C.fg3, textTransform:'uppercase', letterSpacing:'0.04em' }}>{l}</div></div>
              </div>
            ))}
          </div>
        </div>

        {/* hypnogram */}
        <div style={{ fontFamily:MONO, fontSize:10.5, letterSpacing:'0.08em', textTransform:'uppercase', color:C.fg3, padding:'22px 20px 10px' }}>Last night</div>
        <div style={{ margin:'0 16px' }}>
          <Card pad={16}>
            <svg width="100%" viewBox={`0 0 ${W} 104`} style={{ display:'block' }}>
              {['awake','rem','core','deep'].map((st)=>(
                <text key={st} x="0" y={laneY[st]+3} fill={C.fg3} style={{ fontFamily:MONO, fontSize:8 }}>{st==='awake'?'Awake':st==='rem'?'REM':st==='core'?'Core':'Deep'}</text>
              ))}
              {segs.map((s,i)=>(
                <rect key={i} x={s.x+30} y={laneY[s.s]-5} width={Math.max(s.w-2,2)} height="10" rx="3" fill={SLEEP_COL[s.s]}/>
              ))}
            </svg>
          </Card>
        </div>

        {/* stage breakdown */}
        <div style={{ padding:'14px 16px 0' }}>
          <Card pad={16}>
            {S.breakdown.map((b,i)=>(
              <div key={b.s} style={{ display:'flex', alignItems:'center', gap:12, padding:i?'12px 0 0':'0' }}>
                <span style={{ width:10, height:10, borderRadius:3, background:b.color, flexShrink:0 }}/>
                <div style={{ width:74, flexShrink:0 }}>
                  <div style={{ fontFamily:FONT, fontSize:13.5, fontWeight:600, color:C.fg }}>{b.label}</div>
                  <div style={{ fontFamily:MONO, fontSize:9.5, color:C.fg3 }}>{b.sub}</div>
                </div>
                <div style={{ flex:1 }}><ProgressBar value={b.pct} color={b.color} h={6}/></div>
                <div style={{ fontFamily:MONO, fontSize:12, color:C.fg2, width:54, textAlign:'right' }}>{Math.floor(b.min/60)}h {b.min%60}m</div>
              </div>
            ))}
          </Card>
        </div>

        {/* metrics */}
        <div style={{ display:'flex', gap:9, padding:'14px 16px 0' }}>
          {S.metrics.map((m,i)=>(
            <div key={i} style={{ flex:1, background:C.card, border:`0.5px solid ${C.sep}`, borderRadius:14, padding:'13px 8px', textAlign:'center' }}>
              <TIcon name={m.icon} size={17} color={m.color} sw={2} style={{ margin:'0 auto' }}/>
              <div style={{ fontFamily:DISPLAY, fontSize:19, fontWeight:700, color:C.fg, marginTop:5 }}>{m.value}<span style={{ fontFamily:MONO, fontSize:10, color:C.fg3, fontWeight:400 }}>{m.unit&&' '+m.unit}</span></div>
              <div style={{ fontFamily:FONT, fontSize:10.5, color:C.fg3 }}>{m.label}</div>
            </div>
          ))}
        </div>

        {/* weekly trend */}
        <div style={{ fontFamily:MONO, fontSize:10.5, letterSpacing:'0.08em', textTransform:'uppercase', color:C.fg3, padding:'22px 20px 10px' }}>This week · avg {avg}h</div>
        <div style={{ margin:'0 16px' }}>
          <Card pad={16}>
            <div style={{ position:'relative', display:'flex', alignItems:'flex-end', gap:8, height:108 }}>
              {/* goal line */}
              <div style={{ position:'absolute', left:0, right:0, bottom:`${(S.goalH/weekMax)*100}%`, borderTop:`1px dashed ${softOf('#7C5CFF',0.5)}`, zIndex:1 }}>
                <span style={{ position:'absolute', right:0, top:-14, fontFamily:MONO, fontSize:9, color:softOf('#7C5CFF',0.9) }}>{S.goalH}h goal</span>
              </div>
              {S.week.map((h,i)=>{
                const last = i===S.week.length-1;
                return (
                  <div key={i} style={{ flex:1, display:'flex', flexDirection:'column', alignItems:'center', gap:6, height:'100%', justifyContent:'flex-end' }}>
                    <div style={{ width:'78%', height:`${(h/weekMax)*100}%`, borderRadius:6, background: last?'#7C5CFF':softOf('#7C5CFF',0.4) }}/>
                    <span style={{ fontFamily:MONO, fontSize:9, color:last?'#9B86FF':C.fg4 }}>{'MTWTFSS'[i]}</span>
                  </div>
                );
              })}
            </div>
          </Card>
        </div>

        {/* AI insight */}
        <div style={{ padding:'16px 16px 0' }}>
          <ProactiveCard text={`Your sleep debt is ${S.debt}. You sleep best when you're in bed by 23:00 — and an earlier night makes Fajr in jamaat far easier. Want a wind-down reminder at 22:30?`}/>
        </div>
      </div>
    </div>
  );
}

// ═══ SPORT & GYM ═════════════════════════════════════════════════════════════
const SPORT_LS = 'tafakkur.sport.v1';
function loadSport() {
  try { const raw = localStorage.getItem(SPORT_LS); if (raw) { const o = JSON.parse(raw); if (o && typeof o==='object') return o; } } catch (e) {}
  return { profile: TSPORT_DEFAULT.profile, activities: TSPORT_DEFAULT.activities.slice() };
}
function bmiInfo(weight, height) {
  const b = weight / Math.pow(height/100, 2);
  let cat = 'Healthy', col = '#2BB673';
  if (b < 18.5) { cat = 'Underweight'; col = '#4CC2FF'; }
  else if (b >= 25 && b < 30) { cat = 'Overweight'; col = '#E0A93B'; }
  else if (b >= 30) { cat = 'Obese'; col = '#E5746B'; }
  return { value: b.toFixed(1), cat, col };
}

function Stepper({ value, set, min, max, step, unit }) {
  const btn = (dir) => () => set(Math.min(max, Math.max(min, value + dir*step)));
  return (
    <div style={{ display:'flex', alignItems:'center', justifyContent:'space-between', background:C.card, border:`0.5px solid ${C.sep}`, borderRadius:13, padding:'8px 10px' }}>
      <button onClick={btn(-1)} style={{ width:38, height:38, borderRadius:10, border:'none', background:'rgba(120,130,150,0.14)', cursor:'pointer', display:'flex', alignItems:'center', justifyContent:'center' }}><TIcon name="x" size={2} color="transparent"/><span style={{ fontFamily:DISPLAY, fontSize:24, color:C.fg2, lineHeight:0 }}>−</span></button>
      <div style={{ textAlign:'center' }}>
        <span style={{ fontFamily:DISPLAY, fontSize:26, fontWeight:700, color:C.fg }}>{value}</span>
        <span style={{ fontFamily:MONO, fontSize:12, color:C.fg3, marginLeft:4 }}>{unit}</span>
      </div>
      <button onClick={btn(1)} style={{ width:38, height:38, borderRadius:10, border:'none', background:'rgba(120,130,150,0.14)', cursor:'pointer', display:'flex', alignItems:'center', justifyContent:'center' }}><TIcon name="plus" size={18} color={C.fg2} sw={2.4}/></button>
    </div>
  );
}

function SportSetup({ initial, onSave, onCancel }) {
  const { accent } = useApp();
  const [height, setHeight] = React.useState(initial?.height ?? 175);
  const [weight, setWeight] = React.useState(initial?.weight ?? 72);
  const [age, setAge] = React.useState(initial?.age ?? 24);
  const [goal, setGoal] = React.useState(initial?.goal ?? 'Stay active');
  const bmi = bmiInfo(weight, height);
  return (
    <div style={{ height:'100%', display:'flex', flexDirection:'column' }}>
      <BackBar title={initial?'Edit profile':'Sport & Gym'} onBack={onCancel}/>
      <div style={{ flex:1, overflowY:'auto', padding:'10px 16px 40px' }}>
        <div style={{ textAlign:'center', padding:'10px 10px 22px' }}>
          <div style={{ width:64, height:64, borderRadius:'50%', background:softOf('#A8C36A',0.16), display:'flex', alignItems:'center', justifyContent:'center', margin:'0 auto 14px' }}>
            <TIcon name="dumbbell" size={30} color="#A8C36A"/>
          </div>
          <h1 style={{ fontFamily:DISPLAY, fontSize:24, fontWeight:700, color:C.fg, margin:'0 0 6px' }}>{initial?'Update your details':"Let's set you up"}</h1>
          <p style={{ fontFamily:FONT, fontSize:14, color:C.fg2, lineHeight:1.5, margin:0, maxWidth:300, marginLeft:'auto', marginRight:'auto' }}>Your height and weight let Murabbiy calculate calories and track your training accurately.</p>
        </div>

        <div style={{ display:'flex', flexDirection:'column', gap:14 }}>
          <div>
            <div style={{ fontFamily:MONO, fontSize:10.5, letterSpacing:'0.06em', textTransform:'uppercase', color:C.fg3, marginBottom:8 }}>Height</div>
            <Stepper value={height} set={setHeight} min={140} max={215} step={1} unit="cm"/>
          </div>
          <div>
            <div style={{ fontFamily:MONO, fontSize:10.5, letterSpacing:'0.06em', textTransform:'uppercase', color:C.fg3, marginBottom:8 }}>Weight</div>
            <Stepper value={weight} set={setWeight} min={40} max={160} step={1} unit="kg"/>
          </div>
          <div>
            <div style={{ fontFamily:MONO, fontSize:10.5, letterSpacing:'0.06em', textTransform:'uppercase', color:C.fg3, marginBottom:8 }}>Age</div>
            <Stepper value={age} set={setAge} min={12} max={90} step={1} unit="yrs"/>
          </div>
          <div>
            <div style={{ fontFamily:MONO, fontSize:10.5, letterSpacing:'0.06em', textTransform:'uppercase', color:C.fg3, marginBottom:8 }}>Goal</div>
            <div style={{ display:'flex', gap:8 }}>
              {TSPORT_GOALS.map(g => {
                const on = goal === g;
                return <button key={g} onClick={()=>setGoal(g)} style={{ flex:1, padding:'12px 6px', borderRadius:12, border:`1px solid ${on?accent:C.sep}`, background:on?softOf(accent,0.14):C.card, color:on?C.fg:C.fg2, fontFamily:FONT, fontSize:12.5, fontWeight:on?600:500, cursor:'pointer' }}>{g}</button>;
              })}
            </div>
          </div>

          {/* live BMI preview */}
          <div style={{ display:'flex', alignItems:'center', gap:13, padding:'14px 16px', borderRadius:14, background:softOf(bmi.col,0.08), border:`0.5px solid ${softOf(bmi.col,0.4)}` }}>
            <div style={{ fontFamily:DISPLAY, fontSize:30, fontWeight:700, color:bmi.col }}>{bmi.value}</div>
            <div><div style={{ fontFamily:FONT, fontSize:13.5, fontWeight:600, color:C.fg }}>BMI · {bmi.cat}</div><div style={{ fontFamily:MONO, fontSize:11, color:C.fg3 }}>body mass index</div></div>
          </div>
        </div>

        <button onClick={()=>onSave({ height, weight, age, goal })} style={{ width:'100%', marginTop:22, background:accent, color:'#fff', border:'none', borderRadius:13, padding:'14px', fontFamily:FONT, fontSize:15, fontWeight:600, cursor:'pointer' }}>{initial?'Save changes':'Start training'}</button>
      </div>
    </div>
  );
}

function AddActivitySheet({ weight, onAdd, onClose }) {
  const { accent } = useApp();
  const [type, setType] = React.useState('run');
  const [dur, setDur] = React.useState(30);
  const t = TSPORT_TYPES.find(x=>x.key===type);
  const kcal = Math.round(t.met * weight * (dur/60));
  return (
    <div style={{ position:'absolute', inset:0, zIndex:50 }}>
      <div onClick={onClose} style={{ position:'absolute', inset:0, background:'rgba(7,9,13,0.6)', backdropFilter:'blur(3px)' }}/>
      <div style={{ position:'absolute', left:0, right:0, bottom:0, background:C.bg1, borderRadius:'22px 22px 0 0', borderTop:`0.5px solid ${C.line}`, padding:'10px 18px 26px', maxHeight:'88%', overflowY:'auto' }}>
        <div style={{ width:38, height:5, borderRadius:3, background:'rgba(255,255,255,0.18)', margin:'0 auto 16px' }}/>
        <div style={{ fontFamily:DISPLAY, fontSize:20, fontWeight:700, color:C.fg, marginBottom:4 }}>Add activity</div>
        <div style={{ fontFamily:FONT, fontSize:13, color:C.fg2, marginBottom:16 }}>Logged sessions update your calories, active minutes and Physical balance.</div>

        <div style={{ display:'grid', gridTemplateColumns:'1fr 1fr 1fr', gap:9, marginBottom:18 }}>
          {TSPORT_TYPES.map(a => {
            const on = a.key === type;
            return (
              <button key={a.key} onClick={()=>setType(a.key)} style={{ display:'flex', flexDirection:'column', alignItems:'center', gap:7, padding:'14px 6px', borderRadius:14, border:`1px solid ${on?a.color:C.sep}`, background:on?softOf(a.color,0.14):C.card, cursor:'pointer' }}>
                <TIcon name={a.icon} size={22} color={on?a.color:C.fg2} sw={1.8}/>
                <span style={{ fontFamily:FONT, fontSize:12, fontWeight:on?600:500, color:on?C.fg:C.fg2 }}>{a.name}</span>
              </button>
            );
          })}
        </div>

        <div style={{ fontFamily:MONO, fontSize:10.5, letterSpacing:'0.06em', textTransform:'uppercase', color:C.fg3, marginBottom:9 }}>Duration</div>
        <div style={{ display:'flex', gap:8, marginBottom:20 }}>
          {TSPORT_DURATIONS.map(d => {
            const on = d === dur;
            return <button key={d} onClick={()=>setDur(d)} style={{ flex:1, padding:'10px 0', borderRadius:11, border:`1px solid ${on?accent:C.sep}`, background:on?softOf(accent,0.16):C.card, color:on?C.fg:C.fg2, fontFamily:MONO, fontSize:13, fontWeight:on?600:500, cursor:'pointer' }}>{d}m</button>;
          })}
        </div>

        <div style={{ display:'flex', alignItems:'center', justifyContent:'center', gap:8, padding:'14px', borderRadius:14, background:softOf(t.color,0.1), border:`0.5px solid ${softOf(t.color,0.4)}`, marginBottom:18 }}>
          <TIcon name="flame" size={18} color={t.color}/>
          <span style={{ fontFamily:DISPLAY, fontSize:24, fontWeight:700, color:C.fg }}>{kcal}</span>
          <span style={{ fontFamily:FONT, fontSize:13, color:C.fg2 }}>kcal · {dur} min {t.name.toLowerCase()}</span>
        </div>

        <button onClick={()=>onAdd({ id:'a'+Date.now(), key:t.key, name:t.name, icon:t.icon, color:t.color, min:dur, kcal, time:'now' })} style={{ width:'100%', background:accent, color:'#fff', border:'none', borderRadius:13, padding:'14px', fontFamily:FONT, fontSize:15, fontWeight:600, cursor:'pointer' }}>Add to today</button>
      </div>
    </div>
  );
}

function SportGymScreen({ onBack }) {
  const { accent } = useApp();
  const [data, setData] = React.useState(loadSport);
  const [mode, setMode] = React.useState(data.profile ? 'dash' : 'setup');
  const [showAdd, setShowAdd] = React.useState(false);
  const persist = (next) => { setData(next); try { localStorage.setItem(SPORT_LS, JSON.stringify(next)); } catch (e) {} };

  if (mode === 'setup' || !data.profile) {
    return <SportSetup initial={data.profile}
      onSave={(p)=>{ persist({ ...data, profile:p }); setMode('dash'); }}
      onCancel={()=>{ data.profile ? setMode('dash') : onBack(); }}/>;
  }

  const P = data.profile;
  const bmi = bmiInfo(P.weight, P.height);
  const acts = data.activities;
  const kcal = acts.reduce((a,x)=>a+x.kcal, 0);
  const mins = acts.reduce((a,x)=>a+x.min, 0);
  const KCAL_GOAL = 500, MIN_GOAL = 45;
  const kcalFrac = Math.min(kcal/KCAL_GOAL, 1), minFrac = Math.min(mins/MIN_GOAL, 1);
  const week = TSPORT_DEFAULT.weekMin.slice(); week[week.length-1] = mins;
  const weekMax = Math.max(90, ...week);
  const r = 64, circ = 2*Math.PI*r;
  const balanceBoost = Math.round(minFrac * 12); // how much today's training lifts the Physical aspect

  const addActivity = (a) => { persist({ ...data, activities: [a, ...acts] }); setShowAdd(false); };
  const removeActivity = (id) => persist({ ...data, activities: acts.filter(x=>x.id!==id) });

  return (
    <div style={{ position:'relative', height:'100%', display:'flex', flexDirection:'column' }}>
      <BackBar title="Sport & Gym" onBack={onBack} right={<button onClick={()=>setMode('setup')} style={{ background:'none', border:'none', cursor:'pointer', padding:0 }}><TIcon name="settings" size={19} color={accent}/></button>}/>
      <div style={{ flex:1, overflowY:'auto', padding:'0 0 120px' }}>

        {/* BMI / profile */}
        <div style={{ padding:'14px 16px 0' }}>
          <Card pad={16} style={{ display:'flex', alignItems:'center', gap:14 }}>
            <div style={{ width:54, height:54, borderRadius:14, background:softOf(bmi.col,0.14), display:'flex', flexDirection:'column', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
              <span style={{ fontFamily:DISPLAY, fontSize:20, fontWeight:700, color:bmi.col, lineHeight:1 }}>{bmi.value}</span>
              <span style={{ fontFamily:MONO, fontSize:8, color:bmi.col, marginTop:2 }}>BMI</span>
            </div>
            <div style={{ flex:1 }}>
              <div style={{ fontFamily:FONT, fontSize:15, fontWeight:600, color:C.fg }}>{bmi.cat} · {P.goal}</div>
              <div style={{ fontFamily:MONO, fontSize:12, color:C.fg2, marginTop:2 }}>{P.height} cm · {P.weight} kg · {P.age} yrs</div>
            </div>
          </Card>
        </div>

        {/* today's rings */}
        <div style={{ fontFamily:MONO, fontSize:10.5, letterSpacing:'0.08em', textTransform:'uppercase', color:C.fg3, padding:'22px 20px 10px' }}>Today's training</div>
        <div style={{ padding:'0 16px' }}>
          <Card pad={18} style={{ display:'flex', alignItems:'center', gap:18 }}>
            <div style={{ position:'relative', width:148, height:148, flexShrink:0 }}>
              <svg width="148" height="148" style={{ transform:'rotate(-90deg)' }}>
                <circle cx="74" cy="74" r={r} fill="none" stroke="rgba(120,130,150,0.14)" strokeWidth="10"/>
                <circle cx="74" cy="74" r={r} fill="none" stroke="#E5746B" strokeWidth="10" strokeLinecap="round" strokeDasharray={circ} strokeDashoffset={circ*(1-kcalFrac)} style={{ transition:'stroke-dashoffset .5s cubic-bezier(0.16,1,0.3,1)' }}/>
                <circle cx="74" cy="74" r={r-15} fill="none" stroke="rgba(120,130,150,0.14)" strokeWidth="10"/>
                <circle cx="74" cy="74" r={r-15} fill="none" stroke="#A8C36A" strokeWidth="10" strokeLinecap="round" strokeDasharray={2*Math.PI*(r-15)} strokeDashoffset={2*Math.PI*(r-15)*(1-minFrac)} style={{ transition:'stroke-dashoffset .5s cubic-bezier(0.16,1,0.3,1)' }}/>
              </svg>
              <div style={{ position:'absolute', inset:0, display:'flex', flexDirection:'column', alignItems:'center', justifyContent:'center' }}>
                <div style={{ fontFamily:DISPLAY, fontSize:28, fontWeight:700, color:C.fg, lineHeight:1 }}>{kcal}</div>
                <div style={{ fontFamily:MONO, fontSize:9, color:C.fg3, letterSpacing:'0.06em' }}>KCAL</div>
              </div>
            </div>
            <div style={{ flex:1, display:'flex', flexDirection:'column', gap:14 }}>
              <div>
                <div style={{ display:'flex', alignItems:'center', gap:7 }}><span style={{ width:9, height:9, borderRadius:3, background:'#E5746B' }}/><span style={{ fontFamily:FONT, fontSize:13, color:C.fg2 }}>Calories</span></div>
                <div style={{ fontFamily:DISPLAY, fontSize:20, fontWeight:700, color:C.fg }}>{kcal}<span style={{ fontFamily:MONO, fontSize:12, color:C.fg3, fontWeight:400 }}> / {KCAL_GOAL}</span></div>
              </div>
              <div>
                <div style={{ display:'flex', alignItems:'center', gap:7 }}><span style={{ width:9, height:9, borderRadius:3, background:'#A8C36A' }}/><span style={{ fontFamily:FONT, fontSize:13, color:C.fg2 }}>Active min</span></div>
                <div style={{ fontFamily:DISPLAY, fontSize:20, fontWeight:700, color:C.fg }}>{mins}<span style={{ fontFamily:MONO, fontSize:12, color:C.fg3, fontWeight:400 }}> / {MIN_GOAL}</span></div>
              </div>
              <div style={{ fontFamily:MONO, fontSize:11, color:'#A8C36A' }}>{acts.length} session{acts.length!==1?'s':''} · +{balanceBoost} Physical balance</div>
            </div>
          </Card>
        </div>

        {/* add */}
        <div style={{ padding:'14px 16px 0' }}>
          <button onClick={()=>setShowAdd(true)} style={{ width:'100%', background:accent, color:'#fff', border:'none', borderRadius:13, padding:'13px', fontFamily:FONT, fontSize:15, fontWeight:600, cursor:'pointer', display:'flex', alignItems:'center', justifyContent:'center', gap:8 }}>
            <TIcon name="plus" size={18} color="#fff" sw={2.2}/> Add activity
          </button>
        </div>

        {/* today's activities */}
        <div style={{ fontFamily:MONO, fontSize:10.5, letterSpacing:'0.08em', textTransform:'uppercase', color:C.fg3, padding:'22px 20px 10px' }}>Logged today</div>
        <div style={{ padding:'0 16px' }}>
          {acts.length === 0 ? (
            <div style={{ textAlign:'center', padding:'24px', fontFamily:FONT, fontSize:13.5, color:C.fg3, border:`1px dashed ${C.line}`, borderRadius:14 }}>No sessions yet. Add one above.</div>
          ) : (
            <div style={{ display:'flex', flexDirection:'column', gap:10 }}>
              {acts.map(a => (
                <Card key={a.id} pad={14} style={{ display:'flex', alignItems:'center', gap:13 }}>
                  <div style={{ width:42, height:42, borderRadius:12, background:softOf(a.color,0.14), display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
                    <TIcon name={a.icon} size={20} color={a.color}/>
                  </div>
                  <div style={{ flex:1, minWidth:0 }}>
                    <div style={{ fontFamily:FONT, fontSize:14.5, fontWeight:600, color:C.fg }}>{a.name}</div>
                    <div style={{ fontFamily:MONO, fontSize:11.5, color:C.fg3 }}>{a.min} min · {a.time}</div>
                  </div>
                  <div style={{ display:'flex', alignItems:'center', gap:5, color:'#E5746B' }}>
                    <TIcon name="flame" size={14} color="#E5746B"/>
                    <span style={{ fontFamily:MONO, fontSize:13, fontWeight:600 }}>{a.kcal}</span>
                  </div>
                  <button onClick={()=>removeActivity(a.id)} style={{ background:'none', border:'none', cursor:'pointer', padding:'4px' }}><TIcon name="x" size={15} color={C.fg4}/></button>
                </Card>
              ))}
            </div>
          )}
        </div>

        {/* weekly */}
        <div style={{ fontFamily:MONO, fontSize:10.5, letterSpacing:'0.08em', textTransform:'uppercase', color:C.fg3, padding:'22px 20px 10px' }}>Active minutes · this week</div>
        <div style={{ margin:'0 16px' }}>
          <Card pad={16}>
            <div style={{ display:'flex', alignItems:'flex-end', gap:8, height:96 }}>
              {week.map((m,i)=>{
                const last = i===week.length-1;
                return (
                  <div key={i} style={{ flex:1, display:'flex', flexDirection:'column', alignItems:'center', gap:6, height:'100%', justifyContent:'flex-end' }}>
                    <div style={{ width:'72%', height:`${Math.max((m/weekMax)*100,3)}%`, borderRadius:6, background:last?'#A8C36A':softOf('#A8C36A',0.4) }}/>
                    <span style={{ fontFamily:MONO, fontSize:9, color:last?'#A8C36A':C.fg4 }}>{'MTWTFSS'[i]}</span>
                  </div>
                );
              })}
            </div>
          </Card>
        </div>

        {/* AI insight */}
        <div style={{ padding:'16px 16px 0' }}>
          <ProactiveCard text={`At ${P.weight} kg, a 30-minute run burns about ${Math.round(9.8*P.weight*0.5)} kcal. You're most consistent training before Dhuhr — want me to block it into your mornings?`}/>
        </div>
      </div>

      {showAdd && <AddActivitySheet weight={P.weight} onAdd={addActivity} onClose={()=>setShowAdd(false)}/>}
    </div>
  );
}

Object.assign(window, { SleepScreen, SportGymScreen });
