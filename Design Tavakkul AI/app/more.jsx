// Tafakkur AI — Career, Social, Finance + the "More" hub (tabs mode)

// ── More hub — 5 life dimensions, each with sub-modules ───────────────────────
// Colors mirror the balance radar on Today, so nav maps onto what the AI measures.
const DIM_COL = { spiritual:'#D8C79A', physical:'#A8C36A', educational:'#A78BFA', social:'#4CC2FF', financial:'#C9A24E' };

const MORE_SECTIONS = [
  {
    key:'spiritual', label:'Spiritual', native:'Ma\u02bcnaviy', icon:'moon',
    tagline:'Salah, reflection, gratitude',
    modules:[
      { name:'Tafakkur journal', sub:'Daily reflection',   icon:'penLine',  route:'tafakkur' },
      { name:'Gratitude',        sub:'Shukr \u00b7 3 a day', icon:'leaf',     route:'gratitude' },
      { name:'Prayer tracker',   sub:'5 daily salah',       icon:'sunrise',  route:'prayer' },
      { name:'Qur\u02bcan & dhikr', sub:'Recitation, adhkar', icon:'bookOpen', route:'quran' },
    ],
  },
  {
    key:'physical', label:'Physical', native:'Jismoniy', icon:'activity',
    tagline:'Energy, rest, movement',
    modules:[
      { name:'Habits',     sub:'Daily streaks',     icon:'repeat',   route:'do' },
      { name:'Focus',      sub:'Pomodoro sessions',  icon:'coffee',   route:'focus' },
      { name:'Sleep & rest',sub:'7h 10m last night', icon:'moon',     route:'sleep' },
      { name:'Sport & Gym', sub:'Train & track',      icon:'dumbbell', route:'sport' },
    ],
  },
  {
    key:'educational', label:'Educational', native:'Ta\u02bclim', icon:'bookOpen',
    tagline:'Learn, practise, grow',
    modules:[
      { name:'Subjects',     sub:'4 in progress',    icon:'languages',   route:'learn' },
      { name:'AI lessons',   sub:'Generated for you', icon:'sparkles',    route:'learn' },
      { name:'Quizzes',      sub:'Check understanding',icon:'checkCircle', route:'learn' },
      { name:'Career path',  sub:'CV \u00b7 interviews', icon:'briefcase', route:'career' },
    ],
  },
  {
    key:'social', label:'Social', native:'Ijtimoiy', icon:'users',
    tagline:'Community, ties, mentors',
    modules:[
      { name:'Thoughts', sub:'From people you follow', icon:'messageCircle', route:'thoughts' },
      { name:'Circles',  sub:'Your groups',            icon:'users',         route:'social' },
      { name:'Mentors',  sub:'Find guidance',          icon:'graduationCap', route:'mentors' },
    ],
  },
  {
    key:'financial', label:'Financial', native:'Moliyaviy', icon:'wallet',
    tagline:'Earn, save, give',
    modules:[
      { name:'Portfolio',   sub:'$8,420 tracked',    icon:'lineChart', route:'finance' },
      { name:'Money habits',sub:'Save, no impulse',  icon:'repeat',    route:'finance' },
      { name:'Insights',    sub:'Risk-aware',        icon:'trendingUp',route:'finance' },
      { name:'Sadaqah',     sub:'Giving log',        icon:'heart',     route:'sadaqah' },
    ],
  },
];

// Info for sub-modules without a full screen yet — rendered by StubScreen.
const STUB_INFO = {
  prayer:   { title:'Prayer tracker', native:'Namoz', icon:'sunrise',  color:DIM_COL.spiritual, dim:'Spiritual', note:'Log each of the five daily prayers. Murabbiy plans your day around the adhan and protects focus blocks between them.' },
  quran:    { title:'Qur\u02bcan & dhikr', native:'Tilovat', icon:'bookOpen', color:DIM_COL.spiritual, dim:'Spiritual', note:'Track recitation and a daily dhikr goal. Gentle, educational, non-denominational — never a ruling, only a reminder.' },
  sleep:    { title:'Sleep & rest', native:'Uyqu', icon:'moon', color:DIM_COL.physical, dim:'Physical', note:'Read from HealthKit. Last night you slept 7h 10m — Murabbiy uses this to time your hardest work when you are best rested.' },
  movement: { title:'Movement', native:'Harakat', icon:'activity', color:DIM_COL.physical, dim:'Physical', note:'Steps and activity, synced from Apple Health. A short walk after Asr is your most-missed habit — want a reminder?' },
  mentors:  { title:'Mentors', native:'Ustozlar', icon:'graduationCap', color:DIM_COL.social, dim:'Social', note:'Connect with people a few steps ahead of you in backend and English. Matched on your goals, privately and on your terms.' },
  sadaqah:  { title:'Sadaqah', native:'Sadaqa', icon:'heart', color:DIM_COL.financial, dim:'Financial', note:'A private log of your giving. Insight only — your generosity, tracked quietly, never shared or judged.' },
};

