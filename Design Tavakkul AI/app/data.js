// Tafakkur AI — mock data (single source of truth for the prototype)
// English-primary copy with natural Uzbek touches.

const TUSER = {
  name: 'Aziz',
  handle: '@aziz',
  greetingUz: 'Xayrli tong',      // Good morning
  greetingEn: 'Good morning',
  streak: 23,
  level: 7,
  xp: 1840,
  xpNext: 2200,
  today: 'Tuesday, June 10',
  coach: 'Murabbiy',              // "mentor/coach" in Uzbek
};

// AI daily brief shown at top of Today
const TBRIEF = {
  line: "You've got a clear morning. I moved your TypeScript lesson before deep work, and protected 90 minutes for it — your focus scores are highest before noon.",
  signals: [
    { icon: 'sun', text: 'Light day — 3 meetings cleared' },
    { icon: 'target', text: '2 of 4 weekly goals on track' },
    { icon: 'moon', text: 'Slept 7h 10m — well rested' },
  ],
};

// Today's plan — mixed timeline of all modules
const TPLAN = [
  { id: 'p1', time: '07:30', dur: '15m', kind: 'reflect', title: 'Morning tafakkur', sub: 'Gratitude — 3 things', done: true, module: 'Reflect' },
  { id: 'p2', time: '08:00', dur: '45m', kind: 'focus', title: 'Deep work — API refactor', sub: 'Focus session · Pomodoro', done: true, module: 'Productivity' },
  { id: 'p3', time: '09:30', dur: '20m', kind: 'learn', title: 'TypeScript — generics', sub: 'AI lesson + quiz', done: false, current: true, module: 'Learn' },
  { id: 'p4', time: '12:00', dur: '10m', kind: 'task', title: 'Reply to mentor email', sub: 'Career · interview follow-up', done: false, module: 'Career' },
  { id: 'p5', time: '18:00', dur: '25m', kind: 'learn', title: 'English — speaking drill', sub: 'Pronunciation with AI', done: false, module: 'Learn' },
  { id: 'p6', time: '21:30', dur: '10m', kind: 'reflect', title: 'Evening reflection', sub: 'Review the day', done: false, module: 'Reflect' },
];

// Habits
const THABITS = [
  { id: 'h1', name: 'Morning reflection', icon: 'sunrise', streak: 23, week: [1,1,1,1,1,0,0], done: true, accent: 'gold' },
  { id: 'h2', name: 'Read 20 pages', icon: 'book', streak: 8, week: [1,1,0,1,1,0,0], done: false, accent: 'blue' },
  { id: 'h3', name: 'Code 1 hour', icon: 'code', streak: 14, week: [1,1,1,1,1,0,0], done: false, accent: 'blue' },
  { id: 'h4', name: 'Walk 8k steps', icon: 'activity', streak: 5, week: [1,0,1,1,0,0,0], done: false, accent: 'green' },
  { id: 'h5', name: 'No screens after 22:00', icon: 'moon', streak: 3, week: [1,1,1,0,1,0,0], done: false, accent: 'gold' },
];

// Tasks (productivity)
const TTASKS = [
  { id: 't1', title: 'Refactor auth middleware', tag: 'Work', priority: 'high', done: false, due: 'Today' },
  { id: 't2', title: 'Prepare interview answers — system design', tag: 'Career', priority: 'high', done: false, due: 'Today' },
  { id: 't3', title: 'Update CV — add Rust project', tag: 'Career', priority: 'med', done: false, due: 'Tomorrow' },
  { id: 't4', title: 'Buy groceries', tag: 'Life', priority: 'low', done: false, due: 'Tomorrow' },
  { id: 't5', title: 'Review pull requests', tag: 'Work', priority: 'med', done: true, due: 'Today' },
  { id: 't6', title: 'Call family', tag: 'Life', priority: 'med', done: true, due: 'Today' },
];

const TWEEK_GOALS = [
  { id: 'g1', title: 'Finish TypeScript module 3', progress: 0.6 },
  { id: 'g2', title: 'Solve 10 algorithm problems', progress: 0.4 },
  { id: 'g3', title: 'Submit 3 job applications', progress: 0.33 },
];

