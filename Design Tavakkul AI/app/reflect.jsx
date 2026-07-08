// Tafakkur AI — Spiritual reflection (tafakkur journaling, gratitude, prompts)

const MOOD = {
  calm:{c:'#4CC2FF',l:'Calm'}, grateful:{c:'#2BB673',l:'Grateful'},
  tired:{c:'#C9A24E',l:'Tired'}, hopeful:{c:'#7C5CFF',l:'Hopeful'},
};

function JournalCard({ j, serif }) {
  const m = MOOD[j.mood] || MOOD.calm;
  return (
    <Card pad={16}>
      <div style={{ display:'flex', alignItems:'center', justifyContent:'space-between', marginBottom:8 }}>
        <span style={{ fontFamily:MONO, fontSize:11, color:C.fg3, letterSpacing:'0.04em' }}>{j.date}</span>
        <Pill color={m.c} soft={softOf(m.c)}>{m.l}</Pill>
      </div>
      <h3 style={{ fontFamily: serif?SERIF:DISPLAY, fontSize: serif?22:17, fontWeight: serif?400:600, color:C.fg, margin:'0 0 6px' }}>{j.title}</h3>
      <p style={{ fontFamily:FONT, fontSize:14, color:C.fg2, lineHeight:1.6, margin:0 }}>{j.excerpt}</p>
      <div style={{ display:'flex', flexWrap:'wrap', gap:6, marginTop:12 }}>
        {j.gratitude.map((g,i) => (
          <span key={i} style={{ display:'inline-flex', alignItems:'center', gap:5, background:softOf(C.green,0.12), borderRadius:999, padding:'4px 9px', fontFamily:FONT, fontSize:11.5, color:C.green }}>
            <TIcon name="leaf" size={11} color={C.green}/>{g}
          </span>
        ))}
      </div>
    </Card>
  );
}

