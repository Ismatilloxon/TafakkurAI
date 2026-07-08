// Tafakkur AI — UI kit: theme, icons, iOS chrome, nav, AI companion, primitives.
// Tafakkur "Fajr" Design System — own identity, Apple HIG, calm & spiritual.

// ── Themes (Fajr design system — switchable palettes) ───────────────────────
const _dkShadow = 'inset 0 1px 0 rgba(255,255,255,0.04), 0 12px 30px -22px rgba(0,0,0,0.85)';
const _dkHair = 'rgba(255,255,255,0.07)';
const _ltShadow = 'inset 0 1px 0 rgba(255,255,255,0.65), 0 10px 26px -20px rgba(60,48,30,0.42)';
const _ltHair = 'rgba(60,50,35,0.10)';

const THEMES = {
  sukut: {
    fg:'#F4F6FB', fg2:'rgba(225,230,245,0.64)', fg3:'rgba(225,230,245,0.36)', fg4:'rgba(225,230,245,0.18)',
    bg0:'#07090F', bg1:'#0B0E16', card:'#141826', card2:'#1A2032', card3:'#232A3D',
    sep:'rgba(150,160,200,0.16)', line:'#2A3146', hair:_dkHair, glass:'rgba(11,14,22,0.8)', cardShadow:_dkShadow,
    green:'#9FD0B0', gold:'#E6CCA0', cyan:'#8AA0E8', purple:'#B98AE0', rose:'#E08AA8', warn:'#E0B98A', danger:'#E5604D', teal:'#8AA0E8',
  },
  fajr: {
    fg:'#FBFAF7', fg2:'rgba(243,239,231,0.64)', fg3:'rgba(243,239,231,0.36)', fg4:'rgba(243,239,231,0.18)',
    bg0:'#080A0A', bg1:'#0D0F11', card:'#16181C', card2:'#1B1E22', card3:'#23262B',
    sep:'rgba(150,146,134,0.16)', line:'#2A2D32', hair:_dkHair, glass:'rgba(13,15,17,0.8)', cardShadow:_dkShadow,
    green:'#7FB85C', gold:'#D4A24A', cyan:'#46B4E8', purple:'#8E79F0', rose:'#E0607F', warn:'#E0A93B', danger:'#E5604D', teal:'#13A88C',
  },
  maghrib: {
    fg:'#FBF4EC', fg2:'rgba(247,235,222,0.64)', fg3:'rgba(247,235,222,0.36)', fg4:'rgba(247,235,222,0.18)',
    bg0:'#100C09', bg1:'#14100D', card:'#1E1712', card2:'#241B14', card3:'#2E241B',
    sep:'rgba(190,150,120,0.16)', line:'#34281E', hair:_dkHair, glass:'rgba(20,16,13,0.8)', cardShadow:_dkShadow,
    green:'#B6B45E', gold:'#E5B452', cyan:'#D98B6B', purple:'#C28BA0', rose:'#C25B5B', warn:'#E5B452', danger:'#E5604D', teal:'#E0794B',
  },
  zumurrad: {
    fg:'#F2F8F4', fg2:'rgba(225,240,230,0.62)', fg3:'rgba(225,240,230,0.34)', fg4:'rgba(225,240,230,0.18)',
    bg0:'#060F0B', bg1:'#08120E', card:'#0F1C16', card2:'#14241B', card3:'#1C2E24',
    sep:'rgba(120,170,140,0.16)', line:'#1E3228', hair:_dkHair, glass:'rgba(8,18,14,0.8)', cardShadow:_dkShadow,
    green:'#7FB85C', gold:'#E5C572', cyan:'#5FC2C0', purple:'#9AD46A', rose:'#E08AA8', warn:'#E5C572', danger:'#E5604D', teal:'#1FB874',
  },
  sama: {
    fg:'#F0F3F8', fg2:'rgba(222,230,245,0.62)', fg3:'rgba(222,230,245,0.34)', fg4:'rgba(222,230,245,0.18)',
    bg0:'#0A0E18', bg1:'#0F1320', card:'#19202F', card2:'#1E2638', card3:'#283044',
    sep:'rgba(150,165,200,0.16)', line:'#2A3346', hair:_dkHair, glass:'rgba(15,19,32,0.8)', cardShadow:_dkShadow,
    green:'#9ED0A0', gold:'#E9C7A0', cyan:'#7FC8C0', purple:'#A6A0E0', rose:'#E0BE9A', warn:'#E9C7A0', danger:'#E5604D', teal:'#7FC8C0',
  },
  noor: {
    fg:'#2A2620', fg2:'rgba(42,38,32,0.66)', fg3:'rgba(42,38,32,0.40)', fg4:'rgba(42,38,32,0.20)',
    bg0:'#EDE6D7', bg1:'#F4EEE2', card:'#FFFFFF', card2:'#FBF6EC', card3:'#F2EAD9',
    sep:'rgba(60,50,35,0.14)', line:'rgba(60,50,35,0.20)', hair:_ltHair, glass:'rgba(244,238,226,0.82)', cardShadow:_ltShadow,
    green:'#5E924B', gold:'#B0832C', cyan:'#2E80A8', purple:'#7B5EA8', rose:'#C25B7A', warn:'#B0832C', danger:'#C0392B', teal:'#1F7A5C',
  },
  sahifa: {
    fg:'#1B2430', fg2:'rgba(27,36,48,0.64)', fg3:'rgba(27,36,48,0.40)', fg4:'rgba(27,36,48,0.20)',
    bg0:'#E7ECF1', bg1:'#EEF2F6', card:'#FFFFFF', card2:'#F6F9FC', card3:'#EAF0F5',
    sep:'rgba(30,45,65,0.12)', line:'rgba(30,45,65,0.18)', hair:_ltHair, glass:'rgba(238,242,246,0.82)', cardShadow:_ltShadow,
    green:'#4E9A6B', gold:'#B0832C', cyan:'#2E80C8', purple:'#6E5EC8', rose:'#C25B7A', warn:'#C08A3A', danger:'#C0392B', teal:'#128A78',
  },
};
const THEME_META = [
  { key:'sukut',    name:'Sukut',    concept:'Moonlit night',  mode:'dark' },
  { key:'fajr',     name:'Fajr',     concept:'Warm dawn',      mode:'dark' },
  { key:'maghrib',  name:'Maghrib',  concept:'Sunset earth',   mode:'dark' },
  { key:'zumurrad', name:'Zumurrad', concept:'Emerald luxe',   mode:'dark' },
  { key:'sama',     name:'Sama',     concept:'Soft sky',       mode:'dark' },
  { key:'noor',     name:'Noor',     concept:'Printed mushaf', mode:'light' },
  { key:'sahifa',   name:'Sahifa',   concept:'Cool paper',     mode:'light' },
];
const C = {};
const FONT = "-apple-system, 'SF Pro Text', 'Inter', BlinkMacSystemFont, sans-serif";
const DISPLAY = "-apple-system, 'SF Pro Display', 'Inter', BlinkMacSystemFont, sans-serif";
const MONO = "'SF Mono', ui-monospace, 'JetBrains Mono', Menlo, monospace";
const SERIF = "'New York', 'Instrument Serif', 'Iowan Old Style', Georgia, serif";