// Learning subjects
const TSUBJECTS = [
  { id: 's-en', name: 'English', native: 'Ingliz tili', icon: 'languages', seed: 4, level: 'B2 · Upper-intermediate', progress: 0.72, next: 'Speaking — describing trends', streak: 19, accent: 'cyan' },
  { id: 's-prog', name: 'Programming', native: 'Dasturlash', icon: 'terminal', seed: 0, level: 'TypeScript · Intermediate', progress: 0.45, next: 'Generics in practice', streak: 14, accent: 'blue' },
  { id: 's-math', name: 'Mathematics', native: 'Matematika', icon: 'sigma', seed: 2, level: 'Calculus I', progress: 0.30, next: 'Derivatives — chain rule', streak: 6, accent: 'purple' },
  { id: 's-fin', name: 'Finance basics', native: 'Moliya asoslari', icon: 'lineChart', seed: 6, level: 'Foundations', progress: 0.18, next: 'Compound interest', streak: 3, accent: 'gold' },
];

// An AI-generated lesson (Programming · generics)
const TLESSON = {
  subject: 'Programming',
  module: 'Module 03',
  index: 'Lesson 02',
  title: 'Generics in practice',
  minutes: 12,
  intro: "Generics let you write a function once and reuse it for many types without losing type safety. Think of them as type-level parameters. I generated this lesson from where you left off — you were comfortable with interfaces but hadn't applied them to reusable utilities.",
  blocks: [
    { type: 'concept', heading: 'The problem', body: 'Without generics you either duplicate a function per type, or fall back to `any` and lose all safety. Generics give you one definition that adapts to the caller.' },
    { type: 'code', lang: 'typescript', code: "function first<T>(arr: T[]): T | undefined {\n  return arr[0];\n}\n\nconst n = first([1, 2, 3]);      // number | undefined\nconst s = first(['a', 'b']);     // string | undefined" },
    { type: 'concept', heading: 'Constraints', body: 'Use `extends` to require that a type has certain shape — here, anything with a `.length`.' },
    { type: 'code', lang: 'typescript', code: "function longest<T extends { length: number }>(a: T, b: T): T {\n  return a.length >= b.length ? a : b;\n}" },
  ],
};

// Quiz for the lesson
const TQUIZ = [
  {
    q: 'What does the type parameter `T` represent in `function first<T>(arr: T[]): T`?',
    options: ['A fixed type chosen by the library', 'A placeholder type the caller fills in', 'Always `any`', 'A runtime value'],
    answer: 1,
    explain: 'T is a type-level parameter — TypeScript infers or you supply it at the call site.',
  },
  {
    q: 'Why use `T extends { length: number }`?',
    options: ['To make T faster', 'To constrain T to types that have a length', 'To convert T to a string', 'It is only decoration'],
    answer: 1,
    explain: 'Constraints guarantee the shape you rely on inside the function.',
  },
];

// Coach chat thread
const TCHAT = [
  { from: 'ai', text: "Xayrli tong, Aziz. You finished your morning reflection — nice. Want me to set up today around your TypeScript goal, or keep it open?", time: '07:34' },
  { from: 'me', text: "Set it up around TypeScript. But I have an interview Thursday I'm nervous about.", time: '07:35' },
  { from: 'ai', text: "Understood. I'll protect 90 minutes this morning for generics, then add two short interview-prep blocks before Thursday — one on system design, one mock with me. Nerves usually drop once you've rehearsed out loud. Sound good?", time: '07:35' },
];

const TCHAT_SUGGEST = [
  'Plan my day',
  'Quiz me on generics',
  'Reflect on yesterday',
  'Prep for Thursday interview',
];

// What the AI remembers (personalization / memory)
const TMEMORY = [
  { icon: 'target', label: 'Goal', text: 'Land a backend role by autumn' },
  { icon: 'clock', label: 'Pattern', text: 'Focus peaks 8–11am' },
  { icon: 'book', label: 'Learning', text: 'TypeScript intermediate · English B2' },
  { icon: 'heart', label: 'Value', text: 'Daily tafakkur matters to you' },
];

// Tafakkur / reflection
const TPROMPTS = [
  'What is one thing you are grateful for this morning?',
  'Where did you notice patience being tested yesterday?',
  'What small good deed can you do today?',
  'What did today teach you about yourself?',
];

