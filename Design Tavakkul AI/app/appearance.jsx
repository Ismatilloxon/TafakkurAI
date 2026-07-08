// Tafakkur AI — Appearance settings: choose mode (light/dark) + design system theme

function ThemeMiniPreview({ th }) {
  // tiny representative composition rendered in the theme's own tokens
  const r = 20, circ = 2 * Math.PI * r;
  return (
    <div style={{ background:th.bg1, borderRadius:13, padding:'13px 13px 12px', border:`0.5px solid ${th.sep}`, overflow:'hidden' }}>
      <div style={{ display:'flex', alignItems:'center', gap:11 }}>
        <div style={{ position:'relative', width:46, height:46, flexShrink:0 }}>
          <svg width="46" height="46" style={{ transform:'rotate(-90deg)' }}>
            <circle cx="23" cy="23" r={r} fill="none" stroke={th.sep} strokeWidth="3"/>
            <circle cx="23" cy="23" r={r} fill="none" stroke={th.teal} strokeWidth="3.4" strokeLinecap="round" strokeDasharray={circ} strokeDashoffset={circ*0.4}/>
          </svg>
          <div style={{ position:'absolute', inset:0, display:'flex', alignItems:'center', justifyContent:'center' }}>
            <span style={{ width:8, height:8, borderRadius:4, background:th.gold }}/>
          </div>
        </div>
        <div style={{ flex:1, minWidth:0 }}>
          <div style={{ height:7, width:'72%', borderRadius:4, background:th.fg, opacity:0.85 }}/>
          <div style={{ height:6, width:'48%', borderRadius:4, background:th.fg, opacity:0.32, marginTop:6 }}/>
          <div style={{ display:'flex', gap:5, marginTop:9 }}>
            {[th.gold, th.green, th.purple, th.cyan].map((c,i)=>(
              <span key={i} style={{ width:9, height:9, borderRadius:3, background:c }}/>
            ))}
          </div>
        </div>
      </div>
      <div style={{ height:24, borderRadius:8, background:th.teal, marginTop:11, display:'flex', alignItems:'center', justifyContent:'center' }}>
        <span style={{ width:8, height:8, borderRadius:'50%', background:th.bg1, opacity:0.9 }}/>
        <div style={{ width:38, height:6, borderRadius:4, background:th.bg1, opacity:0.85, marginLeft:6 }}/>
      </div>
    </div>
  );
}

function AppearanceScreen({ t, setTweak, onBack }) {
  const { accent } = useApp();
  const mode = t.mode || 'dark';
  const activeKey = mode === 'light' ? (t.lightTheme || 'noor') : (t.darkTheme || 'sukut');
  const list = THEME_META.filter(m => m.mode === mode);
  const pick = (key) => setTweak(mode === 'light' ? 'lightTheme' : 'darkTheme', key);

  return (
    <div style={{ height:'100%', display:'flex', flexDirection:'column' }}>
      <BackBar title="Appearance" onBack={onBack}/>
      <div style={{ flex:1, overflowY:'auto', padding:'14px 16px 60px' }}>

        <div style={{ padding:'0 4px 4px' }}>
          <h1 style={{ fontFamily:DISPLAY, fontSize:26, fontWeight:700, color:C.fg, margin:'0 0 6px' }}>Make it yours</h1>
          <p style={{ fontFamily:FONT, fontSize:14, color:C.fg2, lineHeight:1.55, margin:0 }}>Choose a mode and the design system that fits your spirit. It applies across the whole app, instantly.</p>
        </div>

        {/* mode */}
        <div style={{ margin:'18px 0 8px' }}>
          <Segmented
            tabs={[['dark','Dark'],['light','Light']]}
            value={mode}
            onChange={(v)=>setTweak('mode', v)}
          />
        </div>
        <div style={{ display:'flex', alignItems:'center', gap:8, padding:'4px 4px 0' }}>
          <TIcon name={mode==='light'?'sun':'moon'} size={14} color={C.fg3}/>
          <span style={{ fontFamily:FONT, fontSize:12.5, color:C.fg3 }}>
            {mode==='light' ? 'Light themes — calm, paper-like, for daytime.' : 'Dark themes — easy on the eyes, for evenings and Fajr.'}
          </span>
        </div>

        {/* theme grid */}
        <div style={{ fontFamily:MONO, fontSize:10.5, letterSpacing:'0.08em', textTransform:'uppercase', color:C.fg3, padding:'22px 4px 12px' }}>
          {mode==='light' ? 'Light' : 'Dark'} design systems · {list.length}
        </div>
        <div style={{ display:'grid', gridTemplateColumns:'1fr 1fr', gap:12 }}>
          {list.map(m => {
            const th = THEMES[m.key];
            const on = m.key === activeKey;
            return (
              <button key={m.key} onClick={()=>pick(m.key)} style={{ textAlign:'left', background:'none', border:'none', padding:0, cursor:'pointer' }}>
                <div style={{ borderRadius:16, padding:3, boxShadow: on ? `0 0 0 2px ${accent}` : '0 0 0 2px transparent' }}>
                  <ThemeMiniPreview th={th}/>
                </div>
                <div style={{ display:'flex', alignItems:'center', gap:7, padding:'9px 6px 0' }}>
                  <div style={{ flex:1, minWidth:0 }}>
                    <div style={{ fontFamily:DISPLAY, fontSize:15, fontWeight:600, color:C.fg }}>{m.name}</div>
                    <div style={{ fontFamily:FONT, fontSize:12, color:C.fg3 }}>{m.concept}</div>
                  </div>
                  {on
                    ? <div style={{ width:21, height:21, borderRadius:'50%', background:accent, display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}><TIcon name="check" size={13} color={C.bg1} sw={3}/></div>
                    : <div style={{ width:21, height:21, borderRadius:'50%', border:`1.5px solid ${C.line}`, flexShrink:0 }}/>}
                </div>
              </button>
            );
          })}
        </div>

        {/* footnote */}
        <div style={{ marginTop:24, padding:'13px 15px', borderRadius:14, border:`0.5px solid ${C.sep}`, display:'flex', gap:11, alignItems:'flex-start' }}>
          <TIcon name="sparkles" size={16} color={accent} style={{ marginTop:1 }}/>
          <span style={{ fontFamily:FONT, fontSize:12.5, color:C.fg2, lineHeight:1.5 }}>Your dark and light choices are remembered separately. Switching Mode flips between them — set one of each and toggle any time.</span>
        </div>
      </div>
    </div>
  );
}

Object.assign(window, { AppearanceScreen, ThemeMiniPreview });