const NAMED = {};
function named(n) { return NAMED[n] || NAMED.teal; }
function applyTheme(key) {
  const t = THEMES[key] || THEMES.sukut;
  Object.assign(C, t);
  NAMED.teal = NAMED.blue = C.teal;
  NAMED.gold = C.gold; NAMED.green = C.green; NAMED.cyan = C.cyan; NAMED.purple = C.purple; NAMED.rose = C.rose;
}
applyTheme('sukut');
function softOf(hex, a = 0.16) {
  const h = hex.replace('#',''); const r = parseInt(h.slice(0,2),16), g = parseInt(h.slice(2,4),16), b = parseInt(h.slice(4,6),16);
  return `rgba(${r},${g},${b},${a})`;
}

// ── App context ──────────────────────────────────────────────────────────────
const AppCtx = React.createContext(null);
function useApp() { return React.useContext(AppCtx); }

// ── Icons (Lucide-style stroke paths; component splits on ' M') ──────────────
const ICONS = {
  // nav / core
  home:'M3 9l9-7 9 7v11a2 2 0 0 1-2 2h-4a1 1 0 0 1-1-1v-6h-4v6a1 1 0 0 1-1 1H5a2 2 0 0 1-2-2z',
  sparkles:'M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9z M19 3v4 M21 5h-4 M5 16v3 M6.5 17.5h-3',
  brain:'M12 5a3 3 0 1 0-5.9-1c-2.3.4-4 2.3-4 4.6 0 1.3.6 2.5 1.6 3.3-.4.5-.6 1.2-.6 1.9 0 1.5 1 2.8 2.4 3.2.2 2 1.9 3.5 4 3.5 1.4 0 2.6-.7 3.3-1.7m.2-13.8a3 3 0 1 1 5.9-1c2.3.4 4 2.3 4 4.6 0 1.3-.6 2.5-1.6 3.3.4.5.6 1.2.6 1.9 0 1.5-1 2.8-2.4 3.2-.2 2-1.9 3.5-4 3.5-1.4 0-2.6-.7-3.3-1.7 M12 5v15',
  book:'M4 19.5A2.5 2.5 0 0 1 6.5 17H20 M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z',
  bookOpen:'M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z',
  moon:'M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z',
  layoutGrid:'M3 3h7v7H3z M14 3h7v7h-7z M14 14h7v7h-7z M3 14h7v7H3z',
  checkSquare:'M9 11l3 3L22 4 M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11',
  briefcase:'M20 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2z M16 7V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v2',
  users:'M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2 M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M23 21v-2a4 4 0 0 0-3-3.9 M16 3.1a4 4 0 0 1 0 7.8',
  wallet:'M20 7H4a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2z M16 13h.01 M2 9V7a2 2 0 0 1 2-2h13',
  // signals / misc
  sun:'M12 17a5 5 0 1 0 0-10 5 5 0 0 0 0 10z M12 1v2 M12 21v2 M4.2 4.2l1.4 1.4 M18.4 18.4l1.4 1.4 M1 12h2 M21 12h2 M4.2 19.8l1.4-1.4 M18.4 5.6l1.4-1.4',
  sunrise:'M12 2v6 M4.2 10.2l1.4 1.4 M1 18h2 M21 18h2 M18.4 11.6l1.4-1.4 M23 22H1 M8 6l4-4 4 4 M16 18a4 4 0 0 0-8 0',
  sunset:'M17 18a5 5 0 0 0-10 0 M12 9V2 M4.2 10.2l1.4 1.4 M1 18h2 M21 18h2 M18.4 11.6l1.4-1.4 M23 22H1 M8 5l4 4 4-4',
  target:'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z M12 18a6 6 0 1 0 0-12 6 6 0 0 0 0 12z M12 14a2 2 0 1 0 0-4 2 2 0 0 0 0 4z',
  activity:'M22 12h-4l-3 9L9 3l-3 9H2',
  code:'M16 18l6-6-6-6 M8 6l-6 6 6 6',
  terminal:'M4 17l6-6-6-6 M12 19h8',
  languages:'M5 8l6 6 M4 14l6-6 2-3 M2 5h12 M7 2h1 M22 22l-5-10-5 10 M14 18h6',
  sigma:'M18 7V5a1 1 0 0 0-1-1H7l6 8-6 8h10a1 1 0 0 0 1-1v-2',
  lineChart:'M3 3v18h18 M19 9l-5 5-4-4-3 3',
  heart:'M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1-1.1a5.5 5.5 0 0 0-7.8 7.8l1 1.1L12 21l7.8-7.6 1-1a5.5 5.5 0 0 0 0-7.8z',
  leaf:'M11 20A7 7 0 0 1 4 13c0-6 7-11 16-11 0 9-5 16-11 16z M4 21c3-3 6-5 9-6',
  server:'M20 4H4a2 2 0 0 0-2 2v3a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2z M20 13H4a2 2 0 0 0-2 2v3a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-3a2 2 0 0 0-2-2z M6 8h.01 M6 17h.01',
  graduationCap:'M22 10L12 5 2 10l10 5 10-5z M6 12v5c0 1.5 3 3 6 3s6-1.5 6-3v-5 M22 10v6',
  fileText:'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z M14 2v6h6 M9 13h6 M9 17h6',
  penLine:'M12 20h9 M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z',
  smile:'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z M8 14s1.5 2 4 2 4-2 4-2 M9 9h.01 M15 9h.01',
  coffee:'M17 8h1a4 4 0 0 1 0 8h-1 M3 8h14v9a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4z M6 2v2 M10 2v2 M14 2v2',
  repeat:'M17 2l4 4-4 4 M3 11V9a4 4 0 0 1 4-4h14 M7 22l-4-4 4-4 M21 13v2a4 4 0 0 1-4 4H3',
  hash:'M4 9h16 M4 15h16 M10 3L8 21 M16 3l-2 18',
  messageCircle:'M21 11.5a8.4 8.4 0 0 1-9 8.4 8.4 8.4 0 0 1-3.8-.9L3 21l1.9-5.2A8.4 8.4 0 0 1 12 3a8.4 8.4 0 0 1 9 8.5z',
  thumbsUp:'M7 22V11 M2 13v7a2 2 0 0 0 2 2h13.3a2 2 0 0 0 2-1.7l1.4-9A2 2 0 0 0 17.7 9H13l1-5a2 2 0 0 0-4-1l-3 6',
  trendingUp:'M23 6l-9.5 9.5-5-5L1 18 M17 6h6v6',
  trendingDown:'M23 18l-9.5-9.5-5 5L1 6 M17 18h6v-6',
  arrowUpRight:'M7 17L17 7 M7 7h10v10',
  arrowRight:'M5 12h14 M12 5l7 7-7 7',
  sliders:'M4 21v-7 M4 10V3 M12 21v-9 M12 8V3 M20 21v-5 M20 12V3 M1 14h6 M9 8h6 M17 16h6',
  circle:'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z',
  checkCircle:'M22 11.1V12a10 10 0 1 1-5.9-9.1 M22 4L12 14.01l-3-3',
  // generic ui
  check:'M20 6L9 17l-5-5', x:'M18 6L6 18 M6 6l12 12', plus:'M12 5v14 M5 12h14',
  chevronRight:'M9 18l6-6-6-6', chevronLeft:'M15 18l-6-6 6-6', chevronDown:'M6 9l6 6 6-6', chevronUp:'M18 15l-6-6-6 6',
  bell:'M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9 M13.7 21a2 2 0 0 1-3.4 0',
  flame:'M8.5 14.5A2.5 2.5 0 0 0 11 17a7 7 0 0 0 7-7c0-2-1-3.9-3-5.5s-3.5-4-4-6.5c-.5 2.5-2 4.9-4 6.5C5 6.1 4 8 4 10a8 8 0 0 0 8 8',
  settings:'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z M19.4 15a1.6 1.6 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.6 1.6 0 0 0-2.7 1.1V21a2 2 0 0 1-4 0v-.1A1.6 1.6 0 0 0 6 19.4a1.6 1.6 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.6 1.6 0 0 0-1.1-2.7H0a2 2 0 0 1 0-4h.1A1.6 1.6 0 0 0 1.6 6 M19.4 9a1.6 1.6 0 0 0 1.1-1.5',
  clock:'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z M12 6v6l4 2',
  star:'M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01z',
  user:'M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2 M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z',
  play:'M6 3l14 9-14 9z', pause:'M6 4h4v16H6z M14 4h4v16h-4z',
  mic:'M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3z M19 10v2a7 7 0 0 1-14 0v-2 M12 19v3',
  send:'M22 2L11 13 M22 2l-7 20-4-9-9-4z', search:'M21 21l-4.3-4.3 M11 19a8 8 0 1 1 0-16 8 8 0 0 1 0 16z',
  list:'M8 6h13 M8 12h13 M8 18h13 M3 6h.01 M3 12h.01 M3 18h.01',
  lock:'M19 11H5a2 2 0 0 0-2 2v7a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7a2 2 0 0 0-2-2z M7 11V7a5 5 0 0 1 10 0v4',
  more:'M12 13a1 1 0 1 0 0-2 1 1 0 0 0 0 2 M19 13a1 1 0 1 0 0-2 1 1 0 0 0 0 2 M5 13a1 1 0 1 0 0-2 1 1 0 0 0 0 2',
  download:'M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4 M7 10l5 5 5-5 M12 15V3',
  bookmark:'M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z',
  calendar:'M8 2v4 M16 2v4 M3 10h18 M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-14a2 2 0 0 1-2-2v-14a2 2 0 0 1 2-2z',
  zap:'M13 2L3 14h9l-1 8 10-12h-9z',
  dumbbell:'M4 9v6 M7 7.5v9 M17 7.5v9 M20 9v6 M7 12h10',
  moonStars:'M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z M19 3v4 M21 5h-4',
  bedDouble:'M2 20v-8a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v8 M2 16h20 M6 10V7a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v3',
};
function TIcon({ name, size = 20, color = 'currentColor', sw = 1.6, style = {} }) {
  const d = ICONS[name]; if (!d) return null;
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
      strokeWidth={sw} strokeLinecap="round" strokeLinejoin="round" style={{ flexShrink:0, ...style }}>
      {d.split(' M').map((p,i) => <path key={i} d={(i===0?'':'M')+p}/>)}
    </svg>
  );
}

