// Tafakkur AI — app shell: router, tweaks, companion, device frame

const TWEAK_DEFAULTS = /*EDITMODE-BEGIN*/{
  "nav": "tabs",
  "mode": "dark",
  "darkTheme": "sukut",
  "lightTheme": "noor",
  "serif": true,
  "proactivity": "balanced"
}/*EDITMODE-END*/;

const TAB_ROOTS = ['today','learn','do','thoughts','more'];

function App() {
  const [t, setTweak] = useTweaks(TWEAK_DEFAULTS);
  // resolve + apply the active theme (mutates the shared C palette before children render)
  const activeTheme = (t.mode === 'light' ? (t.lightTheme || 'noor') : (t.darkTheme || 'sukut'));
  applyTheme(activeTheme);
  const acc = { c: C.teal, soft: softOf(C.teal, 0.16), ring: softOf(C.teal, 0.30) };

  // navigation stack
  const [nav, setNav] = React.useState(t.nav === 'hub' ? ['hub'] : ['today']);
  const [sheet, setSheet] = React.useState(false);
  const [thoughts, setThoughts] = React.useState(TSOCIAL);

  // reset root when nav mode changes
  const navModeRef = React.useRef(t.nav);
  React.useEffect(() => {
    if (navModeRef.current !== t.nav) {
      navModeRef.current = t.nav;
      setNav(t.nav === 'hub' ? ['hub'] : ['today']);
    }
  }, [t.nav]);

  const top = nav[nav.length - 1];
  const push = (s) => setNav(n => [...n, s]);
  const pop = () => setNav(n => n.length > 1 ? n.slice(0, -1) : n);
  const setTab = (k) => setNav([k]);

  // open a module by key (used by hub + more + plan items)
  const openModule = (k) => {
    if (k === 'today') { setNav(t.nav === 'hub' ? ['hub'] : ['today']); return; }
    if (TAB_ROOTS.includes(k)) { push(k === 'today' ? 'today' : k); return; }
    push(k);
  };
  const openByModuleName = (name) => {
    const map = { Productivity:'do', Learn:'learn', Reflect:'reflect', Career:'career', Thoughts:'thoughts', Finance:'finance' };
    const k = map[name]; if (k) push(k);
  };

  const ctx = {
    accent: acc.c, accentSoft: acc.soft, accentRing: acc.ring,
    serifHead: SERIF, proactivity: t.proactivity, navMode: t.nav,
    mode: t.mode,
  };

  const isTabRoot = TAB_ROOTS.includes(top);
  const showTab = t.nav === 'tabs' && isTabRoot;

  const rootWrap = (el) => <div style={{ height:'100%', overflowY:'auto', WebkitOverflowScrolling:'touch' }}>{el}</div>;

  let screen;
  switch (top) {
    case 'today':  screen = rootWrap(<TodayScreen onOpen={(it)=>openByModuleName(it.module)} onCoach={()=>push('coach')}/>); break;
    case 'hub':    screen = rootWrap(<HubScreen onOpen={openModule}/>); break;
    case 'learn':  screen = rootWrap(<LearnScreen onLesson={()=>push('lesson')}/>); break;
    case 'do':     screen = rootWrap(<ProductivityScreen/>); break;
    case 'thoughts':screen = rootWrap(<ThoughtsScreen posts={thoughts} onCompose={()=>push('compose')}/>); break;
    case 'compose': screen = <ComposeThoughtScreen onBack={pop} onPublish={(post)=>{ setThoughts(p=>[post,...p]); pop(); }}/>; break;
    case 'more':   screen = rootWrap(<MoreScreen onOpen={openModule}/>); break;
    case 'coach':  screen = (
      <div style={{ height:'100%', display:'flex', flexDirection:'column' }}>
        <BackBar title="" onBack={pop}/>
        <div style={{ flex:1, minHeight:0 }}><CoachScreen/></div>
      </div>
    ); break;
    case 'lesson': screen = <LessonScreen onBack={pop}/>; break;
    case 'career': screen = <CareerScreen onBack={pop}/>; break;
    case 'social': screen = <SocialScreen onBack={pop}/>; break;
    case 'finance':screen = <FinanceScreen onBack={pop}/>; break;
    case 'reflect':screen = rootWrap(<ReflectScreen serif={true}/>); break;
    case 'prayer':  screen = <PrayerTrackerScreen onBack={pop}/>; break;
    case 'tafakkur':screen = <TafakkurJournalScreen onBack={pop}/>; break;
    case 'gratitude':screen = <GratitudeScreen onBack={pop}/>; break;
    case 'quran':   screen = <QuranDhikrScreen onBack={pop}/>; break;
    case 'sleep':   screen = <SleepScreen onBack={pop}/>; break;
    case 'sport':   screen = <SportGymScreen onBack={pop}/>; break;
    case 'focus':   screen = <FocusScreen onBack={pop}/>; break;
    case 'appearance': screen = <AppearanceScreen t={t} setTweak={setTweak} onBack={pop}/>; break;
    default:
      if (STUB_INFO[top]) screen = <StubScreen info={STUB_INFO[top]} onBack={pop}/>;
      else screen = rootWrap(<TodayScreen onOpen={()=>{}} onCoach={()=>push('coach')}/>);
  }

  const showFab = !['coach','lesson','compose','appearance'].includes(top);

  return (
    <AppCtx.Provider value={ctx}>
      <div className="tf-phone">
        <Notch/>
        <TStatusBar/>
        <div style={{ position:'absolute', inset:0, paddingTop:54, boxSizing:'border-box', background:C.bg1 }}>
          <div key={top} className="tf-screen">{screen}</div>
        </div>
        {showTab && <TTabBar active={top} onChange={setTab} accent={acc.c}/>}
        {showFab && <CompanionFab onClick={()=>setSheet(true)} raised={showTab}/>}
        <CompanionSheet open={sheet} onClose={()=>setSheet(false)} onFull={()=>{ setSheet(false); push('coach'); }}/>
        <HomeIndicator/>
      </div>

      <TweaksPanel>
        <TweakSection label="Appearance" />
        <TweakRadio label="Mode" value={t.mode} options={['dark','light']}
          onChange={(v)=>setTweak('mode', v)} />
        <TweakSection label="Navigation" />
        <TweakRadio label="Structure" value={t.nav} options={['tabs','hub']}
          onChange={(v)=>setTweak('nav', v)} />
        <TweakSection label="Reflection" />
        <TweakToggle label="Reflective serif" value={t.serif}
          onChange={(v)=>setTweak('serif', v)} />
        <TweakSection label="AI coach" />
        <TweakRadio label="Proactivity" value={t.proactivity}
          options={['ambient','balanced','chatty']}
          onChange={(v)=>setTweak('proactivity', v)} />
      </TweaksPanel>
    </AppCtx.Provider>
  );
}