function DimensionSection({ section, score, onOpen }) {
  const col = DIM_COL[section.key];
  return (
    <div style={{ marginBottom:26 }}>
      {/* header */}
      <div style={{ display:'flex', alignItems:'center', gap:12, padding:'0 4px 12px' }}>
        <div style={{ width:38, height:38, borderRadius:11, background:softOf(col,0.16), display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
          <TIcon name={section.icon} size={20} color={col} sw={1.8}/>
        </div>
        <div style={{ flex:1, minWidth:0 }}>
          <div style={{ display:'flex', alignItems:'baseline', gap:8 }}>
            <span style={{ fontFamily:DISPLAY, fontSize:18, fontWeight:600, color:C.fg, letterSpacing:'0.2px' }}>{section.label}</span>
            <span style={{ fontFamily:MONO, fontSize:11, color:C.fg3 }}>{section.native}</span>
          </div>
          <div style={{ fontFamily:FONT, fontSize:12, color:C.fg3, marginTop:1 }}>{section.tagline}</div>
        </div>
        <div style={{ textAlign:'right', flexShrink:0 }}>
          <div style={{ fontFamily:DISPLAY, fontSize:17, fontWeight:700, color:col }}>{Math.round(score*100)}</div>
          <div style={{ fontFamily:MONO, fontSize:8.5, letterSpacing:'0.1em', color:C.fg3 }}>BALANCE</div>
        </div>
      </div>
      {/* dimension score bar */}
      <div style={{ padding:'0 4px 12px' }}><ProgressBar value={score} color={col} h={4}/></div>
      {/* sub-module grid */}
      <div style={{ display:'grid', gridTemplateColumns:'1fr 1fr', gap:10 }}>
        {section.modules.map((m,i) => (
          <button key={i} onClick={()=>onOpen(m.route)} style={{ textAlign:'left', background:C.card, border:`0.5px solid ${C.sep}`, borderRadius:14, padding:13, cursor:'pointer', display:'flex', flexDirection:'column', gap:10, minHeight:94 }}>
            <div style={{ width:34, height:34, borderRadius:10, background:softOf(col,0.14), display:'flex', alignItems:'center', justifyContent:'center' }}>
              <TIcon name={m.icon} size={18} color={col} sw={1.8}/>
            </div>
            <div>
              <div style={{ fontFamily:FONT, fontSize:13.5, fontWeight:600, color:C.fg, lineHeight:1.2 }}>{m.name}</div>
              <div style={{ fontFamily:FONT, fontSize:11.5, color:C.fg3, marginTop:2 }}>{m.sub}</div>
            </div>
          </button>
        ))}
      </div>
    </div>
  );
}

function MoreScreen({ onOpen }) {
  const scores = {}; TBALANCE.forEach(d => { scores[d.key] = d.value; });
  return (
    <div>
      <LargeTitle title="Explore" subtitle="Oʻzingni rivojlantir · by life dimension"/>
      <div style={{ padding:'4px 16px 0' }}>
        {/* AI framing */}
        <div style={{ display:'flex', gap:11, padding:'13px 15px', borderRadius:15, background:softOf('#C9A24E',0.08), border:'0.5px solid rgba(201,162,78,0.3)', marginBottom:22, alignItems:'flex-start' }}>
          <div style={{ width:28, height:28, borderRadius:'50%', background:'linear-gradient(135deg,#C9A24E,#7C5CFF)', display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
            <TIcon name="sparkles" size={14} color="#fff" sw={1.8}/>
          </div>
          <div style={{ fontFamily:FONT, fontSize:13, color:C.fg, lineHeight:1.5 }}>Everything here feeds your <b style={{ fontWeight:600 }}>balance</b>. Educational is your lowest dimension this month — a few lessons would even it out.</div>
        </div>

        {MORE_SECTIONS.map(s => <DimensionSection key={s.key} section={s} score={scores[s.key]} onOpen={onOpen}/>)}

        {/* appearance */}
        <Card pad={15} onClick={()=>onOpen('appearance')} style={{ display:'flex', alignItems:'center', gap:12, marginTop:4 }}>
          <IconBadge name="sparkles" color={named('teal')} size={40} isz={20}/>
          <div style={{ flex:1 }}>
            <div style={{ fontFamily:FONT, fontSize:14.5, fontWeight:600, color:C.fg }}>Appearance</div>
            <div style={{ fontFamily:FONT, fontSize:12.5, color:C.fg2 }}>Mode &amp; design system</div>
          </div>
          <TIcon name="chevronRight" size={18} color={C.fg3}/>
        </Card>

        {/* settings */}
        <Card pad={15} style={{ display:'flex', alignItems:'center', gap:12, marginTop:11 }}>
          <IconBadge name="settings" color={C.fg2} size={40} isz={20} soft="rgba(120,130,150,0.14)"/>
          <div style={{ flex:1 }}>
            <div style={{ fontFamily:FONT, fontSize:14.5, fontWeight:600, color:C.fg }}>Settings &amp; privacy</div>
            <div style={{ fontFamily:FONT, fontSize:12.5, color:C.fg2 }}>On-device AI, sync, data controls</div>
          </div>
          <TIcon name="chevronRight" size={18} color={C.fg3}/>
        </Card>
      </div>
      <div style={{ height:120 }}/>
    </div>
  );
}

// ── Stub screen for sub-modules without a full build yet ──────────────────────
function StubScreen({ info, onBack }) {
  const { accent } = useApp();
  const col = info.color;
  return (
    <div style={{ height:'100%', display:'flex', flexDirection:'column' }}>
      <BackBar title={info.title} onBack={onBack}/>
      <div style={{ flex:1, overflowY:'auto', padding:'30px 24px 40px', display:'flex', flexDirection:'column', alignItems:'center', textAlign:'center' }}>
        <div style={{ position:'relative', marginBottom:22 }}>
          <div style={{ position:'absolute', inset:-18, borderRadius:'50%', background:`radial-gradient(closest-side, ${softOf(col,0.35)}, transparent 70%)` }}/>
          <div style={{ position:'relative', width:84, height:84, borderRadius:'50%', background:softOf(col,0.16), border:`1px solid ${softOf(col,0.4)}`, display:'flex', alignItems:'center', justifyContent:'center' }}>
            <TIcon name={info.icon} size={36} color={col} sw={1.6}/>
          </div>
        </div>
        <div style={{ fontFamily:MONO, fontSize:10.5, letterSpacing:'0.12em', textTransform:'uppercase', color:col, marginBottom:8 }}>{info.dim} · {info.native}</div>
        <h1 style={{ fontFamily:DISPLAY, fontSize:28, fontWeight:700, color:C.fg, margin:'0 0 14px' }}>{info.title}</h1>
        <p style={{ fontFamily:FONT, fontSize:15, color:C.fg2, lineHeight:1.6, maxWidth:300, margin:'0 0 24px' }}>{info.note}</p>
        <div style={{ display:'flex', alignItems:'center', gap:9, padding:'12px 16px', borderRadius:13, background:C.card, border:`0.5px solid ${C.sep}`, maxWidth:320 }}>
          <div style={{ width:26, height:26, borderRadius:'50%', background:`linear-gradient(135deg, ${accent}, ${C.purple})`, display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
            <TIcon name="sparkles" size={13} color="#fff"/>
          </div>
          <span style={{ fontFamily:FONT, fontSize:12.5, color:C.fg2, lineHeight:1.45, textAlign:'left' }}>Murabbiy is already learning from this to shape your daily balance.</span>
        </div>
        <button style={{ marginTop:20, background:col, color:'#0B0F17', border:'none', borderRadius:12, padding:'12px 22px', fontFamily:FONT, fontSize:14.5, fontWeight:600, cursor:'pointer' }}>Set up {info.title.toLowerCase()}</button>
      </div>
    </div>
  );
}

// ── Career ───────────────────────────────────────────────────────────────────
function CareerScreen({ onBack }) {
  const { accent } = useApp();
  const D = TCAREER;
  const [tab, setTab] = React.useState('roadmap');
  return (
    <div style={{ height:'100%', display:'flex', flexDirection:'column' }}>
      <BackBar title="Career" onBack={onBack}/>
      <div style={{ flex:1, overflowY:'auto' }}>
        <div style={{ padding:'14px 20px 0' }}>
          <div style={{ fontFamily:MONO, fontSize:11, color:C.fg3, letterSpacing:'0.06em', marginBottom:4 }}>TARGET ROLE</div>
          <h1 style={{ fontFamily:DISPLAY, fontSize:26, fontWeight:700, color:C.fg, margin:'0 0 14px' }}>{D.role}</h1>
        </div>
        <div style={{ padding:'0 16px 12px' }}>
          <Segmented tabs={[['roadmap','Roadmap'],['cv','CV'],['interview','Interview']]} value={tab} onChange={setTab}/>
        </div>

        {tab === 'roadmap' && (
          <div style={{ padding:'4px 16px 120px' }}>
            <ProactiveCard text="You're ahead on fundamentals. The fastest lever now is proof of work — let's ship 2 backend projects this month." compact/>
            <div style={{ height:14 }}/>
            <div style={{ display:'flex', flexDirection:'column', gap:11 }}>
              {D.roadmap.map((p,i) => {
                const col = p.done?C.green:p.current?accent:C.fg3;
                return (
                  <Card key={i} pad={15} style={{ border:`0.5px solid ${p.current?accent+'55':C.sep}` }}>
                    <div style={{ display:'flex', alignItems:'center', gap:9, marginBottom:9 }}>
                      <div style={{ width:24, height:24, borderRadius:'50%', background:p.done?C.green:softOf(col), display:'flex', alignItems:'center', justifyContent:'center' }}>
                        {p.done ? <TIcon name="check" size={13} color="#fff" sw={3}/> : <span style={{ fontFamily:MONO, fontSize:11, color:col, fontWeight:600 }}>{i+1}</span>}
                      </div>
                      <span style={{ fontFamily:MONO, fontSize:10.5, letterSpacing:'0.06em', textTransform:'uppercase', color:col, fontWeight:600 }}>{p.phase}</span>
                    </div>
                    <div style={{ fontFamily:DISPLAY, fontSize:16, fontWeight:600, color:C.fg, marginBottom:8 }}>{p.title}</div>
                    <div style={{ display:'flex', flexWrap:'wrap', gap:6 }}>
                      {p.items.map((it,j) => <span key={j} style={{ background:C.card2, borderRadius:999, padding:'4px 10px', fontFamily:FONT, fontSize:12, color:C.fg2 }}>{it}</span>)}
                    </div>
                  </Card>
                );
              })}
            </div>
            <div style={{ marginTop:18 }}>
              <div style={{ fontFamily:MONO, fontSize:10.5, letterSpacing:'0.07em', textTransform:'uppercase', color:C.fg3, padding:'0 4px 9px' }}>University matches</div>
              {D.universities.map((u,i) => (
                <Card key={i} pad={14} style={{ display:'flex', alignItems:'center', gap:12, marginBottom:9 }}>
                  <IconBadge name="graduationCap" color={accent} size={38} isz={19}/>
                  <div style={{ flex:1 }}>
                    <div style={{ fontFamily:FONT, fontSize:14.5, fontWeight:600, color:C.fg }}>{u.name}</div>
                    <div style={{ fontFamily:FONT, fontSize:12.5, color:C.fg2 }}>{u.program} · due {u.deadline}</div>
                  </div>
                  <div style={{ textAlign:'right' }}>
                    <div style={{ fontFamily:DISPLAY, fontSize:18, fontWeight:700, color:C.green }}>{u.match}%</div>
                    <div style={{ fontFamily:MONO, fontSize:9.5, color:C.fg3 }}>MATCH</div>
                  </div>
                </Card>
              ))}
            </div>
          </div>
        )}

        {tab === 'cv' && (
          <div style={{ padding:'4px 16px 120px' }}>
            <Card pad={18} style={{ textAlign:'center', marginBottom:14 }}>
              <div style={{ position:'relative', width:120, height:120, margin:'0 auto 8px' }}>
                <svg width="120" height="120" style={{ transform:'rotate(-90deg)' }}>
                  <circle cx="60" cy="60" r="50" fill="none" stroke="rgba(120,130,150,0.16)" strokeWidth="9"/>
                  <circle cx="60" cy="60" r="50" fill="none" stroke={accent} strokeWidth="9" strokeLinecap="round" strokeDasharray={2*Math.PI*50} strokeDashoffset={2*Math.PI*50*(1-D.cvScore/100)}/>
                </svg>
                <div style={{ position:'absolute', inset:0, display:'flex', flexDirection:'column', alignItems:'center', justifyContent:'center' }}>
                  <div style={{ fontFamily:DISPLAY, fontSize:32, fontWeight:700, color:C.fg }}>{D.cvScore}</div>
                  <div style={{ fontFamily:MONO, fontSize:9.5, color:C.fg3 }}>CV SCORE</div>
                </div>
              </div>
              <div style={{ fontFamily:FONT, fontSize:13.5, color:C.fg2 }}>Strong. A few tweaks push you past the screening bar.</div>
            </Card>
            <div style={{ fontFamily:MONO, fontSize:10.5, letterSpacing:'0.07em', textTransform:'uppercase', color:C.fg3, padding:'4px 4px 9px' }}>AI suggestions</div>
            <Card pad={4}>
              {D.cvTips.map((t,i) => (
                <div key={i} style={{ display:'flex', alignItems:'flex-start', gap:11, padding:'12px 13px', borderBottom:i<D.cvTips.length-1?`0.5px solid ${C.sep}`:'none' }}>
                  <div style={{ width:22, height:22, borderRadius:'50%', background:`linear-gradient(135deg, ${accent}, ${C.purple})`, display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0, marginTop:1 }}><TIcon name="sparkles" size={12} color="#fff"/></div>
                  <span style={{ fontFamily:FONT, fontSize:14, color:C.fg, lineHeight:1.5 }}>{t}</span>
                </div>
              ))}
            </Card>
            <button style={{ marginTop:14, width:'100%', background:accent, color:'#fff', border:'none', borderRadius:12, padding:'13px', fontFamily:FONT, fontSize:15, fontWeight:600, cursor:'pointer', display:'flex', alignItems:'center', justifyContent:'center', gap:8 }}>
              <TIcon name="fileText" size={16} color="#fff"/> Open CV editor
            </button>
          </div>
        )}

        {tab === 'interview' && (
          <div style={{ padding:'4px 16px 120px' }}>
            <Card pad={16} style={{ display:'flex', alignItems:'center', gap:13, marginBottom:14 }}>
              <BreathOrb size={54} color={accent}/>
              <div style={{ flex:1 }}>
                <div style={{ fontFamily:DISPLAY, fontSize:16, fontWeight:600, color:C.fg }}>Mock interview</div>
                <div style={{ fontFamily:FONT, fontSize:12.5, color:C.fg2, marginTop:1 }}>Practise out loud. AI plays the interviewer and scores you.</div>
              </div>
            </Card>
            <div style={{ fontFamily:MONO, fontSize:10.5, letterSpacing:'0.07em', textTransform:'uppercase', color:C.fg3, padding:'4px 4px 9px' }}>Practice questions</div>
            <div style={{ display:'flex', flexDirection:'column', gap:9 }}>
              {D.interviews.map(iv => (
                <Card key={iv.id} pad={14} onClick={()=>{}} style={{ display:'flex', alignItems:'center', gap:12, cursor:'pointer' }}>
                  <div style={{ flex:1 }}>
                    <div style={{ display:'flex', alignItems:'center', gap:7, marginBottom:3 }}>
                      <span style={{ fontFamily:MONO, fontSize:10, color:accent, letterSpacing:'0.05em', textTransform:'uppercase' }}>{iv.kind}</span>
                      <Pill color={iv.difficulty==='Easy'?C.green:C.gold} soft={softOf(iv.difficulty==='Easy'?C.green:C.gold)}>{iv.difficulty}</Pill>
                    </div>
                    <div style={{ fontFamily:FONT, fontSize:14.5, color:C.fg }}>{iv.q}</div>
                  </div>
                  <div style={{ width:34, height:34, borderRadius:'50%', background:softOf(accent), display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}><TIcon name="mic" size={16} color={accent}/></div>
                </Card>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

// ── Social ───────────────────────────────────────────────────────────────────
function SocialScreen({ onBack }) {
  const { accent } = useApp();
  const [liked, setLiked] = React.useState({});
  const toggle = id => setLiked(l => ({ ...l, [id]: !l[id] }));
  return (
    <div style={{ height:'100%', display:'flex', flexDirection:'column' }}>
      <BackBar title="Community" onBack={onBack} right={<TIcon name="penLine" size={19} color={accent}/>}/>
      <div style={{ flex:1, overflowY:'auto', padding:'12px 16px 120px' }}>
        {/* compose */}
        <Card pad={14} style={{ display:'flex', alignItems:'center', gap:11, marginBottom:14 }}>
          <div style={{ width:34, height:34, borderRadius:'50%', background:`linear-gradient(135deg, ${C.gold}, ${C.rose})`, display:'flex', alignItems:'center', justifyContent:'center', fontFamily:FONT, fontSize:14, fontWeight:600, color:'#fff', flexShrink:0 }}>A</div>
          <span style={{ flex:1, fontFamily:FONT, fontSize:14, color:C.fg3 }}>Share a thought…</span>
          <button style={{ background:accent, border:'none', borderRadius:999, padding:'8px 14px', color:'#fff', fontFamily:FONT, fontSize:13, fontWeight:600, cursor:'pointer' }}>Publish</button>
        </Card>

        {/* groups */}
        <div style={{ display:'flex', gap:9, overflowX:'auto', paddingBottom:14 }}>
          {TGROUPS.map(g => (
            <div key={g.id} style={{ minWidth:140, background:C.card, border:`0.5px solid ${C.sep}`, borderRadius:14, padding:13 }}>
              <div style={{ display:'flex', alignItems:'center', justifyContent:'space-between', marginBottom:9 }}>
                <IconBadge name={g.icon} color={accent} size={32} isz={16}/>
                {g.active && <span style={{ width:7, height:7, borderRadius:4, background:C.green }}/>}
              </div>
              <div style={{ fontFamily:FONT, fontSize:13.5, fontWeight:600, color:C.fg }}>{g.name}</div>
              <div style={{ fontFamily:MONO, fontSize:10.5, color:C.fg3, marginTop:2 }}>{g.members.toLocaleString()} members</div>
            </div>
          ))}
        </div>

        {/* feed */}
        <div style={{ display:'flex', flexDirection:'column', gap:11 }}>
          {TSOCIAL.map(p => {
            const isL = liked[p.id];
            const likes = p.likes + (isL?1:0);
            return (
              <Card key={p.id} pad={16}>
                <div style={{ display:'flex', alignItems:'center', gap:10, marginBottom:10 }}>
                  <Avatar name={p.author} seed={p.avatarSeed} size={36}/>
                  <div style={{ flex:1 }}>
                    <div style={{ fontFamily:FONT, fontSize:14, fontWeight:600, color:C.fg }}>{p.author}</div>
                    <div style={{ fontFamily:FONT, fontSize:12, color:C.fg3 }}>{p.handle} · {p.time}</div>
                  </div>
                  <Pill color={accent} soft={softOf(accent)}><TIcon name="hash" size={10} color={accent}/>{p.tag}</Pill>
                </div>
                <p style={{ fontFamily:FONT, fontSize:14.5, color:C.fg, lineHeight:1.6, margin:'0 0 12px' }}>{p.text}</p>
                <div style={{ display:'flex', alignItems:'center', gap:18 }}>
                  <button onClick={()=>toggle(p.id)} style={{ display:'flex', alignItems:'center', gap:6, background:'none', border:'none', cursor:'pointer', color:isL?C.rose:C.fg3 }}>
                    <TIcon name="heart" size={17} color={isL?C.rose:C.fg3}/><span style={{ fontFamily:MONO, fontSize:12.5 }}>{likes}</span>
                  </button>
                  <button style={{ display:'flex', alignItems:'center', gap:6, background:'none', border:'none', cursor:'pointer', color:C.fg3 }}>
                    <TIcon name="messageCircle" size={16} color={C.fg3}/><span style={{ fontFamily:MONO, fontSize:12.5 }}>{p.comments}</span>
                  </button>
                  <span style={{ flex:1 }}/>
                  <TIcon name="bookmark" size={16} color={C.fg3}/>
                </div>
              </Card>
            );
          })}
        </div>
      </div>
    </div>
  );
}

// ── Finance ──────────────────────────────────────────────────────────────────
function FinanceScreen({ onBack }) {
  const { accent } = useApp();
  const D = TFINANCE;
  return (
    <div style={{ height:'100%', display:'flex', flexDirection:'column' }}>
      <BackBar title="Finance" onBack={onBack}/>
      <div style={{ flex:1, overflowY:'auto', padding:'14px 16px 120px' }}>
        {/* net worth */}
        <div style={{ position:'relative', overflow:'hidden', borderRadius:18, padding:'20px 18px', background:`linear-gradient(150deg, ${softOf(accent,0.18)}, rgba(124,92,255,0.05))`, border:`0.5px solid ${accent}40`, marginBottom:8 }}>
          <div style={{ fontFamily:MONO, fontSize:10.5, letterSpacing:'0.08em', textTransform:'uppercase', color:C.fg2, marginBottom:6 }}>Net worth · tracked</div>
          <div style={{ display:'flex', alignItems:'baseline', gap:10 }}>
            <div style={{ fontFamily:DISPLAY, fontSize:38, fontWeight:700, color:C.fg }}>${D.net.toLocaleString()}</div>
            <div style={{ display:'flex', alignItems:'center', gap:3, color:C.green }}>
              <TIcon name="trendingUp" size={16} color={C.green}/>
              <span style={{ fontFamily:MONO, fontSize:13, fontWeight:600 }}>{D.change}%</span>
            </div>
          </div>
          <div style={{ fontFamily:FONT, fontSize:12.5, color:C.fg2, marginTop:4 }}>+${D.changeAbs} this month</div>
        </div>

        {/* allocation bar */}
        <Card pad={16} style={{ marginBottom:14 }}>
          <div style={{ fontFamily:DISPLAY, fontSize:16, fontWeight:600, color:C.fg, marginBottom:12 }}>Allocation</div>
          <div style={{ display:'flex', height:12, borderRadius:6, overflow:'hidden', marginBottom:14 }}>
            {D.allocations.map((a,i) => <div key={i} style={{ width:`${a.pct}%`, background:named(a.color) }}/>)}
          </div>
          <div style={{ display:'flex', flexDirection:'column', gap:10 }}>
            {D.allocations.map((a,i) => (
              <div key={i} style={{ display:'flex', alignItems:'center', gap:10 }}>
                <span style={{ width:10, height:10, borderRadius:3, background:named(a.color) }}/>
                <span style={{ flex:1, fontFamily:FONT, fontSize:13.5, color:C.fg }}>{a.name}</span>
                <span style={{ fontFamily:MONO, fontSize:12.5, color:C.fg2 }}>${a.value.toLocaleString()}</span>
                <span style={{ fontFamily:MONO, fontSize:12.5, color:C.fg3, width:36, textAlign:'right' }}>{a.pct}%</span>
              </div>
            ))}
          </div>
        </Card>

        {/* habits */}
        <div style={{ fontFamily:MONO, fontSize:10.5, letterSpacing:'0.07em', textTransform:'uppercase', color:C.fg3, padding:'4px 4px 9px' }}>Financial habits</div>
        <div style={{ display:'flex', flexDirection:'column', gap:9, marginBottom:14 }}>
          {D.habits.map(h => {
            const st = h.status==='done'?C.green:h.status==='watch'?C.gold:accent;
            return (
              <Card key={h.id} pad={14}>
                <div style={{ display:'flex', justifyContent:'space-between', marginBottom:9 }}>
                  <span style={{ fontFamily:FONT, fontSize:14, color:C.fg }}>{h.name}</span>
                  <Pill color={st} soft={softOf(st)}>{h.status}</Pill>
                </div>
                <ProgressBar value={h.progress} color={st}/>
              </Card>
            );
          })}
        </div>

        {/* AI insights */}
        <ProactiveCard text={D.insights[0]} />
        <div style={{ marginTop:11, display:'flex', flexDirection:'column', gap:9 }}>
          {D.insights.slice(1).map((t,i) => (
            <Card key={i} pad={13} style={{ display:'flex', gap:10, alignItems:'flex-start' }}>
              <TIcon name="lineChart" size={16} color={accent} style={{ marginTop:2 }}/>
              <span style={{ fontFamily:FONT, fontSize:13.5, color:C.fg2, lineHeight:1.5 }}>{t}</span>
            </Card>
          ))}
        </div>

        <div style={{ marginTop:16, padding:'12px 14px', borderRadius:12, border:`0.5px solid ${C.sep}`, display:'flex', gap:10, alignItems:'center' }}>
          <TIcon name="lock" size={15} color={C.fg3}/>
          <span style={{ fontFamily:FONT, fontSize:12, color:C.fg3, lineHeight:1.5 }}>Educational insights only — not financial advice. You set your own risk bands.</span>
        </div>
      </div>
    </div>
  );
}

// ── Thoughts (primary tab) — read friends' thoughts + post your own ──────────
function ThoughtCard({ p, liked, onLike }) {
  const { accent } = useApp();
  const isL = liked[p.id];
  const likes = p.likes + (isL?1:0);
  return (
    <Card pad={16}>
      <div style={{ display:'flex', alignItems:'center', gap:10, marginBottom:10 }}>
        {p.mine
          ? <div style={{ width:36, height:36, borderRadius:'50%', background:`linear-gradient(135deg, ${C.gold}, ${C.rose})`, display:'flex', alignItems:'center', justifyContent:'center', fontFamily:FONT, fontSize:15, fontWeight:600, color:'#fff', flexShrink:0 }}>A</div>
          : <Avatar name={p.author} seed={p.avatarSeed} size={36}/>}
        <div style={{ flex:1, minWidth:0 }}>
          <div style={{ fontFamily:FONT, fontSize:14, fontWeight:600, color:C.fg }}>{p.author}{p.mine && <span style={{ fontFamily:MONO, fontSize:10, color:accent, marginLeft:7 }}>YOU</span>}</div>
          <div style={{ fontFamily:FONT, fontSize:12, color:C.fg3 }}>{p.handle} · {p.time}</div>
        </div>
        <Pill color={accent} soft={softOf(accent)}><TIcon name="hash" size={10} color={accent}/>{p.tag}</Pill>
      </div>
      <p style={{ fontFamily:FONT, fontSize:14.5, color:C.fg, lineHeight:1.6, margin:'0 0 12px' }}>{p.text}</p>
      <div style={{ display:'flex', alignItems:'center', gap:18 }}>
        <button onClick={()=>onLike(p.id)} style={{ display:'flex', alignItems:'center', gap:6, background:'none', border:'none', cursor:'pointer', color:isL?C.rose:C.fg3 }}>
          <TIcon name="heart" size={17} color={isL?C.rose:C.fg3}/><span style={{ fontFamily:MONO, fontSize:12.5 }}>{likes}</span>
        </button>
        <button style={{ display:'flex', alignItems:'center', gap:6, background:'none', border:'none', cursor:'pointer', color:C.fg3 }}>
          <TIcon name="messageCircle" size={16} color={C.fg3}/><span style={{ fontFamily:MONO, fontSize:12.5 }}>{p.comments}</span>
        </button>
        <span style={{ flex:1 }}/>
        <TIcon name="bookmark" size={16} color={C.fg3}/>
      </div>
    </Card>
  );
}

function ThoughtsScreen({ posts, onCompose }) {
  const { accent } = useApp();
  const [liked, setLiked] = React.useState({});
  const [filter, setFilter] = React.useState('all');
  const onLike = id => setLiked(l => ({ ...l, [id]: !l[id] }));
  const shown = filter==='mine' ? posts.filter(p=>p.mine) : posts;
  return (
    <div>
      <LargeTitle title="Thoughts" subtitle="Fikrlar · from people you follow" right={
        <button onClick={onCompose} aria-label="New thought" style={{ width:40, height:40, borderRadius:'50%', border:'none', cursor:'pointer', background:accent, display:'flex', alignItems:'center', justifyContent:'center', boxShadow:`0 4px 14px ${softOf(accent,0.5)}` }}>
          <TIcon name="plus" size={22} color="#fff" sw={2.2}/>
        </button>
      }/>
      <div style={{ padding:'10px 16px 0' }}>
        {/* circles */}
        <div style={{ display:'flex', gap:9, overflowX:'auto', paddingBottom:4 }}>
          {TGROUPS.map(g => (
            <div key={g.id} style={{ minWidth:132, background:C.card, border:`0.5px solid ${C.sep}`, borderRadius:14, padding:12 }}>
              <div style={{ display:'flex', alignItems:'center', justifyContent:'space-between', marginBottom:8 }}>
                <IconBadge name={g.icon} color={accent} size={30} isz={15}/>
                {g.active && <span style={{ width:7, height:7, borderRadius:4, background:C.green }}/>}
              </div>
              <div style={{ fontFamily:FONT, fontSize:13, fontWeight:600, color:C.fg }}>{g.name}</div>
              <div style={{ fontFamily:MONO, fontSize:10, color:C.fg3, marginTop:2 }}>{g.members.toLocaleString()} members</div>
            </div>
          ))}
        </div>
        {/* filter */}
        <div style={{ padding:'12px 0 14px' }}>
          <Segmented tabs={[['all','Following'],['mine','Your thoughts']]} value={filter} onChange={setFilter}/>
        </div>
      </div>

      <div style={{ padding:'0 16px', display:'flex', flexDirection:'column', gap:11 }}>
        {shown.length === 0
          ? <div style={{ textAlign:'center', padding:'40px 20px', fontFamily:FONT, fontSize:14, color:C.fg3 }}>You haven't shared a thought yet. Tap + to write one.</div>
          : shown.map(p => <ThoughtCard key={p.id} p={p} liked={liked} onLike={onLike}/>)}
      </div>
      <div style={{ height:120 }}/>
    </div>
  );
}

// ── Compose: write & publish a thought / experience / achievement ────────────
const THOUGHT_TYPES = [
  { k:'thought',     label:'Thought',     icon:'messageCircle', color:'rose', tag:'Mindset',     ph:"What's on your mind? A reflection, an idea, a lesson you learned today\u2026" },
  { k:'experience',  label:'Experience',  icon:'leaf',          color:'cyan', tag:'Experience',  ph:'Share an experience \u2014 something you went through, and what it taught you.' },
  { k:'achievement', label:'Achievement', icon:'star',          color:'gold', tag:'Win',         ph:'Celebrate a win. What did you accomplish, and how did you get there?' },
];
function ComposeThoughtScreen({ onBack, onPublish }) {
  const { accent } = useApp();
  const [type, setType] = React.useState('thought');
  const [text, setText] = React.useState('');
  const ref = React.useRef(null);
  React.useEffect(() => { if (ref.current) ref.current.focus(); }, []);
  const t = THOUGHT_TYPES.find(x => x.k === type);
  const col = named(t.color);
  const canPost = text.trim().length > 0;
  const publish = () => {
    if (!canPost) return;
    onPublish({ id:'me'+Date.now(), author:'Aziz', handle:'@aziz', avatarSeed:2, time:'now', text:text.trim(), likes:0, comments:0, tag:t.tag, mine:true, kind:type });
  };
  return (
    <div style={{ height:'100%', display:'flex', flexDirection:'column' }}>
      <div style={{ height:48, padding:'0 12px 0 16px', display:'flex', alignItems:'center', justifyContent:'space-between', borderBottom:`0.5px solid ${C.sep}`, position:'sticky', top:0, zIndex:5, background:'rgba(11,15,23,0.72)', backdropFilter:'blur(20px)', WebkitBackdropFilter:'blur(20px)' }}>
        <button onClick={onBack} style={{ background:'none', border:'none', color:C.fg2, fontFamily:FONT, fontSize:16, cursor:'pointer', padding:'6px 0' }}>Cancel</button>
        <span style={{ fontFamily:FONT, fontSize:16, fontWeight:600, color:C.fg }}>New {t.label.toLowerCase()}</span>
        <button onClick={publish} disabled={!canPost} style={{ border:'none', cursor:canPost?'pointer':'default', borderRadius:999, padding:'8px 16px', fontFamily:FONT, fontSize:14, fontWeight:600, background:canPost?accent:C.card2, color:canPost?'#fff':C.fg3 }}>Publish</button>
      </div>

      <div style={{ flex:1, overflowY:'auto', padding:'16px 16px 40px' }}>
        {/* type selector */}
        <div style={{ display:'flex', gap:8, marginBottom:18 }}>
          {THOUGHT_TYPES.map(x => {
            const xc = named(x.color); const on = x.k === type;
            return (
              <button key={x.k} onClick={()=>setType(x.k)} style={{ flex:1, display:'flex', flexDirection:'column', alignItems:'center', gap:6, padding:'12px 6px', borderRadius:14, cursor:'pointer', background:on?softOf(xc,0.14):C.card, border:`1px solid ${on?xc:C.sep}` }}>
                <TIcon name={x.icon} size={20} color={on?xc:C.fg2} sw={1.8}/>
                <span style={{ fontFamily:FONT, fontSize:12.5, fontWeight:on?600:500, color:on?C.fg:C.fg2 }}>{x.label}</span>
              </button>
            );
          })}
        </div>

        {/* author row */}
        <div style={{ display:'flex', alignItems:'center', gap:10, marginBottom:14 }}>
          <div style={{ width:38, height:38, borderRadius:'50%', background:`linear-gradient(135deg, ${C.gold}, ${C.rose})`, display:'flex', alignItems:'center', justifyContent:'center', fontFamily:FONT, fontSize:15, fontWeight:600, color:'#fff' }}>A</div>
          <div>
            <div style={{ fontFamily:FONT, fontSize:14.5, fontWeight:600, color:C.fg }}>Aziz</div>
            <div style={{ display:'inline-flex', alignItems:'center', gap:5, fontFamily:MONO, fontSize:10.5, color:C.fg3 }}><TIcon name="users" size={11} color={C.fg3}/> Public · to your followers</div>
          </div>
          <span style={{ flex:1 }}/>
          <Pill color={col} soft={softOf(col)}><TIcon name="hash" size={10} color={col}/>{t.tag}</Pill>
        </div>

        {/* editor */}
        <textarea ref={ref} value={text} onChange={e=>setText(e.target.value)} placeholder={t.ph} rows={8}
          style={{ width:'100%', boxSizing:'border-box', background:'transparent', border:'none', outline:'none', resize:'none', color:C.fg, fontFamily:FONT, fontSize:17, lineHeight:1.6, minHeight:180 }}/>

        {/* helper */}
        <div style={{ display:'flex', alignItems:'center', gap:8, marginTop:8, padding:'10px 12px', borderRadius:11, background:softOf(accent,0.08), border:`0.5px solid ${accent}33` }}>
          <TIcon name="sparkles" size={14} color={accent}/>
          <span style={{ fontFamily:FONT, fontSize:12.5, color:C.fg2, lineHeight:1.5 }}>Murabbiy can help you phrase this — tap to refine tone or summarise.</span>
        </div>
      </div>
    </div>
  );
}

Object.assign(window, { MoreScreen, StubScreen, STUB_INFO, CareerScreen, SocialScreen, FinanceScreen, ThoughtsScreen, ComposeThoughtScreen });