const TJOURNAL = [
  { id: 'j1', date: 'Yesterday', mood: 'calm', title: 'On patience', excerpt: "I lost my temper in a code review and regretted it. Tomorrow I'll pause before replying — a breath of sabr.", gratitude: ['Health', 'A patient mentor', 'Quiet morning'] },
  { id: 'j2', date: 'Jun 8', mood: 'grateful', title: 'Small wins', excerpt: "Shipped the feature I'd been avoiding. Reminded myself that consistency beats intensity.", gratitude: ['Family', 'Work that grows me', 'Rain'] },
  { id: 'j3', date: 'Jun 7', mood: 'tired', title: 'Slowing down', excerpt: "Pushed too hard this week. Rest is not laziness — it is part of the work.", gratitude: ['A good night sleep', 'Tea', 'A friend who listened'] },
];

const TGRATITUDE_TODAY = ['Health', 'Time to learn', 'A roof and warm food'];

// Career
const TCAREER = {
  role: 'Backend Engineer',
  cvScore: 78,
  cvTips: ['Add metrics to your Rust project', 'Lead with impact, not tasks', 'Trim summary to 2 lines'],
  roadmap: [
    { phase: 'Now', title: 'Strengthen fundamentals', items: ['Data structures', 'SQL & Postgres', 'System design basics'], done: true },
    { phase: 'Next 60 days', title: 'Build proof of work', items: ['Ship 2 backend projects', 'Write 3 technical posts'], done: false, current: true },
    { phase: 'Quarter', title: 'Apply & interview', items: ['Target 15 companies', 'Mock interviews', 'Negotiation prep'], done: false },
  ],
  interviews: [
    { id: 'iv1', kind: 'System design', q: 'Design a URL shortener', difficulty: 'Medium' },
    { id: 'iv2', kind: 'Behavioral', q: 'Tell me about a conflict you resolved', difficulty: 'Easy' },
    { id: 'iv3', kind: 'Coding', q: 'Two-sum, then scale it', difficulty: 'Easy' },
  ],
  universities: [
    { name: 'TU Munich', program: 'MSc Informatics', deadline: 'Jan 15', match: 86 },
    { name: 'KAIST', program: 'MSc Computer Science', deadline: 'Mar 01', match: 79 },
  ],
};

// Social
const TSOCIAL = [
  { id: 'so1', author: 'Dilnoza', handle: '@dilnoza', avatarSeed: 6, time: '2h', text: "Tafakkur for builders: the bug you avoid is the one you most need to face. Sat with a nasty race condition this morning — felt like a small jihad against my own impatience.", likes: 42, comments: 7, tag: 'Mindset' },
  { id: 'so2', author: 'Jasur', handle: '@jasur', avatarSeed: 0, time: '5h', text: "Day 100 of learning English with 20 minutes a day. Consistency really is a quiet superpower. Aziz, your speaking drills inspired me to start.", likes: 88, comments: 14, tag: 'Learning' },
  { id: 'so3', author: 'Madina', handle: '@madina', avatarSeed: 3, time: '1d', text: "Finished my first Postgres project. Six months ago I couldn't write a JOIN. Grateful for this community keeping me accountable.", likes: 61, comments: 9, tag: 'Wins' },
];

const TGROUPS = [
  { id: 'gr1', name: 'Backend learners', members: 1240, icon: 'server', active: true },
  { id: 'gr2', name: 'Daily tafakkur', members: 860, icon: 'moon', active: true },
  { id: 'gr3', name: 'English speaking club', members: 2100, icon: 'languages', active: false },
];

// Finance
const TFINANCE = {
  net: 8420,
  change: 4.2,
  changeAbs: 340,
  allocations: [
    { name: 'Index funds', pct: 52, color: 'blue', value: 4378 },
    { name: 'Cash savings', pct: 28, color: 'green', value: 2358 },
    { name: 'Tech stocks', pct: 14, color: 'gold', value: 1179 },
    { name: 'Crypto', pct: 6, color: 'purple', value: 505 },
  ],
  habits: [
    { id: 'f1', name: 'Save 20% of income', progress: 0.8, status: 'on track' },
    { id: 'f2', name: 'No impulse buys this week', progress: 0.6, status: 'watch' },
    { id: 'f3', name: 'Track every expense', progress: 1.0, status: 'done' },
  ],
  insights: [
    'Your savings rate is above your 6-month average — steady.',
    'Crypto is 6% of your portfolio. That sits inside the risk band you set.',
    'Subscriptions rose 12% this month. Worth a review.',
  ],
};