// Hub home (hub nav mode)
function HubScreen({ onOpen }) {
  const { accent } = useApp();
  return (
    <div style={{ position:'relative' }}>
      <AmbientGlow color={accent} top={-40}/>
      <div style={{ position:'relative', zIndex:1, padding:'8px 20px 0' }}>
        <div style={{ display:'flex', alignItems:'center', justifyContent:'space-between' }}>
          <div style={{ fontFamily:FONT, fontSize:14, color:C.fg2 }}>{TUSER.greetingUz} · {TUSER.today}</div>
          <div style={{ width:34, height:34, borderRadius:'50%', background:`linear-gradient(135deg, ${C.gold}, ${C.rose})`, display:'flex', alignItems:'center', justifyContent:'center', fontFamily:FONT, fontSize:14, fontWeight:600, color:'#fff' }}>A</div>
        </div>
        <h1 style={{ fontFamily:DISPLAY, fontSize:30, fontWeight:700, color:C.fg, letterSpacing:'0.3px', margin:'12px 0 2px' }}>Tafakkur</h1>
        <div style={{ fontFamily:FONT, fontSize:14, color:C.fg2, marginBottom:16 }}>Think · learn · act · reflect</div>
      </div>
      <div style={{ position:'relative', zIndex:1, padding:'0 16px 14px' }}>
        <ProactiveCard text={TBRIEF.line} action="Talk to Murabbiy" onAction={()=>onOpen('coach')} />
      </div>
      <HubLauncher onOpen={onOpen}/>
      <div style={{ height:120 }}/>
    </div>
  );
}

ReactDOM.createRoot(document.getElementById('root')).render(<App/>);
