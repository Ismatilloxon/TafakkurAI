// Tafakkur AI — Spiritual sub-views: Prayer tracker, Tafakkur journal, Gratitude, Qurʼan & dhikr
// Patterns drawn from Uzbek Islamic apps (Azon Times, Hilol, Muslim Board): jamaat/on-time states,
// qaza counter, streak grid, tasbih counter, adhkar, last-read.

const ARABIC = "'SF Arabic', 'Geeza Pro', 'Scheherazade New', 'Times New Roman', serif";
const GOLD = '#C9A24E';

// ═══ 1 · PRAYER TRACKER ══════════════════════════════════════════════════════
const PRAYER_CYCLE = ['jamaat','ontime','late','qada'];

function PrayerTrackerScreen({ onBack }) {
  const [states, setStates] = React.useState(() => TPRAYER_TODAY.map(p => p.state));
  const cycle = (i) => setStates(s => s.map((v,j) => {
    if (j !== i) return v;
    if (v === 'upcoming') return 'jamaat';
    const k = PRAYER_CYCLE.indexOf(v);
    return PRAYER_CYCLE[(k+1) % PRAYER_CYCLE.length];
  }));
  const logged = states.filter(s => s !== 'upcoming' && s !== 'qada').length;

  const gridColor = (c) => {
    if (c === 5) return GOLD;
    if (c === 4) return softOf(GOLD, 0.7);
    if (c >= 2) return softOf(GOLD, 0.4);
    if (c === 1) return softOf(GOLD, 0.22);
    return 'rgba(120,130,150,0.12)';
  };

  return (
    <div style={{ height:'100%', display:'flex', flexDirection:'column' }}>
      <BackBar title="Prayer tracker" onBack={onBack} right={<TIcon name="calendar" size={19} color={GOLD}/>}/>
      <div style={{ flex:1, overflowY:'auto', padding:'0 0 120px' }}>

        {/* hero — next prayer */}
        <div style={{ position:'relative', overflow:'hidden', padding:'20px 20px 16px', textAlign:'center' }}>
          <AmbientGlow color={GOLD} top={-70} opacity={0.5}/>
          <div style={{ position:'relative', zIndex:1 }}>
            <div style={{ fontFamily:ARABIC, fontSize:30, color:GOLD, direction:'rtl', marginBottom:4 }}>الصلاة</div>
            <div style={{ fontFamily:MONO, fontSize:11, letterSpacing:'0.14em', textTransform:'uppercase', color:C.fg3 }}>Next · {TNEXT.name} in {TNEXT.in}</div>
            <div style={{ fontFamily:DISPLAY, fontSize:34, fontWeight:700, color:C.fg, margin:'6px 0 2px' }}>{logged} of 5 prayed</div>
            <div style={{ fontFamily:FONT, fontSize:13, color:C.fg2 }}>{THIJRI} · {TCITY}</div>
          </div>
        </div>

        {/* stats */}
        <div style={{ display:'flex', gap:9, padding:'0 16px 18px' }}>
          {[['flame',`${TPRAYER_STATS.streak}`,'day streak',GOLD],['users',`${TPRAYER_STATS.jamaat}%`,'jamaat',C.green],['check',`${TPRAYER_STATS.onTime}%`,'on time',named('cyan')]].map(([ic,v,l,col],i)=>(
            <div key={i} style={{ flex:1, background:C.card, border:`0.5px solid ${C.sep}`, borderRadius:14, padding:'12px 8px', textAlign:'center' }}>
              <TIcon name={ic} size={17} color={col} sw={2} style={{ margin:'0 auto' }}/>
              <div style={{ fontFamily:DISPLAY, fontSize:18, fontWeight:700, color:C.fg, marginTop:4 }}>{v}</div>
              <div style={{ fontFamily:FONT, fontSize:10.5, color:C.fg3 }}>{l}</div>
            </div>
          ))}
        </div>

        {/* today's five */}
        <div style={{ fontFamily:MONO, fontSize:10.5, letterSpacing:'0.08em', textTransform:'uppercase', color:C.fg3, padding:'0 20px 9px' }}>Today · tap to log</div>
        <div style={{ margin:'0 16px', background:C.card, border:`0.5px solid ${C.sep}`, borderRadius:16, overflow:'hidden' }}>
          {TPRAYER_TODAY.map((p,i) => {
            const st = PRAYER_STATES[states[i]];
            const done = states[i] !== 'upcoming';
            return (
              <div key={p.key} style={{ display:'flex', alignItems:'center', gap:13, padding:'13px 15px', borderBottom:i<TPRAYER_TODAY.length-1?`0.5px solid ${C.sep}`:'none' }}>
                <div style={{ width:38, height:38, borderRadius:11, background:softOf(st.color,0.14), display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
                  <TIcon name={TPRAYERS.find(x=>x.name===p.name).icon} size={19} color={st.color}/>
                </div>
                <div style={{ flex:1, minWidth:0 }}>
                  <div style={{ display:'flex', alignItems:'baseline', gap:8 }}>
                    <span style={{ fontFamily:FONT, fontSize:15, fontWeight:600, color:C.fg }}>{p.name}</span>
                    <span style={{ fontFamily:ARABIC, fontSize:15, color:C.fg3, direction:'rtl' }}>{p.arabic}</span>
                  </div>
                  <div style={{ fontFamily:MONO, fontSize:12, color:C.fg3, marginTop:1 }}>{p.time}</div>
                </div>
                <button onClick={()=>cycle(i)} style={{ display:'flex', alignItems:'center', gap:7, padding:'7px 11px', borderRadius:999, border:`1px solid ${done?st.color:C.line}`, background:done?softOf(st.color,0.14):'transparent', cursor:'pointer' }}>
                  {done
                    ? <TIcon name={st.icon} size={13} color={st.color}/>
                    : <span style={{ width:13, height:13, borderRadius:'50%', border:`1.6px solid ${C.fg3}` }}/>}
                  <span style={{ fontFamily:FONT, fontSize:12.5, fontWeight:600, color:done?st.color:C.fg3 }}>{done?st.label:'Log'}</span>
                </button>
              </div>
            );
          })}
        </div>

        {/* AI nudge */}
        <div style={{ padding:'16px 16px 0' }}>
          <ProactiveCard text="You pray Fajr in jamaat 6 days a week — masha'Allah. Asr is your most-missed prayer; want a reminder 15 minutes before?" compact/>
        </div>

        {/* month grid */}
        <div style={{ fontFamily:MONO, fontSize:10.5, letterSpacing:'0.08em', textTransform:'uppercase', color:C.fg3, padding:'24px 20px 10px' }}>Last 5 weeks · {TPRAYER_STATS.monthPct}% complete</div>
        <div style={{ margin:'0 16px', background:C.card, border:`0.5px solid ${C.sep}`, borderRadius:16, padding:'16px' }}>
          <div style={{ display:'grid', gridTemplateColumns:'repeat(7, 1fr)', gap:7 }}>
            {['M','T','W','T','F','S','S'].map((d,i)=>(<div key={i} style={{ textAlign:'center', fontFamily:MONO, fontSize:9, color:C.fg4 }}>{d}</div>))}
            {TPRAYER_MONTH.map((c,i)=>(
              <div key={i} style={{ aspectRatio:'1', borderRadius:6, background:gridColor(c), display:'flex', alignItems:'center', justifyContent:'center' }}>
                {c===5 && <TIcon name="check" size={11} color="#0B0F17" sw={3}/>}
              </div>
            ))}
          </div>
          <div style={{ display:'flex', alignItems:'center', gap:8, marginTop:14, justifyContent:'center' }}>
            <span style={{ fontFamily:MONO, fontSize:10, color:C.fg3 }}>0</span>
            {[0,1,2,4,5].map((c,i)=><span key={i} style={{ width:13, height:13, borderRadius:4, background:gridColor(c) }}/>)}
            <span style={{ fontFamily:MONO, fontSize:10, color:C.fg3 }}>5</span>
          </div>
        </div>

        {/* qada counter */}
        <div style={{ padding:'16px 16px 0' }}>
          <Card pad={16} style={{ display:'flex', alignItems:'center', gap:13 }}>
            <div style={{ width:44, height:44, borderRadius:12, background:softOf('#E5746B',0.14), display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
              <TIcon name="repeat" size={21} color="#E5746B"/>
            </div>
            <div style={{ flex:1 }}>
              <div style={{ fontFamily:FONT, fontSize:14.5, fontWeight:600, color:C.fg }}>Qada prayers</div>
              <div style={{ fontFamily:FONT, fontSize:12.5, color:C.fg2 }}>Make up missed prayers gradually</div>
            </div>
            <div style={{ fontFamily:DISPLAY, fontSize:26, fontWeight:700, color:'#E5746B' }}>{TPRAYER_STATS.qada}</div>
          </Card>
        </div>
      </div>
    </div>
  );
}

// ═══ 2 · TAFAKKUR JOURNAL ════════════════════════════════════════════════════
const MOODS = [
  { key:'grateful', label:'Grateful', color:'#2BB673', icon:'smile' },
  { key:'calm',     label:'Calm',     color:'#4CC2FF', icon:'leaf' },
  { key:'hopeful',  label:'Hopeful',  color:'#A78BFA', icon:'sun' },
  { key:'tested',   label:'Tested',   color:'#E0A93B', icon:'flame' },
];
const MOOD_MAP = { calm:'#4CC2FF', grateful:'#2BB673', tired:'#C9A24E', hopeful:'#A78BFA' };

function TafakkurJournalScreen({ onBack }) {
  const [prompt, setPrompt] = React.useState(0);
  const [mood, setMood] = React.useState('calm');
  const [text, setText] = React.useState('');
  return (
    <div style={{ height:'100%', display:'flex', flexDirection:'column' }}>
      <BackBar title="Tafakkur" onBack={onBack} right={<TIcon name="bookmark" size={19} color={GOLD}/>}/>
      <div style={{ flex:1, overflowY:'auto', padding:'0 0 120px' }}>

        {/* calm hero — prompt of the day */}
        <div style={{ position:'relative', overflow:'hidden', padding:'22px 24px 10px', textAlign:'center' }}>
          <AmbientGlow color={GOLD} top={-60} opacity={0.5}/>
          <div style={{ position:'relative', zIndex:1, display:'flex', flexDirection:'column', alignItems:'center' }}>
            <BreathOrb size={76} color={GOLD}/>
            <div style={{ fontFamily:MONO, fontSize:10.5, letterSpacing:'0.14em', textTransform:'uppercase', color:GOLD, margin:'18px 0 10px' }}>Reflect · take a breath</div>
            <h1 style={{ fontFamily:SERIF, fontSize:28, fontWeight:400, color:C.fg, margin:0, lineHeight:1.25, maxWidth:300 }}>{TPROMPTS[prompt]}</h1>
            <button onClick={()=>setPrompt(p=>(p+1)%TPROMPTS.length)} style={{ marginTop:20, display:'inline-flex', alignItems:'center', gap:7, background:'transparent', border:`1px solid ${C.line}`, borderRadius:999, padding:'8px 14px', color:C.fg2, fontFamily:FONT, fontSize:13, cursor:'pointer' }}>
              <TIcon name="repeat" size={14} color={GOLD}/> Another prompt
            </button>
          </div>
        </div>

        {/* write */}
        <div style={{ padding:'18px 16px 0' }}>
          <Card pad={16}>
            <div style={{ display:'flex', gap:7, marginBottom:13 }}>
              {MOODS.map(m => {
                const on = mood === m.key;
                return (
                  <button key={m.key} onClick={()=>setMood(m.key)} style={{ flex:1, display:'flex', flexDirection:'column', alignItems:'center', gap:4, padding:'9px 4px', borderRadius:11, border:`1px solid ${on?m.color:C.sep}`, background:on?softOf(m.color,0.14):'transparent', cursor:'pointer' }}>
                    <TIcon name={m.icon} size={17} color={on?m.color:C.fg3}/>
                    <span style={{ fontFamily:FONT, fontSize:10.5, fontWeight:on?600:500, color:on?C.fg:C.fg3 }}>{m.label}</span>
                  </button>
                );
              })}
            </div>
            <textarea value={text} onChange={e=>setText(e.target.value)} rows={4} placeholder="Write freely. No one sees this but you and Allah." style={{ width:'100%', boxSizing:'border-box', background:'transparent', border:'none', outline:'none', resize:'none', color:C.fg, fontFamily:SERIF, fontSize:18, lineHeight:1.6 }}/>
            <div style={{ display:'flex', alignItems:'center', gap:8, marginTop:8 }}>
              <button style={{ display:'flex', alignItems:'center', gap:6, background:C.card2, border:`0.5px solid ${C.sep}`, borderRadius:999, padding:'7px 11px', color:C.fg2, fontFamily:FONT, fontSize:12.5, cursor:'pointer' }}><TIcon name="mic" size={14} color={C.fg2}/> Voice</button>
              <span style={{ flex:1 }}/>
              <button style={{ background:GOLD, color:'#1A1306', border:'none', borderRadius:999, padding:'9px 18px', fontFamily:FONT, fontSize:13.5, fontWeight:600, cursor:'pointer' }}>Save entry</button>
            </div>
          </Card>
          <div style={{ marginTop:14 }}>
            <ProactiveCard text="Yesterday you reflected on patience in a code review. A gentle practice today: pause for one breath before you respond to anything that tests you."/>
          </div>
        </div>

        {/* history */}
        <div style={{ fontFamily:MONO, fontSize:10.5, letterSpacing:'0.08em', textTransform:'uppercase', color:C.fg3, padding:'24px 20px 10px' }}>Your journal · {TJOURNAL.length} entries</div>
        <div style={{ padding:'0 16px', display:'flex', flexDirection:'column', gap:11 }}>
          {TJOURNAL.map(j => {
            const col = MOOD_MAP[j.mood] || '#4CC2FF';
            return (
              <Card key={j.id} pad={16}>
                <div style={{ display:'flex', alignItems:'center', justifyContent:'space-between', marginBottom:8 }}>
                  <span style={{ fontFamily:MONO, fontSize:11, color:C.fg3 }}>{j.date}</span>
                  <Pill color={col} soft={softOf(col)}>{j.mood}</Pill>
                </div>
                <h3 style={{ fontFamily:SERIF, fontSize:21, fontWeight:400, color:C.fg, margin:'0 0 6px' }}>{j.title}</h3>
                <p style={{ fontFamily:FONT, fontSize:14, color:C.fg2, lineHeight:1.6, margin:0 }}>{j.excerpt}</p>
              </Card>
            );
          })}
        </div>
      </div>
    </div>
  );
}

// ═══ 3 · GRATITUDE (SHUKR) ═══════════════════════════════════════════════════
function GratitudeScreen({ onBack }) {
  const [grat, setGrat] = React.useState(TGRATITUDE_TODAY);
  const [draft, setDraft] = React.useState('');
  const add = () => { if (draft.trim()) { setGrat(g => [...g, draft.trim()]); setDraft(''); } };
  const green = '#2BB673';
  return (
    <div style={{ height:'100%', display:'flex', flexDirection:'column' }}>
      <BackBar title="Shukr" onBack={onBack}/>
      <div style={{ flex:1, overflowY:'auto', padding:'0 0 120px' }}>

        {/* hero */}
        <div style={{ position:'relative', overflow:'hidden', padding:'20px 24px 8px', textAlign:'center' }}>
          <AmbientGlow color={green} top={-60} opacity={0.45}/>
          <div style={{ position:'relative', zIndex:1 }}>
            <div style={{ fontFamily:ARABIC, fontSize:30, color:green, direction:'rtl', marginBottom:4 }}>الشكر</div>
            <div style={{ fontFamily:MONO, fontSize:10.5, letterSpacing:'0.14em', textTransform:'uppercase', color:C.fg3 }}>Gratitude · shukr</div>
            <h1 style={{ fontFamily:SERIF, fontSize:26, fontWeight:400, color:C.fg, margin:'8px 0 0', lineHeight:1.3, maxWidth:300, marginLeft:'auto', marginRight:'auto' }}>"If you are grateful, I will surely increase you."</h1>
            <div style={{ fontFamily:MONO, fontSize:11, color:C.fg3, marginTop:8 }}>Ibrahim · 14:7</div>
          </div>
        </div>

        {/* streak */}
        <div style={{ display:'flex', gap:9, padding:'18px 16px' }}>
          {[['flame','12','day streak',green],['leaf','3','today',named('cyan')],['star','248','total noted',GOLD]].map(([ic,v,l,col],i)=>(
            <div key={i} style={{ flex:1, background:C.card, border:`0.5px solid ${C.sep}`, borderRadius:14, padding:'12px 8px', textAlign:'center' }}>
              <TIcon name={ic} size={17} color={col} sw={2} style={{ margin:'0 auto' }}/>
              <div style={{ fontFamily:DISPLAY, fontSize:18, fontWeight:700, color:C.fg, marginTop:4 }}>{v}</div>
              <div style={{ fontFamily:FONT, fontSize:10.5, color:C.fg3 }}>{l}</div>
            </div>
          ))}
        </div>

        {/* today's three */}
        <div style={{ fontFamily:MONO, fontSize:10.5, letterSpacing:'0.08em', textTransform:'uppercase', color:C.fg3, padding:'0 20px 10px' }}>Today · three things</div>
        <div style={{ padding:'0 16px' }}>
          <Card pad={16}>
            <div style={{ display:'flex', flexDirection:'column', gap:9 }}>
              {grat.map((g,i) => (
                <div key={i} style={{ display:'flex', alignItems:'center', gap:11, padding:'11px 13px', background:softOf(green,0.1), borderRadius:12 }}>
                  <div style={{ width:28, height:28, borderRadius:'50%', background:softOf(green,0.2), display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0, fontFamily:MONO, fontSize:12, color:green, fontWeight:600 }}>{i+1}</div>
                  <span style={{ fontFamily:SERIF, fontSize:18, color:C.fg }}>{g}</span>
                </div>
              ))}
            </div>
            <div style={{ display:'flex', gap:8, marginTop:11 }}>
              <input value={draft} onChange={e=>setDraft(e.target.value)} onKeyDown={e=>{ if(e.key==='Enter') add(); }} placeholder="Alhamdulillah for…" style={{ flex:1, background:C.card2, border:`0.5px solid ${C.sep}`, borderRadius:11, padding:'11px 13px', color:C.fg, fontFamily:FONT, fontSize:14, outline:'none' }}/>
              <button onClick={add} style={{ width:44, borderRadius:11, border:'none', background:green, cursor:'pointer', display:'flex', alignItems:'center', justifyContent:'center' }}><TIcon name="plus" size={18} color="#fff"/></button>
            </div>
          </Card>
        </div>

        {/* history */}
        <div style={{ fontFamily:MONO, fontSize:10.5, letterSpacing:'0.08em', textTransform:'uppercase', color:C.fg3, padding:'24px 20px 10px' }}>Earlier</div>
        <div style={{ padding:'0 16px', display:'flex', flexDirection:'column', gap:11 }}>
          {TJOURNAL.map(j => (
            <Card key={j.id} pad={15}>
              <div style={{ fontFamily:MONO, fontSize:11, color:C.fg3, marginBottom:9 }}>{j.date}</div>
              <div style={{ display:'flex', flexWrap:'wrap', gap:7 }}>
                {j.gratitude.map((g,i)=>(
                  <span key={i} style={{ display:'inline-flex', alignItems:'center', gap:6, background:softOf(green,0.1), borderRadius:999, padding:'6px 11px', fontFamily:FONT, fontSize:13, color:C.fg }}>
                    <TIcon name="leaf" size={12} color={green}/>{g}
                  </span>
                ))}
              </div>
            </Card>
          ))}
        </div>
      </div>
    </div>
  );
}

// ═══ 4 · QURʼAN & DHIKR ══════════════════════════════════════════════════════
function QuranDhikrScreen({ onBack }) {
  const [tab, setTab] = React.useState('quran');
  return (
    <div style={{ height:'100%', display:'flex', flexDirection:'column' }}>
      <BackBar title="Qurʼan & dhikr" onBack={onBack}/>
      <div style={{ padding:'10px 16px 14px' }}>
        <Segmented tabs={[['quran','Qurʼan'],['dhikr','Dhikr']]} value={tab} onChange={setTab}/>
      </div>
      <div style={{ flex:1, overflowY:'auto', padding:'0 0 120px' }}>
        {tab === 'quran' ? <QuranTab/> : <DhikrTab/>}
      </div>
    </div>
  );
}

function QuranTab() {
  const L = TQURAN_LAST; const blue = named('cyan');
  return (
    <div>
      {/* continue reading */}
      <div style={{ padding:'0 16px 14px' }}>
        <div style={{ position:'relative', overflow:'hidden', borderRadius:18, padding:'18px', background:`linear-gradient(150deg, ${softOf(blue,0.16)}, rgba(124,92,255,0.05))`, border:`0.5px solid ${blue}44` }}>
          <div style={{ display:'flex', alignItems:'flex-start', justifyContent:'space-between' }}>
            <div>
              <div style={{ fontFamily:MONO, fontSize:10.5, letterSpacing:'0.08em', textTransform:'uppercase', color:blue, fontWeight:600 }}>Continue reading</div>
              <div style={{ fontFamily:DISPLAY, fontSize:24, fontWeight:700, color:C.fg, margin:'5px 0 2px' }}>{L.surah}</div>
              <div style={{ fontFamily:FONT, fontSize:13, color:C.fg2 }}>Ayah {L.ayah} · Juz {L.juz}</div>
            </div>
            <div style={{ fontFamily:ARABIC, fontSize:30, color:blue, direction:'rtl' }}>{L.arabic}</div>
          </div>
          <div style={{ display:'flex', alignItems:'center', gap:10, marginTop:14 }}>
            <ProgressBar value={L.pct} color={blue}/>
            <span style={{ fontFamily:MONO, fontSize:11, color:C.fg2 }}>{Math.round(L.pct*100)}%</span>
          </div>
          <button style={{ marginTop:14, width:'100%', background:blue, color:'#08121A', border:'none', borderRadius:11, padding:'11px', fontFamily:FONT, fontSize:14, fontWeight:600, cursor:'pointer', display:'flex', alignItems:'center', justifyContent:'center', gap:7 }}>
            <TIcon name="bookOpen" size={15} color="#08121A"/> Resume
          </button>
        </div>
      </div>

      {/* verse of the day */}
      <div style={{ padding:'0 16px 18px' }}>
        <Card pad={18} style={{ textAlign:'center' }}>
          <div style={{ fontFamily:MONO, fontSize:10, letterSpacing:'0.1em', textTransform:'uppercase', color:GOLD, marginBottom:12 }}>Verse of the day</div>
          <div style={{ fontFamily:ARABIC, fontSize:26, color:C.fg, direction:'rtl', lineHeight:1.9, marginBottom:12 }}>{TQURAN_VERSE.arabic}</div>
          <div style={{ fontFamily:SERIF, fontSize:17, color:C.fg2, fontStyle:'italic', lineHeight:1.5, marginBottom:6 }}>"{TQURAN_VERSE.meaning}"</div>
          <div style={{ fontFamily:MONO, fontSize:11, color:C.fg3 }}>{TQURAN_VERSE.ref}</div>
        </Card>
      </div>

      {/* surah list */}
      <div style={{ fontFamily:MONO, fontSize:10.5, letterSpacing:'0.08em', textTransform:'uppercase', color:C.fg3, padding:'0 20px 9px' }}>Surahs</div>
      <div style={{ margin:'0 16px', background:C.card, border:`0.5px solid ${C.sep}`, borderRadius:16, overflow:'hidden' }}>
        {TQURAN_SURAHS.map((s,i) => (
          <div key={s.no} style={{ display:'flex', alignItems:'center', gap:13, padding:'13px 15px', borderBottom:i<TQURAN_SURAHS.length-1?`0.5px solid ${C.sep}`:'none' }}>
            <div style={{ position:'relative', width:34, height:34, flexShrink:0 }}>
              <svg width="34" height="34" viewBox="0 0 34 34" style={{ position:'absolute', transform:'rotate(45deg)' }}><rect x="6" y="6" width="22" height="22" rx="5" fill="none" stroke={s.read>=1?C.green:s.read>0?blue:C.line} strokeWidth="1.5"/></svg>
              <div style={{ position:'absolute', inset:0, display:'flex', alignItems:'center', justifyContent:'center', fontFamily:MONO, fontSize:11, fontWeight:600, color:s.read>=1?C.green:s.read>0?blue:C.fg2 }}>{s.no}</div>
            </div>
            <div style={{ flex:1, minWidth:0 }}>
              <div style={{ fontFamily:FONT, fontSize:14.5, fontWeight:600, color:C.fg }}>{s.name}</div>
              <div style={{ fontFamily:FONT, fontSize:12, color:C.fg3 }}>{s.meaning} · {s.ayahs} ayahs · {s.place}</div>
            </div>
            {s.read >= 1
              ? <TIcon name="checkCircle" size={19} color={C.green}/>
              : <div style={{ fontFamily:ARABIC, fontSize:18, color:C.fg2, direction:'rtl' }}>{s.arabic}</div>}
          </div>
        ))}
      </div>
    </div>
  );
}

function DhikrTab() {
  const [active, setActive] = React.useState('subhan');
  const [counts, setCounts] = React.useState(() => { const o={}; TDHIKR.forEach(d=>o[d.key]=d.count); return o; });
  const cur = TDHIKR.find(d => d.key === active);
  const count = counts[active];
  const frac = Math.min(count / cur.target, 1);
  const r = 96, circ = 2*Math.PI*r;
  const inc = () => setCounts(c => ({ ...c, [active]: c[active] + 1 }));
  const reset = () => setCounts(c => ({ ...c, [active]: 0 }));
  return (
    <div>
      {/* tasbih counter */}
      <div style={{ display:'flex', flexDirection:'column', alignItems:'center', padding:'8px 16px 0' }}>
        <div style={{ fontFamily:ARABIC, fontSize:30, color:cur.color, direction:'rtl', marginBottom:2, minHeight:40 }}>{cur.arabic}</div>
        <div style={{ fontFamily:FONT, fontSize:13.5, color:C.fg2, marginBottom:18 }}>{cur.translit} · {cur.meaning}</div>
        <button onClick={inc} style={{ position:'relative', width:230, height:230, borderRadius:'50%', border:'none', background:'transparent', cursor:'pointer', padding:0 }}>
          <svg width="230" height="230" style={{ position:'absolute', inset:0, transform:'rotate(-90deg)' }}>
            <circle cx="115" cy="115" r={r} fill="none" stroke="rgba(120,130,150,0.16)" strokeWidth="9"/>
            <circle cx="115" cy="115" r={r} fill="none" stroke={cur.color} strokeWidth="9" strokeLinecap="round" strokeDasharray={circ} strokeDashoffset={circ*(1-frac)} style={{ transition:'stroke-dashoffset .3s cubic-bezier(0.16,1,0.3,1)', filter:`drop-shadow(0 0 7px ${softOf(cur.color,0.6)})` }}/>
          </svg>
          <div style={{ position:'absolute', inset:18, borderRadius:'50%', background:softOf(cur.color,0.08), border:`1px solid ${softOf(cur.color,0.25)}`, display:'flex', flexDirection:'column', alignItems:'center', justifyContent:'center' }}>
            <div style={{ fontFamily:DISPLAY, fontSize:58, fontWeight:700, color:C.fg, lineHeight:1 }}>{count}</div>
            <div style={{ fontFamily:MONO, fontSize:12, color:C.fg3, marginTop:4 }}>of {cur.target}</div>
            {frac >= 1 && <div style={{ fontFamily:MONO, fontSize:10, letterSpacing:'0.1em', color:cur.color, marginTop:6, textTransform:'uppercase' }}>Complete</div>}
          </div>
        </button>
        <div style={{ display:'flex', gap:10, marginTop:18 }}>
          <button onClick={reset} style={{ display:'flex', alignItems:'center', gap:7, background:C.card, border:`0.5px solid ${C.line}`, borderRadius:999, padding:'9px 16px', color:C.fg2, fontFamily:FONT, fontSize:13, cursor:'pointer' }}><TIcon name="repeat" size={14} color={C.fg2}/> Reset</button>
          <div style={{ display:'flex', alignItems:'center', gap:7, background:C.card, border:`0.5px solid ${C.line}`, borderRadius:999, padding:'9px 16px', color:C.fg2, fontFamily:FONT, fontSize:13 }}><TIcon name="mic" size={14} color={C.fg2}/> Tap anywhere</div>
        </div>
      </div>

      {/* dhikr selector */}
      <div style={{ fontFamily:MONO, fontSize:10.5, letterSpacing:'0.08em', textTransform:'uppercase', color:C.fg3, padding:'26px 20px 10px' }}>Daily dhikr</div>
      <div style={{ padding:'0 16px', display:'flex', flexDirection:'column', gap:9 }}>
        {TDHIKR.map(d => {
          const on = d.key === active; const c = counts[d.key]; const done = c >= d.target;
          return (
            <button key={d.key} onClick={()=>setActive(d.key)} style={{ textAlign:'left', display:'flex', alignItems:'center', gap:13, padding:'13px 15px', borderRadius:14, background:on?softOf(d.color,0.1):C.card, border:`1px solid ${on?d.color:C.sep}`, cursor:'pointer' }}>
              <div style={{ fontFamily:ARABIC, fontSize:22, color:d.color, direction:'rtl', minWidth:48, textAlign:'center' }}>{d.arabic.split(' ')[0]}</div>
              <div style={{ flex:1, minWidth:0 }}>
                <div style={{ fontFamily:FONT, fontSize:14.5, fontWeight:600, color:C.fg }}>{d.translit}</div>
                <div style={{ fontFamily:FONT, fontSize:12, color:C.fg3 }}>{d.meaning}</div>
              </div>
              <div style={{ textAlign:'right' }}>
                <div style={{ fontFamily:MONO, fontSize:14, fontWeight:600, color:done?C.green:d.color }}>{c}/{d.target}</div>
                {done && <TIcon name="check" size={13} color={C.green} style={{ marginLeft:'auto' }}/>}
              </div>
            </button>
          );
        })}
      </div>

      {/* adhkar */}
      <div style={{ fontFamily:MONO, fontSize:10.5, letterSpacing:'0.08em', textTransform:'uppercase', color:C.fg3, padding:'26px 20px 10px' }}>Adhkar</div>
      <div style={{ padding:'0 16px', display:'flex', flexDirection:'column', gap:10 }}>
        {TADHKAR.map(a => (
          <Card key={a.key} pad={15} style={{ display:'flex', alignItems:'center', gap:13 }}>
            <div style={{ width:42, height:42, borderRadius:12, background:softOf(a.color,0.14), display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
              <TIcon name={a.icon} size={20} color={a.color}/>
            </div>
            <div style={{ flex:1, minWidth:0 }}>
              <div style={{ fontFamily:FONT, fontSize:14.5, fontWeight:600, color:C.fg }}>{a.label}</div>
              <div style={{ fontFamily:FONT, fontSize:12, color:C.fg3, marginBottom:7 }}>{a.native}</div>
              <div style={{ display:'flex', alignItems:'center', gap:9 }}>
                <ProgressBar value={a.done/a.total} color={a.color} h={4}/>
                <span style={{ fontFamily:MONO, fontSize:11, color:C.fg2 }}>{a.done}/{a.total}</span>
              </div>
            </div>
            <TIcon name={a.done>=a.total?'checkCircle':'chevronRight'} size={19} color={a.done>=a.total?C.green:C.fg3}/>
          </Card>
        ))}
      </div>
    </div>
  );
}

Object.assign(window, { PrayerTrackerScreen, TafakkurJournalScreen, GratitudeScreen, QuranDhikrScreen });
