// Tafakkur AI — Learning system (subjects, AI lesson, quiz)

function SubjectCard({ s, onOpen }) {
  const col = named(s.accent);
  return (
    <button onClick={()=>onOpen(s)} style={{ textAlign:'left', width:'100%', background:C.card, border:`0.5px solid ${C.sep}`, borderRadius:16, padding:14, cursor:'pointer', display:'flex', gap:13, alignItems:'center' }}>
      <div style={{ width:50, height:50, borderRadius:14, background:`linear-gradient(140deg, ${col}, ${softOf(col,0.3)})`, display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
        <TIcon name={s.icon} size={24} color="#fff" sw={1.8}/>
      </div>
      <div style={{ flex:1, minWidth:0 }}>
        <div style={{ display:'flex', alignItems:'baseline', gap:7 }}>
          <span style={{ fontFamily:DISPLAY, fontSize:17, fontWeight:600, color:C.fg }}>{s.name}</span>
          <span style={{ fontFamily:FONT, fontSize:12, color:C.fg3 }}>{s.native}</span>
        </div>
        <div style={{ fontFamily:FONT, fontSize:12.5, color:C.fg2, margin:'2px 0 9px' }}>{s.level}</div>
        <div style={{ display:'flex', alignItems:'center', gap:9 }}>
          <ProgressBar value={s.progress} color={col} h={5}/>
          <span style={{ fontFamily:MONO, fontSize:11, color:C.fg2 }}>{Math.round(s.progress*100)}%</span>
        </div>
      </div>
    </button>
  );
}

function LearnScreen({ onLesson }) {
  const { accent } = useApp();
  const next = TSUBJECTS[1];
  return (
    <div>
      <LargeTitle title="Learn" subtitle="Ta'lim · 4 subjects in progress"/>
      <div style={{ padding:'8px 16px 0', display:'flex', flexDirection:'column', gap:14 }}>
        <ProactiveCard text="You're 3 lessons from finishing TypeScript Module 3. I generated the next one from where you left off — generics in practice." action="Open AI lesson" onAction={onLesson}/>

        {/* Daily English drill */}
        <Card pad={16} style={{ display:'flex', alignItems:'center', gap:13 }}>
          <div style={{ position:'relative' }}><BreathOrb size={52} color={named('cyan')}/></div>
          <div style={{ flex:1 }}>
            <div style={{ fontFamily:MONO, fontSize:10, letterSpacing:'0.07em', textTransform:'uppercase', color:named('cyan'), fontWeight:600, marginBottom:3 }}>English · speaking</div>
            <div style={{ fontFamily:FONT, fontSize:14.5, fontWeight:600, color:C.fg }}>2-minute pronunciation drill</div>
            <div style={{ fontFamily:FONT, fontSize:12.5, color:C.fg2, marginTop:1 }}>Speak, the AI scores you live</div>
          </div>
          <button style={{ width:42, height:42, borderRadius:'50%', border:'none', background:named('cyan'), cursor:'pointer', display:'flex', alignItems:'center', justifyContent:'center' }}><TIcon name="mic" size={19} color="#08121A"/></button>
        </Card>
      </div>

      <div style={{ paddingTop:22 }}>
        <SectionTitle>Subjects</SectionTitle>
        <div style={{ padding:'0 16px', display:'flex', flexDirection:'column', gap:11 }}>
          {TSUBJECTS.map(s => <SubjectCard key={s.id} s={s} onOpen={()=>onLesson()}/>)}
        </div>
      </div>

      <div style={{ padding:'22px 16px 0' }}>
        <button style={{ width:'100%', border:`1px dashed ${C.line}`, background:'transparent', borderRadius:14, padding:'14px', color:C.fg2, fontFamily:FONT, fontSize:14, cursor:'pointer', display:'flex', alignItems:'center', justifyContent:'center', gap:8 }}>
          <TIcon name="plus" size={17} color={accent}/> Add a subject
        </button>
      </div>
      <div style={{ height:120 }}/>
    </div>
  );
}

// ── AI Lesson + Quiz ─────────────────────────────────────────────────────────
function CodeBlock({ code }) {
  return (
    <div style={{ background:C.bg0, borderRadius:12, overflow:'hidden', border:`0.5px solid ${C.sep}` }}>
      <div style={{ padding:'7px 12px', background:C.card2, borderBottom:`0.5px solid ${C.sep}`, display:'flex', alignItems:'center', gap:8 }}>
        <span style={{ width:9, height:9, borderRadius:5, background:'#FF5F57' }}/>
        <span style={{ width:9, height:9, borderRadius:5, background:'#FEBC2E' }}/>
        <span style={{ width:9, height:9, borderRadius:5, background:'#28C840' }}/>
        <span style={{ flex:1 }}/>
        <span style={{ fontFamily:MONO, fontSize:10.5, color:C.fg3 }}>typescript</span>
      </div>
      <pre style={{ margin:0, padding:'12px 14px', fontFamily:MONO, fontSize:11.5, lineHeight:1.7, color:'#D7E0EE', overflowX:'auto' }}>{code}</pre>
    </div>
  );
}

function LessonScreen({ onBack }) {
  const { accent } = useApp();
  const L = TLESSON;
  const [mode, setMode] = React.useState('lesson'); // lesson | quiz | done
  const [qi, setQi] = React.useState(0);
  const [picked, setPicked] = React.useState(null);
  const [correct, setCorrect] = React.useState(0);
  const Q = TQUIZ[qi];

  const choose = (i) => {
    if (picked !== null) return;
    setPicked(i);
    if (i === Q.answer) setCorrect(c => c+1);
  };
  const nextQ = () => {
    if (qi < TQUIZ.length - 1) { setQi(qi+1); setPicked(null); }
    else setMode('done');
  };

  return (
    <div style={{ height:'100%', display:'flex', flexDirection:'column' }}>
      <BackBar title={mode==='quiz'?'Quiz':'AI Lesson'} onBack={onBack} right={<TIcon name="bookmark" size={20} color={accent}/>}/>
      <div style={{ flex:1, overflowY:'auto' }}>
        {mode === 'lesson' && (
          <div style={{ padding:'14px 20px 120px' }}>
            <Pill color={accent} soft={softOf(accent)} style={{ marginBottom:12 }}><TIcon name="sparkles" size={11} color={accent}/> Generated for you</Pill>
            <div style={{ fontFamily:MONO, fontSize:11, color:C.fg3, letterSpacing:'0.06em', marginBottom:6 }}>{L.subject.toUpperCase()} · {L.module} · {L.index} · {L.minutes} MIN</div>
            <h1 style={{ fontFamily:DISPLAY, fontSize:27, fontWeight:700, color:C.fg, margin:'0 0 12px', lineHeight:1.15 }}>{L.title}</h1>
            <p style={{ fontFamily:FONT, fontSize:15, color:C.fg2, lineHeight:1.6, margin:'0 0 20px' }}>{L.intro}</p>

            <div style={{ display:'flex', flexDirection:'column', gap:16 }}>
              {L.blocks.map((b,i) => b.type === 'concept' ? (
                <div key={i}>
                  <h3 style={{ fontFamily:DISPLAY, fontSize:17, fontWeight:600, color:C.fg, margin:'0 0 6px' }}>{b.heading}</h3>
                  <p style={{ fontFamily:FONT, fontSize:14.5, color:C.fg2, lineHeight:1.6, margin:0 }} dangerouslySetInnerHTML={{ __html: b.body.replace(/`([^`]+)`/g, `<code style="font-family:${MONO};background:${C.card2};padding:1px 5px;border-radius:4px;color:${named('cyan')};font-size:0.9em">$1</code>`) }}/>
                </div>
              ) : <CodeBlock key={i} code={b.code}/>)}
            </div>

            <div style={{ marginTop:24, padding:16, borderRadius:16, background:softOf(accent,0.1), border:`0.5px solid ${accent}40`, textAlign:'center' }}>
              <div style={{ fontFamily:FONT, fontSize:14, color:C.fg, marginBottom:12 }}>Ready to check your understanding?</div>
              <button onClick={()=>setMode('quiz')} style={{ background:accent, color:'#fff', border:'none', borderRadius:11, padding:'12px 24px', fontFamily:FONT, fontSize:15, fontWeight:600, cursor:'pointer', display:'inline-flex', alignItems:'center', gap:8 }}>
                Start quiz · {TQUIZ.length} questions <TIcon name="arrowRight" size={15} color="#fff"/>
              </button>
            </div>
          </div>
        )}

        {mode === 'quiz' && (
          <div style={{ padding:'18px 20px 40px' }}>
            <div style={{ display:'flex', alignItems:'center', gap:10, marginBottom:18 }}>
              <ProgressBar value={(qi+(picked!==null?1:0))/TQUIZ.length} color={accent} h={6}/>
              <span style={{ fontFamily:MONO, fontSize:12, color:C.fg2 }}>{qi+1}/{TQUIZ.length}</span>
            </div>
            <h2 style={{ fontFamily:DISPLAY, fontSize:21, fontWeight:600, color:C.fg, margin:'0 0 18px', lineHeight:1.3 }} dangerouslySetInnerHTML={{ __html: Q.q.replace(/`([^`]+)`/g, `<code style="font-family:${MONO};background:${C.card2};padding:1px 5px;border-radius:4px;color:${named('cyan')};font-size:0.82em">$1</code>`) }}/>
            <div style={{ display:'flex', flexDirection:'column', gap:10 }}>
              {Q.options.map((opt,i) => {
                const isAns = i === Q.answer, isPicked = i === picked;
                let bg = C.card, bd = C.sep, fg = C.fg, ic = null;
                if (picked !== null) {
                  if (isAns) { bg = softOf(C.green,0.14); bd = C.green; ic = 'checkCircle'; }
                  else if (isPicked) { bg = softOf(C.danger,0.14); bd = C.danger; ic = 'x'; }
                }
                return (
                  <button key={i} onClick={()=>choose(i)} disabled={picked!==null} style={{ textAlign:'left', background:bg, border:`1px solid ${bd}`, borderRadius:13, padding:'14px 15px', cursor:picked===null?'pointer':'default', display:'flex', alignItems:'center', gap:11, fontFamily:FONT, fontSize:14.5, color:fg }}>
                    <div style={{ width:24, height:24, borderRadius:'50%', border:`1.5px solid ${picked!==null&&(isAns||isPicked)?bd:C.fg3}`, display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0, color:bd, fontFamily:MONO, fontSize:11 }}>
                      {ic ? <TIcon name={ic} size={14} color={bd}/> : String.fromCharCode(65+i)}
                    </div>
                    <span style={{ flex:1 }}>{opt}</span>
                  </button>
                );
              })}
            </div>
            {picked !== null && (
              <div style={{ marginTop:16, padding:14, borderRadius:13, background:C.card, border:`0.5px solid ${C.sep}` }}>
                <div style={{ display:'flex', alignItems:'center', gap:8, marginBottom:6 }}>
                  <div style={{ width:22, height:22, borderRadius:'50%', background:`linear-gradient(135deg, ${accent}, ${C.purple})`, display:'flex', alignItems:'center', justifyContent:'center' }}><TIcon name="sparkles" size={12} color="#fff"/></div>
                  <span style={{ fontFamily:MONO, fontSize:10, letterSpacing:'0.06em', textTransform:'uppercase', color:accent, fontWeight:600 }}>Why</span>
                </div>
                <p style={{ fontFamily:FONT, fontSize:13.5, color:C.fg2, lineHeight:1.55, margin:0 }}>{Q.explain}</p>
                <button onClick={nextQ} style={{ marginTop:12, width:'100%', background:accent, color:'#fff', border:'none', borderRadius:11, padding:'11px', fontFamily:FONT, fontSize:14.5, fontWeight:600, cursor:'pointer' }}>{qi < TQUIZ.length-1 ? 'Next question' : 'See results'}</button>
              </div>
            )}
          </div>
        )}

        {mode === 'done' && (
          <div style={{ padding:'40px 24px', display:'flex', flexDirection:'column', alignItems:'center', textAlign:'center' }}>
            <div style={{ marginBottom:28 }}><BreathOrb size={108} color={accent}/></div>
            <h1 style={{ fontFamily:DISPLAY, fontSize:30, fontWeight:700, color:C.fg, margin:'0 0 8px' }}>{correct}/{TQUIZ.length} correct</h1>
            <p style={{ fontFamily:FONT, fontSize:15, color:C.fg2, lineHeight:1.6, maxWidth:280, margin:'0 0 8px' }}>{correct===TQUIZ.length ? 'Generics clicked. I\u2019ll weave them into your next project task so it sticks.' : 'Good start. I\u2019ll re-surface the tricky idea tomorrow in a 2-minute review.'}</p>
            <div style={{ display:'flex', gap:6, margin:'14px 0 24px', alignItems:'center' }}>
              <Pill color={C.gold} soft={softOf(C.gold)}><TIcon name="zap" size={11} color={C.gold}/> +40 xp</Pill>
              <Pill color={C.green} soft={softOf(C.green)}><TIcon name="flame" size={11} color={C.green}/> Streak kept</Pill>
            </div>
            <button onClick={onBack} style={{ width:'100%', maxWidth:300, background:accent, color:'#fff', border:'none', borderRadius:12, padding:'13px', fontFamily:FONT, fontSize:15, fontWeight:600, cursor:'pointer' }}>Back to learning</button>
          </div>
        )}
      </div>
    </div>
  );
}

Object.assign(window, { LearnScreen, LessonScreen });