function ReflectScreen({ serif }) {
  const { accent } = useApp();
  const [grat, setGrat] = React.useState(TGRATITUDE_TODAY);
  const [draft, setDraft] = React.useState('');
  const [prompt, setPrompt] = React.useState(0);
  const addGrat = () => { if (draft.trim()) { setGrat(g => [...g, draft.trim()]); setDraft(''); } };
  const accentG = serif ? C.gold : accent;
  return (
    <div>
      {/* Calm hero */}
      <div style={{ position:'relative', overflow:'hidden', padding:'18px 20px 8px', display:'flex', flexDirection:'column', alignItems:'center', textAlign:'center' }}>
        <AmbientGlow color={C.gold} top={-50} opacity={0.55}/>
        <div style={{ position:'relative', zIndex:1, marginBottom:18 }}><BreathOrb size={96} color={C.gold}/></div>
        <div style={{ position:'relative', zIndex:1 }}>
          <div style={{ fontFamily:MONO, fontSize:11, letterSpacing:'0.12em', textTransform:'uppercase', color:C.gold, marginBottom:8 }}>Tafakkur · take a breath</div>
          <h1 style={{ fontFamily: serif?SERIF:DISPLAY, fontSize: serif?34:26, fontWeight: serif?400:700, color:C.fg, margin:0, lineHeight:1.2, maxWidth:300 }}>{TPROMPTS[prompt]}</h1>
        </div>
      </div>

      <div style={{ padding:'18px 16px 0' }}>
        <button onClick={()=>setPrompt(p => (p+1)%TPROMPTS.length)} style={{ width:'100%', background:'transparent', border:`1px solid ${C.line}`, borderRadius:12, padding:'11px', color:C.fg2, fontFamily:FONT, fontSize:13.5, cursor:'pointer', display:'flex', alignItems:'center', justifyContent:'center', gap:8, marginBottom:14 }}>
          <TIcon name="repeat" size={15} color={C.gold}/> New prompt
        </button>

        {/* Write */}
        <Card pad={16}>
          <div style={{ display:'flex', alignItems:'center', gap:8, marginBottom:10 }}>
            <TIcon name="penLine" size={16} color={accentG}/>
            <span style={{ fontFamily:DISPLAY, fontSize:16, fontWeight:600, color:C.fg }}>Today's reflection</span>
          </div>
          <textarea placeholder="Write freely. No one sees this but you." rows={3} style={{ width:'100%', boxSizing:'border-box', background:'transparent', border:'none', outline:'none', resize:'none', color:C.fg, fontFamily: serif?SERIF:FONT, fontSize: serif?18:14.5, lineHeight:1.6 }}/>
          <div style={{ display:'flex', alignItems:'center', gap:8, marginTop:6 }}>
            <button style={{ display:'flex', alignItems:'center', gap:6, background:C.card2, border:`0.5px solid ${C.sep}`, borderRadius:999, padding:'7px 11px', color:C.fg2, fontFamily:FONT, fontSize:12.5, cursor:'pointer' }}><TIcon name="mic" size={14} color={C.fg2}/> Voice</button>
            <span style={{ flex:1 }}/>
            <button style={{ background:accentG, color: serif?'#1A1306':'#fff', border:'none', borderRadius:999, padding:'9px 18px', fontFamily:FONT, fontSize:13.5, fontWeight:600, cursor:'pointer' }}>Save entry</button>
          </div>
        </Card>

        {/* AI gentle nudge */}
        <div style={{ marginTop:14 }}>
          <ProactiveCard text="Yesterday you wrote about patience in a code review. A gentle practice for today: notice one moment you'd usually rush, and slow it down." />
        </div>
      </div>

      {/* Gratitude */}
      <div style={{ paddingTop:24 }}>
        <SectionTitle serif={serif}>Shukr · gratitude</SectionTitle>
        <div style={{ padding:'0 16px' }}>
          <Card pad={16}>
            <div style={{ fontFamily:FONT, fontSize:13, color:C.fg2, marginBottom:12 }}>Three things you're grateful for today</div>
            <div style={{ display:'flex', flexDirection:'column', gap:9 }}>
              {grat.map((g,i) => (
                <div key={i} style={{ display:'flex', alignItems:'center', gap:11, padding:'10px 12px', background:softOf(C.green,0.1), borderRadius:11 }}>
                  <div style={{ width:26, height:26, borderRadius:'50%', background:softOf(C.green,0.2), display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}><TIcon name="leaf" size={14} color={C.green}/></div>
                  <span style={{ fontFamily: serif?SERIF:FONT, fontSize: serif?17:14, color:C.fg }}>{g}</span>
                </div>
              ))}
            </div>
            <div style={{ display:'flex', gap:8, marginTop:11 }}>
              <input value={draft} onChange={e=>setDraft(e.target.value)} onKeyDown={e=>{ if(e.key==='Enter') addGrat(); }} placeholder="Add one more…" style={{ flex:1, background:C.card2, border:`0.5px solid ${C.sep}`, borderRadius:10, padding:'10px 12px', color:C.fg, fontFamily:FONT, fontSize:14, outline:'none' }}/>
              <button onClick={addGrat} style={{ width:42, borderRadius:10, border:'none', background:C.green, cursor:'pointer', display:'flex', alignItems:'center', justifyContent:'center' }}><TIcon name="plus" size={18} color="#fff"/></button>
            </div>
          </Card>
        </div>
      </div>

      {/* Journal history */}
      <div style={{ paddingTop:24 }}>
        <SectionTitle serif={serif} action="All entries">Your journal</SectionTitle>
        <div style={{ padding:'0 16px', display:'flex', flexDirection:'column', gap:11 }}>
          {TJOURNAL.map(j => <JournalCard key={j.id} j={j} serif={serif}/>)}
        </div>
      </div>

      <div style={{ padding:'18px 16px 0', textAlign:'center' }}>
        <div style={{ display:'inline-flex', alignItems:'center', gap:7, fontFamily:FONT, fontSize:12, color:C.fg3 }}>
          <TIcon name="lock" size={13} color={C.fg3}/> Private &amp; on-device. Educational, non-denominational tone.
        </div>
      </div>
      <div style={{ height:120 }}/>
    </div>
  );
}

Object.assign(window, { ReflectScreen });