// ── Salah / prayer times (Tashkent) ─────────────────────────────────────────
// minutes-from-midnight used to place markers on the day ring. "Now" = 09:41.
const TPRAYERS = [
  { key:'fajr',    name:'Fajr',    short:'Fajr', time:'04:35', min:275,  icon:'sunrise' },
  { key:'sunrise', name:'Sunrise', short:'Sunr', time:'05:58', min:358,  icon:'sun' },
  { key:'dhuhr',   name:'Dhuhr',   short:'Dhuh', time:'12:51', min:771,  icon:'sun' },
  { key:'asr',     name:'Asr',     short:'Asr',  time:'17:18', min:1038, icon:'sun' },
  { key:'maghrib', name:'Maghrib', short:'Magh', time:'20:42', min:1242, icon:'sunset' },
  { key:'isha',    name:'Isha',    short:'Isha', time:'22:11', min:1331, icon:'moon' },
];
const TNOW = { min: 581, label: '09:41' };
const TNEXT = { name: 'Dhuhr', in: '3h 10m', at: '12:51' };
const THIJRI = '22 Dhul-Qa\'dah';
const TCITY = 'Tashkent';

// ── Balance radar (5 dimensions of a balanced life) ─────────────────────────
const TBALANCE = [
  { key:'physical',    label:'Physical',    value:0.70, color:'#A8C36A', desc:'Walk, rest, train, drink.' },
  { key:'social',      label:'Social',      value:0.55, color:'#4CC2FF', desc:'Family, ummah, ties of kinship.' },
  { key:'financial',   label:'Financial',   value:0.62, color:'#C9A24E', desc:'Earn lawfully, give sadaqah.' },
  { key:'educational', label:'Educational', value:0.42, color:'#A78BFA', desc:'Read, study, teach.' },
  { key:'spiritual',   label:'Spiritual',   value:0.84, color:'#E6E1D6', desc:"Salah, Qur'an, dhikr." },
];

// ── AI recommendations grounded in the user's data ──────────────────────────
const TRECS = [
  { icon:'clock', tone:'gold',   tag:'Before Dhuhr', text:"You have 3h 10m until Dhuhr \u2014 your focus peaks now. I protected 90 minutes for deep work before salah." },
  { icon:'book',  tone:'purple', tag:'Rebalance',    text:"Educational is your lowest dimension this month. 20 minutes of reading after Isha would bring it back into balance." },
];

// ── Salah-anchored day plan (prayers interleaved with tasks) ────────────────
const TDAYPLAN = [
  { type:'salah', name:'Fajr',    time:'04:35', state:'done' },
  { type:'task',  title:'Deep work',           time:'06:00', dur:'3h',  cat:'financial',   done:true },
  { type:'task',  title:'Gym',                 time:'10:00', dur:'1h',  cat:'physical',    done:false, current:true },
  { type:'salah', name:'Dhuhr',   time:'12:51', state:'next' },
  { type:'task',  title:'Family check-in',     time:'13:30', dur:'15m', cat:'social',      done:false },
  { type:'salah', name:'Asr',     time:'17:18', state:'upcoming' },
  { type:'task',  title:'Read \u00b7 Hadis & Hayot', time:'18:00', dur:'20m', cat:'educational', done:false },
  { type:'salah', name:'Maghrib', time:'20:42', state:'upcoming' },
  { type:'task',  title:'Recite Surah Yasin',  time:'21:00', dur:'15m', cat:'spiritual',   done:false },
  { type:'salah', name:'Isha',    time:'22:11', state:'upcoming' },
];

