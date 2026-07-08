// Zukko Dev — Shared data + utilities for the UI kits.
// Single source of truth for course catalog, modules, lessons.

const ZK_COLORS = {
  bg0: '#07090D', bg1: '#0B0F17', bg2: '#11161F', bg3: '#161C28', bg4: '#1F2633',
  line1: '#1B2230', line2: '#232C3D', line3: '#2E394F',
  fg1: '#ECEFF5', fg2: '#B8C0CE', fg3: '#7E8799', fg4: '#525A6B',
  brand: '#3D7BFF', brandHover: '#5891FF', brandPress: '#2E68E6', brandSoft: '#1A2A4D',
  paid: '#C9A24E', paidSoft: '#2A2316',
  free: '#2BB673', freeSoft: '#14301F',
  warning: '#E0A93B', danger: '#E5484D', info: '#4CC2FF',
};

const ZK_FONT = "Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif";
const ZK_MONO = "'JetBrains Mono', 'SF Mono', ui-monospace, Menlo, monospace";

// Course cover — abstract gradient generated from a seed.
function CourseCover({ seed = 0, title = '', size = 'md' }) {
  const palettes = [
    ['#1E3A8A', '#3D7BFF', '#0B1936'],   // blue
    ['#0F3D2E', '#2BB673', '#06241A'],   // green
    ['#3D1E5C', '#7C5CFF', '#1A0E2E'],   // purple
    ['#5C2E1E', '#E07B3B', '#2E1208'],   // copper
    ['#1E3A4E', '#4CC2FF', '#0B1822'],   // cyan
    ['#3A1E2E', '#E5486B', '#1F0A14'],   // rose
    ['#3A3520', '#C9A24E', '#1F1D10'],   // gold
    ['#1F2937', '#6B7280', '#0F1419'],   // slate
  ];
  const p = palettes[seed % palettes.length];
  const angles = [125, 145, 165, 110, 200, 235];
  const angle = angles[seed % angles.length];
  return (
    <div style={{
      position: 'relative',
      width: '100%', height: '100%',
      borderRadius: 8,
      background: `linear-gradient(${angle}deg, ${p[0]} 0%, ${p[1]} 60%, ${p[2]} 100%)`,
      overflow: 'hidden',
      flexShrink: 0,
    }}>
      {/* Code bracket motif as subtle texture */}
      <div style={{
        position: 'absolute', inset: 0,
        opacity: 0.18,
        backgroundImage: `repeating-linear-gradient(${angle - 90}deg, transparent 0 14px, rgba(255,255,255,0.08) 14px 15px)`,
      }} />
      <div style={{
        position: 'absolute',
        right: -30, bottom: -30,
        fontFamily: ZK_MONO, fontSize: size === 'lg' ? 220 : 120, fontWeight: 700,
        color: 'rgba(255,255,255,0.06)',
        lineHeight: 1, letterSpacing: '-0.05em',
      }}>{seed % 2 ? '</>' : '{}'}</div>
      {title && size !== 'sm' && (
        <div style={{
          position: 'absolute', left: 14, top: 12, right: 14,
          fontFamily: ZK_FONT, fontSize: size === 'lg' ? 22 : 13,
          fontWeight: 600, color: 'rgba(255,255,255,0.95)',
          letterSpacing: '-0.01em', lineHeight: 1.2,
        }}>{title}</div>
      )}
    </div>
  );
}