// ── iOS chrome ───────────────────────────────────────────────────────────────
function TStatusBar({ time = '9:41' }) {
  return (
    <div style={{ height:54, padding:'21px 32px 0', display:'flex', alignItems:'flex-start', justifyContent:'space-between', position:'absolute', top:0, left:0, right:0, zIndex:30, pointerEvents:'none' }}>
      <span style={{ fontFamily:FONT, fontSize:16, fontWeight:600, color:C.fg }}>{time}</span>
      <div style={{ display:'flex', alignItems:'center', gap:7 }}>
        <svg width="18" height="11" viewBox="0 0 18 11"><rect x="0" y="7" width="3" height="4" rx="0.5" fill={C.fg}/><rect x="5" y="5" width="3" height="6" rx="0.5" fill={C.fg}/><rect x="10" y="2.5" width="3" height="8.5" rx="0.5" fill={C.fg}/><rect x="15" y="0" width="3" height="11" rx="0.5" fill={C.fg}/></svg>
        <svg width="16" height="11" viewBox="0 0 16 11"><path d="M8 3C10 3 11.5 3.7 12.7 4.8L13.7 3.7C12.2 2.2 10.2 1.3 8 1.3C5.8 1.3 3.8 2.2 2.3 3.7L3.3 4.8C4.5 3.7 6 3 8 3Z" fill={C.fg}/><circle cx="8" cy="9" r="1.3" fill={C.fg}/></svg>
        <svg width="25" height="12" viewBox="0 0 25 12"><rect x="0.5" y="0.5" width="21" height="11" rx="3" stroke={C.fg} strokeOpacity="0.4" fill="none"/><rect x="2" y="2" width="18" height="8" rx="1.5" fill={C.fg}/></svg>
      </div>
    </div>
  );
}
function Notch() {
  return <div style={{ position:'absolute', top:11, left:'50%', transform:'translateX(-50%)', width:124, height:36, background:'#000', borderRadius:24, zIndex:40 }}/>;
}
function HomeIndicator() {
  return <div style={{ position:'absolute', bottom:8, left:'50%', transform:'translateX(-50%)', width:134, height:5, borderRadius:100, background:'rgba(255,255,255,0.32)', zIndex:60, pointerEvents:'none' }}/>;
}