// ── Spiritual module data ────────────────────────────────────────────────────
// Prayer tracker — today's 5 prayers with state, plus streak + month grid.
const TPRAYER_TODAY = [
  { key:'fajr',    name:'Fajr',    arabic:'الفجر',  time:'04:35', state:'jamaat' },
  { key:'dhuhr',   name:'Dhuhr',   arabic:'الظهر',  time:'12:51', state:'ontime' },
  { key:'asr',     name:'Asr',     arabic:'العصر',  time:'17:18', state:'upcoming' },
  { key:'maghrib', name:'Maghrib', arabic:'المغرب', time:'20:42', state:'upcoming' },
  { key:'isha',    name:'Isha',    arabic:'العشاء', time:'22:11', state:'upcoming' },
];
const PRAYER_STATES = {
  jamaat:   { label:'Jamaat',  short:'Jamoat', color:'#2BB673', icon:'users' },
  ontime:   { label:'On time', short:'Vaqtida', color:'#C9A24E', icon:'check' },
  late:     { label:'Late',    short:'Kechikib', color:'#E0A93B', icon:'clock' },
  qada:     { label:'Qada',    short:'Qazo',   color:'#E5746B', icon:'repeat' },
  upcoming: { label:'Upcoming',short:'Keyin',  color:'#7E8799', icon:'circle' },
};
// 35-day completion grid (0 none .. 5 all five). Most recent last.
const TPRAYER_MONTH = [5,5,4,5,5,3,5, 5,5,5,4,5,5,5, 4,5,5,5,3,5,5, 5,4,5,5,5,5,4, 5,5,5,5,2,0,0];
const TPRAYER_STATS = { streak:18, jamaat:62, onTime:84, qada:3, monthPct:91 };

// Daily dhikr / tasbih targets
const TDHIKR = [
  { key:'subhan',  arabic:'سُبْحَانَ ٱللَّٰه', translit:'SubhanAllah',  meaning:'Glory be to Allah',        count:21, target:33, color:'#4CC2FF' },
  { key:'hamd',    arabic:'ٱلْحَمْدُ لِلَّٰه', translit:'Alhamdulillah', meaning:'All praise to Allah',      count:33, target:33, color:'#2BB673' },
  { key:'akbar',   arabic:'ٱللَّٰهُ أَكْبَر',  translit:'Allahu akbar',  meaning:'Allah is the greatest',    count:12, target:34, color:'#C9A24E' },
  { key:'istighfar', arabic:'أَسْتَغْفِرُ ٱللَّٰه', translit:'Astaghfirullah', meaning:'I seek forgiveness', count:7, target:100, color:'#A78BFA' },
];
// Adhkar collections (morning / evening)
const TADHKAR = [
  { key:'morning', label:'Morning adhkar', native:'Tongги zikr', icon:'sunrise', total:12, done:12, color:'#C9A24E' },
  { key:'evening', label:'Evening adhkar', native:'Kechki zikr', icon:'sunset',  total:12, done:4,  color:'#7C5CFF' },
];
// Qur'an — last read + a few surahs
const TQURAN_LAST = { surah:'Al-Mulk', surahNo:67, ayah:14, juz:29, pct:0.46, arabic:'الملك' };
const TQURAN_SURAHS = [
  { no:67, name:'Al-Mulk',   arabic:'الملك',   meaning:'The Sovereignty', ayahs:30, place:'Meccan', read:0.46 },
  { no:36, name:'Ya-Sin',    arabic:'يٰسٓ',     meaning:'Ya Sin',          ayahs:83, place:'Meccan', read:1 },
  { no:18, name:'Al-Kahf',   arabic:'الكهف',   meaning:'The Cave',        ayahs:110, place:'Meccan', read:0.2 },
  { no:55, name:'Ar-Rahman', arabic:'الرحمٰن', meaning:'The Beneficent',  ayahs:78, place:'Medinan', read:0 },
  { no:2,  name:'Al-Baqarah',arabic:'البقرة',  meaning:'The Cow',         ayahs:286, place:'Medinan', read:0.08 },
];
const TQURAN_VERSE = {
  arabic:'وَمَن يَتَّقِ ٱللَّٰهَ يَجْعَل لَّهُۥ مَخْرَجًا',
  translit:'Wa man yattaqi-llaha yaj‘al lahu makhraja',
  meaning:'And whoever is mindful of Allah — He will make a way out for them.',
  ref:'At-Talaq · 65:2',
};