const ZK_COURSES = [
  { id: 'ts-fund', title: 'TypeScript fundamentals', author: 'Karina Petrova', level: 'Beginner', duration: '8h 20m', lessons: 42, modules: 6, price: 0, free: true, seed: 0, tag: 'Web', enrolled: 12480, rating: 4.8 },
  { id: 'rsc-prod', title: 'React Server Components in production', author: 'David Kim', level: 'Advanced', duration: '6h 10m', lessons: 28, modules: 5, price: 149, seed: 2, tag: 'Web', enrolled: 3210, rating: 4.9 },
  { id: 'go-rest', title: 'Designing REST APIs with Go', author: 'Mei Tanaka', level: 'Intermediate', duration: '11h 45m', lessons: 56, modules: 8, price: 199, seed: 1, tag: 'Backend', enrolled: 5420, rating: 4.7 },
  { id: 'rust-1', title: 'Building with Rust — month one', author: 'Tomás Reyes', level: 'Beginner', duration: '14h 00m', lessons: 64, modules: 9, price: 249, seed: 3, tag: 'Systems', enrolled: 2810, rating: 4.9 },
  { id: 'pg-app', title: 'Postgres for application developers', author: 'Idris Hassan', level: 'Intermediate', duration: '9h 30m', lessons: 38, modules: 7, price: 179, seed: 4, tag: 'Backend', enrolled: 6120, rating: 4.8 },
  { id: 'sys-dist', title: 'Distributed systems crash course', author: 'Hannah Wei', level: 'Advanced', duration: '12h 15m', lessons: 48, modules: 10, price: 229, seed: 5, tag: 'Systems', enrolled: 1890, rating: 4.9 },
  { id: 'css-deep', title: 'Modern CSS deep dive', author: 'Júlia Almeida', level: 'Intermediate', duration: '7h 50m', lessons: 36, modules: 6, price: 0, free: true, seed: 6, tag: 'Web', enrolled: 18430, rating: 4.6 },
  { id: 'sw-arch', title: 'Software architecture for senior devs', author: 'Marco Rossi', level: 'Advanced', duration: '15h 20m', lessons: 52, modules: 12, price: 299, seed: 7, tag: 'Architecture', enrolled: 1240, rating: 4.9 },
  { id: 'k8s-prod', title: 'Kubernetes in production', author: 'Olu Adeyemi', level: 'Intermediate', duration: '10h 40m', lessons: 44, modules: 8, price: 199, seed: 0, tag: 'DevOps', enrolled: 4280, rating: 4.7 },
  { id: 'auth-zero', title: 'Auth from scratch', author: 'Sofia Lindgren', level: 'Intermediate', duration: '5h 30m', lessons: 22, modules: 4, price: 99, seed: 2, tag: 'Backend', enrolled: 7110, rating: 4.8 },
  { id: 'next-14', title: 'Next.js patterns for shipping teams', author: 'Karina Petrova', level: 'Intermediate', duration: '8h 00m', lessons: 34, modules: 6, price: 159, seed: 4, tag: 'Web', enrolled: 5980, rating: 4.7 },
  { id: 'algo-int', title: 'Algorithms for technical interviews', author: 'Wei Chen', level: 'Beginner', duration: '20h 10m', lessons: 78, modules: 12, price: 179, seed: 5, tag: 'Foundations', enrolled: 21340, rating: 4.8 },
];

// In-progress courses for "Continue learning" rail
const ZK_IN_PROGRESS = [
  { courseId: 'pg-app', moduleIdx: 2, lessonIdx: 4, progress: 0.34, lastWatched: '2 hours ago', currentLesson: 'Indexes — when to use which' },
  { courseId: 'go-rest', moduleIdx: 4, lessonIdx: 1, progress: 0.58, lastWatched: 'yesterday', currentLesson: 'Pagination at scale' },
  { courseId: 'ts-fund', moduleIdx: 5, lessonIdx: 2, progress: 0.81, lastWatched: '3 days ago', currentLesson: 'Mapped types in practice' },
];

// Sample modules for one course (Postgres for application developers)
const ZK_SAMPLE_MODULES = [
  { idx: 1, title: 'Foundations', lessons: [
    { idx: 1, title: 'Why Postgres', duration: '8:42', completed: true, free: true },
    { idx: 2, title: 'Installing locally', duration: '6:15', completed: true },
    { idx: 3, title: 'psql, the survival kit', duration: '12:08', completed: true },
  ]},
  { idx: 2, title: 'Schema design', lessons: [
    { idx: 1, title: 'Tables, types, constraints', duration: '14:22', completed: true },
    { idx: 2, title: 'Normalization, briefly', duration: '9:50', completed: true },
    { idx: 3, title: 'Foreign keys done right', duration: '11:36', completed: true },
    { idx: 4, title: 'Indexes — when to use which', duration: '18:04', completed: false, current: true, progress: 0.34 },
    { idx: 5, title: 'Migrations without tears', duration: '15:48', completed: false },
  ]},
  { idx: 3, title: 'Query performance', lessons: [
    { idx: 1, title: 'EXPLAIN ANALYZE', duration: '20:11', completed: false, locked: false },
    { idx: 2, title: 'CTEs and lateral joins', duration: '16:29', completed: false, locked: false },
    { idx: 3, title: 'Window functions', duration: '13:15', completed: false, locked: false, homework: true },
  ]},
  { idx: 4, title: 'Transactions & concurrency', lessons: [
    { idx: 1, title: 'Isolation levels', duration: '17:55', completed: false },
    { idx: 2, title: 'Avoiding deadlocks', duration: '12:44', completed: false },
  ]},
  { idx: 5, title: 'Operating Postgres', lessons: [
    { idx: 1, title: 'Backups & restore', duration: '14:00', completed: false, locked: true },
    { idx: 2, title: 'Replication basics', duration: '19:22', completed: false, locked: true },
  ]},
];