// ── Tab bar (tabs nav mode) ──────────────────────────────────────────────────
const TABS = [
  ['today','sparkles','Today'],
  ['learn','bookOpen','Learn'],
  ['do','checkSquare','Do'],
  ['thoughts','messageCircle','Thoughts'],
  ['more','layoutGrid','More'],
];
function TTabBar({ active, onChange, accent }) {
  return (
    <div style={{ position:'absolute', left:0, right:0, bottom:0, paddingBottom:22, paddingTop:9, background:C.glass, backdropFilter:'blur(30px) saturate(180%)', WebkitBackdropFilter:'blur(30px) saturate(180%)', borderTop:`0.5px solid ${C.sep}`, display:'flex', zIndex:25 }}>
      {TABS.map(([k,ic,l]) => {
        const on = active === k;
        return (
          <button key={k} onClick={()=>onChange(k)} style={{ flex:1, background:'none', border:'none', display:'flex', flexDirection:'column', alignItems:'center', gap:4, cursor:'pointer', padding:0 }}>
            <div style={{ position:'relative', width:52, height:32, display:'flex', alignItems:'center', justifyContent:'center' }}>
              <div className="tf-tab-ind" style={{ position:'absolute', inset:'1px 6px', borderRadius:11, background:on?softOf(accent,0.18):'transparent', transform:on?'scale(1)':'scale(0.55)', opacity:on?1:0 }}/>
              <TIcon name={ic} size={22} color={on?accent:C.fg3} sw={on?2:1.7} style={{ position:'relative', zIndex:1, transition:'color .2s ease' }}/>
            </div>
            <span style={{ fontFamily:FONT, fontSize:10, fontWeight:on?600:500, color:on?accent:C.fg3, transition:'color .2s ease' }}>{l}</span>
          </button>
        );
      })}
    </div>
  );
}