// ── Physical: Sleep & Sport data ─────────────────────────────────────────────
const TSLEEP = {
  score: 82,
  asleep: '7h 10m', inBed: '7h 45m', bedtime: '23:20', wake: '06:30', goalH: 8,
  // hypnogram segments across the night (stage + minutes)
  stages: [
    { s:'awake', m:8 }, { s:'core', m:50 }, { s:'deep', m:42 }, { s:'rem', m:22 },
    { s:'core', m:58 }, { s:'deep', m:23 }, { s:'rem', m:40 }, { s:'core', m:48 },
    { s:'awake', m:7 }, { s:'rem', m:38 }, { s:'core', m:54 }, { s:'deep', m:0 },
  ],
  breakdown: [
    { s:'deep',  label:'Deep',  sub:'Restoration', min:65,  pct:0.15, color:'#3D5CCC' },
    { s:'rem',   label:'REM',   sub:'Dreaming',    min:100, pct:0.23, color:'#7C5CFF' },
    { s:'core',  label:'Core',  sub:'Light sleep', min:245, pct:0.57, color:'#4CC2FF' },
    { s:'awake', label:'Awake', sub:'Brief stirs', min:20,  pct:0.05, color:'#E0A93B' },
  ],
  metrics: [
    { label:'Resting HR', value:'54', unit:'bpm', icon:'heart',    color:'#E5746B' },
    { label:'Restful',    value:'88', unit:'%',   icon:'activity', color:'#2BB673' },
    { label:'Consistency',value:'High',unit:'',   icon:'repeat',   color:'#4CC2FF' },
  ],
  week: [6.5, 7.2, 6.8, 7.5, 7.0, 8.1, 7.17], // hours, last 7 days (today last)
  debt: '50m',
};
const SLEEP_COL = { awake:'#E0A93B', rem:'#7C5CFF', core:'#4CC2FF', deep:'#3D5CCC' };

const TSPORT_TYPES = [
  { key:'run',      name:'Running',  icon:'activity', met:9.8, color:'#E5746B' },
  { key:'gym',      name:'Strength', icon:'dumbbell', met:6.0, color:'#C9A24E' },
  { key:'football', name:'Football', icon:'target',   met:7.0, color:'#2BB673' },
  { key:'cycling',  name:'Cycling',  icon:'repeat',   met:7.5, color:'#4CC2FF' },
  { key:'swim',     name:'Swimming', icon:'activity', met:8.0, color:'#7C5CFF' },
  { key:'walk',     name:'Walking',  icon:'activity', met:3.5, color:'#A8C36A' },
];
const TSPORT_GOALS = ['Lose weight', 'Stay active', 'Build strength'];
const TSPORT_DURATIONS = [15, 30, 45, 60, 90];
const TSPORT_DEFAULT = {
  profile: null, // { height, weight, age, goal } — null triggers setup
  activities: [ { id:'a1', key:'gym', name:'Strength', icon:'dumbbell', color:'#C9A24E', min:45, kcal:284, time:'06:00' } ],
  weekMin: [40, 0, 55, 30, 0, 45, 45], // active minutes, last 7 (today last)
};

Object.assign(window, {
  TUSER, TBRIEF, TPLAN, THABITS, TTASKS, TWEEK_GOALS, TSUBJECTS, TLESSON, TQUIZ,
  TCHAT, TCHAT_SUGGEST, TMEMORY, TPROMPTS, TJOURNAL, TGRATITUDE_TODAY,
  TCAREER, TSOCIAL, TGROUPS, TFINANCE,
  TPRAYERS, TNOW, TNEXT, THIJRI, TCITY, TBALANCE, TRECS, TDAYPLAN,
  TPRAYER_TODAY, PRAYER_STATES, TPRAYER_MONTH, TPRAYER_STATS,
  TDHIKR, TADHKAR, TQURAN_LAST, TQURAN_SURAHS, TQURAN_VERSE,
  TSLEEP, SLEEP_COL, TSPORT_TYPES, TSPORT_GOALS, TSPORT_DURATIONS, TSPORT_DEFAULT,
});