const ZK_SAMPLE_CODE = `-- Lesson 04 · Indexes — when to use which
-- Find users with the most posts in the last 30 days

SELECT u.id, u.username, COUNT(p.id) AS post_count
FROM   users u
JOIN   posts p ON p.author_id = u.id
WHERE  p.created_at > now() - interval '30 days'
GROUP  BY u.id, u.username
ORDER  BY post_count DESC
LIMIT  20;

-- Without an index on posts(author_id, created_at):
-- Seq Scan on posts (cost=0..184211) -- ~2.4s
-- With the index:
-- Index Scan using posts_author_created_idx (cost=0..1284) -- ~14ms`;

// Lucide icon helper — renders an SVG from path data
const ZK_ICONS = {
  home: 'M3 9l9-7 9 7v11a2 2 0 0 1-2 2h-4a1 1 0 0 1-1-1v-6h-4v6a1 1 0 0 1-1 1H5a2 2 0 0 1-2-2z',
  search: 'M21 21l-4.3-4.3 M11 19a8 8 0 1 1 0-16 8 8 0 0 1 0 16z',
  library: 'M3 3v18 M8 3v18 M3 8h5 M3 13h5 M3 18h5 M13 3h6a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-6z',
  inbox: 'M22 12h-6l-2 3h-4l-2-3H2 M5.5 5h13l3.5 7v6a2 2 0 0 1-2 2h-16a2 2 0 0 1-2-2v-6z',
  calendar: 'M8 2v4 M16 2v4 M3 10h18 M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-14a2 2 0 0 1-2-2v-14a2 2 0 0 1 2-2z',
  flame: 'M8.5 14.5A2.5 2.5 0 0 0 11 17a7 7 0 0 0 7-7c0-2-1-3.9-3-5.5s-3.5-4-4-6.5c-.5 2.5-2 4.9-4 6.5C5 6.1 4 8 4 10a8 8 0 0 0 8 8',
  bookmark: 'M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z',
  settings: 'M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2 M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z',
  play: 'M6 3l14 9-14 9z',
  pause: 'M6 4h4v16H6z M14 4h4v16h-4z',
  lock: 'M19 11H5a2 2 0 0 0-2 2v7a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7a2 2 0 0 0-2-2z M7 11V7a5 5 0 0 1 10 0v4',
  check: 'M20 6L9 17l-5-5',
  bookOpen: 'M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z',
  upload: 'M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4 M17 8l-5-5-5 5 M12 3v12',
  download: 'M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4 M7 10l5 5 5-5 M12 15V3',
  fileCode: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z M14 2v6h6 M10 13l-2 2 2 2 M14 13l2 2-2 2',
  message: 'M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z',
  chevronRight: 'M9 18l6-6-6-6',
  chevronLeft: 'M15 18l-6-6 6-6',
  chevronDown: 'M6 9l6 6 6-6',
  chevronUp: 'M18 15l-6-6-6 6',
  bell: 'M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9 M13.7 21a2 2 0 0 1-3.4 0',
  star: 'M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01z',
  user: 'M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2 M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z',
  arrowRight: 'M5 12h14 M12 5l7 7-7 7',
  clock: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z M12 6v6l4 2',
  list: 'M8 6h13 M8 12h13 M8 18h13 M3 6h.01 M3 12h.01 M3 18h.01',
  video: 'M23 7l-7 5 7 5z M14 5H3a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h11a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2z',
  brain: 'M12 5a3 3 0 1 0-5.9-1c-2.3.4-4 2.3-4 4.6 0 1.3.6 2.5 1.6 3.3-.4.5-.6 1.2-.6 1.9 0 1.5 1 2.8 2.4 3.2.2 2 1.9 3.5 4 3.5 1.4 0 2.6-.7 3.3-1.7m.2-13.8a3 3 0 1 1 5.9-1c2.3.4 4 2.3 4 4.6 0 1.3-.6 2.5-1.6 3.3.4.5.6 1.2.6 1.9 0 1.5-1 2.8-2.4 3.2-.2 2-1.9 3.5-4 3.5-1.4 0-2.6-.7-3.3-1.7 M12 5v15',
  trophy: 'M6 9H4.5a2.5 2.5 0 0 1 0-5H6 M18 9h1.5a2.5 2.5 0 0 0 0-5H18 M4 22h16 M10 14.66V17c0 .55-.47.98-.97 1.21C7.85 18.75 7 20.24 7 22 M14 14.66V17c0 .55.47.98.97 1.21C16.15 18.75 17 20.24 17 22 M18 2H6v7a6 6 0 0 0 12 0z',
  zap: 'M13 2L3 14h9l-1 8 10-12h-9z',
  plus: 'M12 5v14 M5 12h14',
  filter: 'M22 3H2l8 9.46V19l4 2v-8.54z',
  more: 'M12 13a1 1 0 1 0 0-2 1 1 0 0 0 0 2 M19 13a1 1 0 1 0 0-2 1 1 0 0 0 0 2 M5 13a1 1 0 1 0 0-2 1 1 0 0 0 0 2',
  trending: 'M23 6l-9.5 9.5-5-5L1 18 M17 6h6v6',
  mic: 'M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3z M19 10v2a7 7 0 0 1-14 0v-2 M12 19v3',
  send: 'M22 2L11 13 M22 2l-7 20-4-9-9-4z',
  paperclip: 'M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48',
  x: 'M18 6L6 18 M6 6l12 12',
  menu: 'M3 6h18 M3 12h18 M3 18h18',
  gripV: 'M9 5h.01 M9 12h.01 M9 19h.01 M15 5h.01 M15 12h.01 M15 19h.01',
  bracket: 'M8 3l-5 9 5 9 M16 3l5 9-5 9',
};