// ── Primitives ───────────────────────────────────────────────────────────────
function Card({ children, style = {}, onClick, pad = 16 }) {
  return (
    <div onClick={onClick} style={{ background:`linear-gradient(180deg, ${C.card2} 0%, ${C.card} 100%)`, border:`0.5px solid ${C.hair}`, borderRadius:16, padding:pad, cursor:onClick?'pointer':'default', boxShadow:C.cardShadow, ...style }}>{children}</div>
  );
}
function SectionTitle({ children, action, onAction, serif }) {
  const { serifHead } = useApp();
  return (
    <div style={{ display:'flex', alignItems:'baseline', justifyContent:'space-between', padding:'0 20px 12px' }}>
      <h2 style={{ fontFamily: serif?serifHead:DISPLAY, fontSize: serif?27:21, fontWeight: serif?400:700, color:C.fg, letterSpacing:'0.3px', margin:0 }}>{children}</h2>
      {action && <button onClick={onAction} style={{ background:'none', border:'none', fontFamily:FONT, fontSize:15, color:useApp().accent, cursor:'pointer', padding:0 }}>{action}</button>}
    </div>
  );
}
function Overline({ children, color }) {
  return <div style={{ fontFamily:MONO, fontSize:10, letterSpacing:'0.08em', textTransform:'uppercase', color:color||C.fg3, fontWeight:600 }}>{children}</div>;
}
function ProgressBar({ value, color, h = 5, track = 'rgba(120,130,150,0.22)' }) {
  return (
    <div style={{ flex:1, height:h, background:track, borderRadius:h, overflow:'hidden' }}>
      <div style={{ width:`${Math.round(value*100)}%`, height:'100%', background:color, borderRadius:h, transition:'width .4s cubic-bezier(0.16,1,0.3,1)' }}/>
    </div>
  );
}
function Pill({ children, color, soft, style = {} }) {
  return <span style={{ display:'inline-flex', alignItems:'center', gap:5, padding:'3px 9px', borderRadius:999, background:soft, color, fontFamily:MONO, fontSize:10.5, fontWeight:600, letterSpacing:'0.03em', textTransform:'uppercase', ...style }}>{children}</span>;
}
function IconBadge({ name, color, size = 40, isz = 20, soft }) {
  return (
    <div style={{ width:size, height:size, borderRadius:11, background:soft||softOf(color), display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
      <TIcon name={name} size={isz} color={color} sw={1.8}/>
    </div>
  );
}
function Segmented({ tabs, value, onChange }) {
  return (
    <div style={{ display:'flex', background:'rgba(120,130,150,0.16)', borderRadius:9, padding:2 }}>
      {tabs.map(([k,l]) => (
        <button key={k} onClick={()=>onChange(k)} style={{ flex:1, padding:'7px 0', textAlign:'center', borderRadius:7, border:'none', background: value===k?'#3A4150':'transparent', boxShadow:value===k?'0 1px 3px rgba(0,0,0,0.32), inset 0 0.5px 0 rgba(255,255,255,0.08)':'none', fontFamily:FONT, fontSize:13, fontWeight:value===k?600:500, color: value===k?C.fg:C.fg2, cursor:'pointer', transition:'color .2s ease' }}>{l}</button>
      ))}
    </div>
  );
}

// ── Proactive AI card (seeded across modules) ────────────────────────────────
function ProactiveCard({ text, action, onAction, compact }) {
  const { accent, accentSoft, proactivity } = useApp();
  if (proactivity === 'ambient' && compact) return null;
  return (
    <div style={{ position:'relative', borderRadius:16, padding:'14px 16px', background:`linear-gradient(135deg, ${accentSoft}, rgba(124,92,255,0.05))`, border:`0.5px solid ${accent}55`, display:'flex', gap:12, alignItems:'flex-start' }}>
      <div style={{ width:30, height:30, borderRadius:'50%', background:`linear-gradient(135deg, ${accent}, ${C.purple})`, display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0, boxShadow:`0 2px 10px ${accent}55` }}>
        <TIcon name="sparkles" size={16} color="#fff" sw={1.8}/>
      </div>
      <div style={{ flex:1, minWidth:0 }}>
        <div style={{ fontFamily:FONT, fontSize:13.5, color:C.fg, lineHeight:1.5 }}>{text}</div>
        {action && <button onClick={onAction} style={{ marginTop:9, background:accent, color:'#fff', border:'none', borderRadius:9, padding:'7px 12px', fontFamily:FONT, fontSize:12.5, fontWeight:600, cursor:'pointer', display:'inline-flex', alignItems:'center', gap:5 }}>{action}<TIcon name="arrowRight" size={12} color="#fff"/></button>}
      </div>
    </div>
  );
}

// ── Screen scaffolding ───────────────────────────────────────────────────────
function LargeTitle({ title, subtitle, right, serif }) {
  const { serifHead } = useApp();
  return (
    <div style={{ padding:'8px 20px 10px' }}>
      <div style={{ display:'flex', alignItems:'flex-start', justifyContent:'space-between' }}>
        <div>
          {subtitle && <div style={{ fontFamily:FONT, fontSize:14, color:C.fg2, marginBottom:3 }}>{subtitle}</div>}
          <h1 style={{ fontFamily: serif?serifHead:DISPLAY, fontSize: serif?40:33, fontWeight: serif?400:700, color:C.fg, letterSpacing: serif?'0':'0.36px', margin:0, lineHeight:1.05 }}>{title}</h1>
        </div>
        {right}
      </div>
    </div>
  );
}
function BackBar({ title, onBack, right }) {
  const { accent } = useApp();
  return (
    <div style={{ height:48, padding:'0 12px', display:'flex', alignItems:'center', justifyContent:'space-between', position:'sticky', top:0, zIndex:20, background:C.glass, backdropFilter:'blur(20px)', WebkitBackdropFilter:'blur(20px)', borderBottom:`0.5px solid ${C.sep}` }}>
      <button onClick={onBack} style={{ display:'flex', alignItems:'center', gap:2, color:accent, background:'none', border:'none', cursor:'pointer', padding:'6px 6px' }}>
        <TIcon name="chevronLeft" size={20} color={accent} sw={2.4}/>
        <span style={{ fontFamily:FONT, fontSize:16 }}>Back</span>
      </button>
      <span style={{ fontFamily:FONT, fontSize:16, fontWeight:600, color:C.fg }}>{title}</span>
      <div style={{ minWidth:64, display:'flex', justifyContent:'flex-end', paddingRight:6 }}>{right}</div>
    </div>
  );
}

// ── Hub launcher (hub nav mode) — springboard of all modules ─────────────────
const HUB_MODULES = [
  { k:'today', name:'Today', icon:'sparkles', color:'blue', sub:'Your AI brief' },
  { k:'coach', name:'Coach', icon:'brain', color:'purple', sub:'Murabbiy' },
  { k:'learn', name:'Learn', icon:'bookOpen', color:'cyan', sub:'4 subjects' },
  { k:'do', name:'Productivity', icon:'checkSquare', color:'blue', sub:'Tasks · focus' },
  { k:'thoughts', name:'Thoughts', icon:'messageCircle', color:'rose', sub:'From friends' },
  { k:'career', name:'Career', icon:'briefcase', color:'green', sub:'CV · interviews' },
  { k:'finance', name:'Finance', icon:'wallet', color:'gold', sub:'Portfolio' },
];
function HubLauncher({ onOpen }) {
  const { accent } = useApp();
  return (
    <div style={{ padding:'4px 16px 0' }}>
      <div style={{ display:'grid', gridTemplateColumns:'1fr 1fr', gap:12 }}>
        {HUB_MODULES.map(m => {
          const col = named(m.color);
          return (
            <button key={m.k} onClick={()=>onOpen(m.k)} style={{ textAlign:'left', background:C.card, border:`0.5px solid ${C.sep}`, borderRadius:18, padding:16, cursor:'pointer', display:'flex', flexDirection:'column', gap:14, minHeight:118 }}>
              <div style={{ width:46, height:46, borderRadius:13, background:softOf(col), display:'flex', alignItems:'center', justifyContent:'center' }}>
                <TIcon name={m.icon} size={24} color={col} sw={1.8}/>
              </div>
              <div>
                <div style={{ fontFamily:DISPLAY, fontSize:17, fontWeight:600, color:C.fg, letterSpacing:'0.2px' }}>{m.name}</div>
                <div style={{ fontFamily:FONT, fontSize:12.5, color:C.fg2, marginTop:2 }}>{m.sub}</div>
              </div>
            </button>
          );
        })}
      </div>
    </div>
  );
}

// ── AI Companion: floating button + chat sheet ───────────────────────────────
function CompanionFab({ onClick, raised }) {
  const { accent } = useApp();
  return (
    <button onClick={onClick} aria-label="Open coach" style={{ position:'absolute', right:18, bottom: raised?92:30, width:58, height:58, borderRadius:'50%', border:'none', cursor:'pointer', zIndex:36, background:`linear-gradient(135deg, ${accent}, ${C.purple})`, boxShadow:`0 8px 24px ${accent}66, 0 2px 6px rgba(0,0,0,0.4)`, display:'flex', alignItems:'center', justifyContent:'center' }}>
      <TIcon name="sparkles" size={26} color="#fff" sw={1.8}/>
    </button>
  );
}

function CompanionSheet({ open, onClose, onFull }) {
  const { accent } = useApp();
  const [msgs, setMsgs] = React.useState(TCHAT);
  const [draft, setDraft] = React.useState('');
  const scroller = React.useRef(null);
  React.useEffect(() => { if (scroller.current) scroller.current.scrollTop = scroller.current.scrollHeight; }, [msgs, open]);
  const send = (txt) => {
    const t = (txt ?? draft).trim(); if (!t) return;
    setMsgs(m => [...m, { from:'me', text:t, time:'now' }]); setDraft('');
    setTimeout(() => setMsgs(m => [...m, { from:'ai', text: aiReply(t), time:'now' }]), 650);
  };
  return (
    <div style={{ position:'absolute', inset:0, zIndex:50, pointerEvents: open?'auto':'none' }}>
      <div onClick={onClose} style={{ position:'absolute', inset:0, background:'rgba(7,9,13,0.55)', backdropFilter:'blur(2px)', opacity:open?1:0, transition:'opacity .28s' }}/>
      <div style={{ position:'absolute', left:0, right:0, bottom:0, height:'74%', background:C.bg1, borderRadius:'22px 22px 0 0', borderTop:`0.5px solid ${C.line}`, transform:`translateY(${open?'0':'100%'})`, transition:'transform .34s cubic-bezier(0.16,1,0.3,1)', display:'flex', flexDirection:'column', overflow:'hidden' }}>
        <div style={{ padding:'10px 16px 8px', display:'flex', alignItems:'center', gap:11, borderBottom:`0.5px solid ${C.sep}` }}>
          <div style={{ width:36, height:36, borderRadius:'50%', background:`linear-gradient(135deg, ${accent}, ${C.purple})`, display:'flex', alignItems:'center', justifyContent:'center', boxShadow:`0 2px 10px ${accent}55` }}>
            <TIcon name="sparkles" size={19} color="#fff" sw={1.8}/>
          </div>
          <div style={{ flex:1 }}>
            <div style={{ fontFamily:DISPLAY, fontSize:16, fontWeight:600, color:C.fg }}>Murabbiy</div>
            <div style={{ fontFamily:FONT, fontSize:12, color:C.green, display:'flex', alignItems:'center', gap:5 }}><span style={{ width:6, height:6, borderRadius:3, background:C.green }}/>On-device · private</div>
          </div>
          <button onClick={onFull} style={{ background:'none', border:'none', color:accent, cursor:'pointer', padding:6 }}><TIcon name="arrowUpRight" size={20} color={accent}/></button>
          <button onClick={onClose} style={{ background:C.card2, border:'none', borderRadius:'50%', width:30, height:30, color:C.fg2, cursor:'pointer', display:'flex', alignItems:'center', justifyContent:'center' }}><TIcon name="x" size={16} color={C.fg2}/></button>
        </div>
        <div ref={scroller} style={{ flex:1, overflowY:'auto', padding:'14px 16px', display:'flex', flexDirection:'column', gap:10 }}>
          {msgs.map((m,i) => <ChatBubble key={i} m={m} accent={accent}/>)}
        </div>
        <div style={{ padding:'0 14px 8px', display:'flex', gap:7, overflowX:'auto' }}>
          {TCHAT_SUGGEST.map(s => <button key={s} onClick={()=>send(s)} style={{ whiteSpace:'nowrap', background:C.card2, border:`0.5px solid ${C.sep}`, borderRadius:999, padding:'7px 12px', fontFamily:FONT, fontSize:12.5, color:C.fg2, cursor:'pointer' }}>{s}</button>)}
        </div>
        <ChatInput draft={draft} setDraft={setDraft} onSend={()=>send()} accent={accent}/>
      </div>
    </div>
  );
}
function ChatBubble({ m, accent }) {
  const me = m.from === 'me';
  return (
    <div style={{ display:'flex', justifyContent: me?'flex-end':'flex-start' }}>
      <div style={{ maxWidth:'82%', padding:'10px 13px', borderRadius:16, borderBottomRightRadius: me?5:16, borderBottomLeftRadius: me?16:5, background: me?accent:C.card, border: me?'none':`0.5px solid ${C.sep}`, color: me?'#fff':C.fg, fontFamily:FONT, fontSize:14, lineHeight:1.5 }}>{m.text}</div>
    </div>
  );
}
function ChatInput({ draft, setDraft, onSend, accent }) {
  return (
    <div style={{ padding:'8px 14px 16px', display:'flex', alignItems:'center', gap:9, borderTop:`0.5px solid ${C.sep}` }}>
      <div style={{ flex:1, display:'flex', alignItems:'center', gap:8, background:C.card, border:`0.5px solid ${C.line}`, borderRadius:999, padding:'9px 14px' }}>
        <input value={draft} onChange={e=>setDraft(e.target.value)} onKeyDown={e=>{ if(e.key==='Enter') onSend(); }} placeholder="Ask Murabbiy…" style={{ flex:1, background:'none', border:'none', outline:'none', color:C.fg, fontFamily:FONT, fontSize:15 }}/>
        <TIcon name="mic" size={18} color={C.fg3}/>
      </div>
      <button onClick={onSend} style={{ width:40, height:40, borderRadius:'50%', border:'none', background:accent, cursor:'pointer', display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}><TIcon name="send" size={18} color="#fff"/></button>
    </div>
  );
}
function aiReply(t) {
  const s = t.toLowerCase();
  if (s.includes('plan')) return "Here's your day: 90 min generics this morning, a 25-min English drill at 18:00, and a short reflection tonight. I kept the afternoon light for your interview prep. Want me to add the mock interview now?";
  if (s.includes('quiz') || s.includes('generic')) return "Let's do it. Quick one: in `function first<T>(arr: T[]): T`, what is `T`? a) a fixed type, b) a placeholder the caller fills in, c) always any.";
  if (s.includes('reflect') || s.includes('yesterday')) return "Yesterday you noted impatience in a code review. A small practice: before replying, take one breath and ask 'is this true, is it kind, is it needed?' Want a prompt for tonight's tafakkur?";
  if (s.includes('interview') || s.includes('thursday')) return "Thursday is system design. We'll rehearse one out loud — start with a URL shortener. Take 30 seconds: how would you map a long URL to a short code?";
  return "Got it. I'll fold that into your plan and keep an eye on your goals. Anything you want to tackle first — learning, focus, or reflection?";
}

// ── Calm motifs (mindful.so influence) ───────────────────────────────────────
// Soft ambient glow behind hero content.
function AmbientGlow({ color, top = -60, opacity = 0.5 }) {
  return <div style={{ position:'absolute', top, left:'50%', transform:'translateX(-50%)', width:420, height:300, borderRadius:'50%', background:`radial-gradient(closest-side, ${softOf(color, opacity)}, transparent 72%)`, pointerEvents:'none', zIndex:0, filter:'blur(8px)' }}/>;
}
// Breathing orb — gentle pulsing concentric rings. Used for companion + reflection.
function BreathOrb({ size = 120, color, label }) {
  return (
    <div style={{ position:'relative', width:size, height:size, display:'flex', alignItems:'center', justifyContent:'center' }}>
      <div className="tf-breathe" style={{ position:'absolute', inset:0, borderRadius:'50%', background:`radial-gradient(circle, ${softOf(color,0.45)}, ${softOf(color,0.06)} 70%)` }}/>
      <div className="tf-breathe" style={{ position:'absolute', inset:size*0.18, borderRadius:'50%', border:`1px solid ${softOf(color,0.5)}`, animationDelay:'0.4s' }}/>
      <div className="tf-breathe" style={{ position:'absolute', inset:size*0.32, borderRadius:'50%', background:`linear-gradient(135deg, ${color}, ${C.purple})`, boxShadow:`0 0 36px ${softOf(color,0.7)}`, animationDelay:'0.2s' }}/>
      <div style={{ position:'relative', zIndex:2, color:'#fff', display:'flex', alignItems:'center', justifyContent:'center' }}>
        <TIcon name="sparkles" size={size*0.28} color="#fff" sw={1.6}/>
      </div>
      {label && <div style={{ position:'absolute', bottom:-26, fontFamily:MONO, fontSize:11, letterSpacing:'0.08em', textTransform:'uppercase', color:C.fg3 }}>{label}</div>}
    </div>
  );
}

Object.assign(window, {
  C, FONT, DISPLAY, MONO, SERIF, THEMES, THEME_META, applyTheme, named, softOf, AppCtx, useApp, TIcon,
  AmbientGlow, BreathOrb,
  TStatusBar, Notch, HomeIndicator, TTabBar, TABS, Card, SectionTitle, Overline,
  ProgressBar, Pill, IconBadge, Segmented, ProactiveCard, LargeTitle, BackBar,
  HubLauncher, HUB_MODULES, CompanionFab, CompanionSheet, ChatBubble, ChatInput, aiReply,
});