function Icon({ name, size = 20, color = 'currentColor', strokeWidth = 1.5, style = {} }) {
  const d = ZK_ICONS[name];
  if (!d) return null;
  const fillBased = ['flame', 'star', 'play', 'pause'].includes(name) && false;
  return (
    <svg
      width={size} height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke={color}
      strokeWidth={strokeWidth}
      strokeLinecap="round"
      strokeLinejoin="round"
      style={{ flexShrink: 0, ...style }}
    >
      {d.split(' M').map((part, i) => (
        <path key={i} d={(i === 0 ? '' : 'M') + part} />
      ))}
    </svg>
  );
}

// Avatars — colored initials
function Avatar({ name = '', size = 28, seed = 0 }) {
  const colors = ['#3D7BFF','#2BB673','#C9A24E','#7C5CFF','#E07B3B','#4CC2FF','#E5486B'];
  const c = colors[(seed || name.charCodeAt(0)) % colors.length];
  const initials = name.split(' ').map(s => s[0]).join('').slice(0, 2).toUpperCase();
  return (
    <div style={{
      width: size, height: size, borderRadius: '50%',
      background: c, color: 'white',
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      fontFamily: ZK_FONT, fontSize: size * 0.4, fontWeight: 600,
      flexShrink: 0,
    }}>{initials}</div>
  );
}

// Format helpers
function fmtPrice(p, free) { return free ? 'Free' : `$${p}`; }
function fmtCount(n) { return n >= 1000 ? `${(n/1000).toFixed(n>=10000?0:1)}k` : `${n}`; }

Object.assign(window, {
  ZK_COLORS, ZK_FONT, ZK_MONO, ZK_COURSES, ZK_IN_PROGRESS, ZK_SAMPLE_MODULES, ZK_SAMPLE_CODE,
  CourseCover, Icon, Avatar, fmtPrice, fmtCount,
});
