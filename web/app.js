// Troop 53 Scout Guide, web edition. Same content files as the Android/iOS apps (see tools/build_web.py).
const SCOUTBOOK_URL = 'https://advancements.scouting.org/';
const $app = document.getElementById('app');
const $viewer = document.getElementById('viewer');
let cats = [], articles = [], byId = {}, questions = [];

const esc = s => String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const imgSrc = p => 'img/' + p.replace(/\.xml$/, '.svg'); // Android vectors are exported from art/svg/<same path>.svg

// Port of GuideParser.parse in Guide.kt; keep the two in sync.
function parseGuide(text) {
  const out = []; let cat = null, art = null;
  const fields = (s, n) => { const f = s.split('|').map(x => x.trim()); if (f.length !== n) throw Error('Expected ' + n + ' fields in: ' + s); return f; };
  for (const raw of text.split('\n')) {
    const line = raw.trim(); let m;
    if (!line) continue;
    if (line.startsWith('===')) { const [id, title, icon, color, subtitle] = fields(line.slice(3), 5); cat = { id, title, icon, color, subtitle, articles: [] }; out.push(cat); }
    else if (line.startsWith('## ')) art.blocks.push({ k: 'h', t: line.slice(3) });
    else if (line.startsWith('# ')) { const [id, title, summary] = fields(line.slice(2), 3); art = { id, title, summary, cat, blocks: [] }; cat.articles.push(art); }
    else if (line.startsWith('- ')) art.blocks.push({ k: 'b', t: line.slice(2) });
    else if (line.startsWith('! ')) art.blocks.push({ k: 'w', t: line.slice(2) });
    else if (line.startsWith('> ')) art.blocks.push({ k: 't', t: line.slice(2) });
    else if (line.startsWith('@ ')) { const [p, t] = line.slice(2).split(/\|(.*)/s).map(x => x.trim()); art.blocks.push({ k: 'i', t, p }); }
    else if ((m = line.match(/^(\d+)\.\s+(.*)$/))) art.blocks.push({ k: 's', n: m[1], t: m[2] });
    else art.blocks.push({ k: 'p', t: line });
  }
  return out;
}

function parseQuiz(text) {
  return text.split(/\n\s*\n/).map(chunk => {
    const lines = chunk.split('\n').map(l => l.trim()).filter(Boolean);
    const get = p => lines.filter(l => l.startsWith(p)).map(l => l.slice(p.length).trim());
    return lines.length ? { q: get('Q:')[0], a: get('A:')[0], w: get('W:'), e: get('E:')[0] || '' } : null;
  }).filter(Boolean);
}

// Same rule as Guide.search: all words must appear; title hits rank first.
function search(q) {
  const words = q.toLowerCase().split(/\s+/).filter(Boolean);
  if (!words.length) return [];
  return articles.filter(a => words.every(w => a.text.includes(w)))
    .map(a => [a, words.filter(w => a.title.toLowerCase().includes(w)).length])
    .sort((x, y) => y[1] - x[1]).map(x => x[0]);
}

// ---------- saved (per device) ----------
const loadSaved = () => { try { return JSON.parse(localStorage.getItem('saved') || '[]'); } catch { return []; } };
let saved = loadSaved();
function toggleSaved(id) {
  saved = saved.includes(id) ? saved.filter(x => x !== id) : [...saved, id];
  try { localStorage.setItem('saved', JSON.stringify(saved)); } catch {}
  return saved.includes(id);
}

// ---------- views ----------
const header = () => `
  <header class="hero" id="hero">
    <img src="icon.svg" alt="Troop 53 emblem" class="emblem">
    <div><div class="kicker">TROOP 53</div><h1>Scout Guide</h1><div class="motto">Be Prepared · Do a Good Turn Daily</div></div>
  </header>`;

const bar = (title, color, extra = '') => `
  <nav class="bar" style="background:${color}"><a href="#/" class="back" aria-label="Home">‹</a><span>${esc(title)}</span>${extra}</nav>`;

const row = (a, showCat) => `
  <a class="row" href="#/a/${a.id}">
    <i style="background:${a.cat.color}"></i>
    <div>${showCat ? `<small style="color:${a.cat.color}">${esc(a.cat.title.toUpperCase())}</small>` : ''}<b>${esc(a.title)}</b><span>${esc(a.summary)}</span></div>
    ${saved.includes(a.id) ? '<em>★</em>' : ''}<span class="chev">›</span>
  </a>`;

function home() {
  $app.innerHTML = header() + `
    <input id="q" type="search" placeholder="Search knots, first aid, recipes…" aria-label="Search the guide">
    <div id="results"></div>
    <div id="main">
      <section class="sos">
        <div class="kicker">EMERGENCY</div><h2>Check the scene · Call 911 · Care</h2>
        <div class="pills"><a href="tel:911">📞 Call 911</a><a href="tel:18002221222">☠ Poison Control</a></div>
        <div class="pills"><a href="#/a/hurry-cases">Hurry cases</a><a href="#/a/cpr">CPR</a><a href="#/a/bleeding">Bleeding</a></div>
      </section>
      <div class="tools">
        <a href="#/saved"><b>★</b>Saved</a><a href="#/quiz"><b>✔</b>Quiz</a><a href="#/a/scout-law"><b>⚜</b>Scout Law</a><a href="#/about"><b>ⓘ</b>About</a>
      </div>
      <a class="big" href="${SCOUTBOOK_URL}" target="_blank" rel="noopener">Open Scoutbook Plus ↗</a>
      <h3 class="label">Guide sections</h3>
      <div class="grid">${cats.map(c => `
        <a class="tile" href="#/c/${c.id}"><span class="badge" style="background:${c.color}">${c.icon}</span><b>${esc(c.title)}</b><small>${c.articles.length} guides</small></a>`).join('')}
      </div>
      <p class="foot">Unofficial troop guide · Not endorsed by Scouting America</p>
    </div>`;
  const q = document.getElementById('q'), res = document.getElementById('results'), main = document.getElementById('main');
  q.value = sessionStorage.getItem('q') || '';
  const run = () => {
    const v = q.value; sessionStorage.setItem('q', v);
    main.hidden = !!v.trim();
    if (!v.trim()) { res.innerHTML = ''; return; }
    const hits = search(v);
    res.innerHTML = `<h3 class="label">${hits.length ? `${hits.length} result${hits.length === 1 ? '' : 's'}` : `No matches for "${esc(v)}"`}</h3>` + hits.map(a => row(a, true)).join('');
  };
  q.oninput = run; run();
}

function category(c) {
  $app.innerHTML = bar(c.title, c.color) + `
    <div class="band" style="background:${c.color}"><h1>${c.icon} ${esc(c.title)}</h1><p>${esc(c.subtitle)}</p></div>
    ${figure({ p: `headers/${c.id}.xml`, t: `${c.title} field illustration` })}
    ${c.articles.map(a => row(a, false)).join('')}`;
}

const figure = b => `<figure><button class="zoom" data-src="${imgSrc(b.p)}" data-cap="${esc(b.t)}"><img src="${imgSrc(b.p)}" alt="${esc(b.t)}" loading="lazy"></button><figcaption>${esc(b.t)}</figcaption></figure>`;

function block(b) {
  switch (b.k) {
    case 'h': return `<h2>${esc(b.t)}</h2>`;
    case 'b': return `<p class="bul">${esc(b.t)}</p>`;
    case 's': return `<p class="step"><span>${b.n}</span>${esc(b.t)}</p>`;
    case 'w': return `<p class="warn">⚠ ${esc(b.t)}</p>`;
    case 't': return `<p class="tip">💡 ${esc(b.t)}</p>`;
    case 'i': return figure(b);
    default: return `<p>${esc(b.t)}</p>`;
  }
}

function article(a) {
  const c = a.cat, i = c.articles.indexOf(a), prev = c.articles[i - 1], next = c.articles[i + 1];
  const knots = a.blocks.filter(b => b.k === 'i' && b.p.startsWith('knots/'));
  $app.innerHTML = bar(c.title, c.color, `<button id="star" aria-label="Save">${saved.includes(a.id) ? '★' : '☆'}</button>`) + `
    <div class="band" style="background:${c.color}"><h1>${esc(a.title)}</h1><p>${esc(a.summary)}</p></div>
    <article>
      ${knots.length ? '<button class="big ghost" id="practice">Knot Practice ›</button>' : ''}
      ${a.blocks.map(block).join('')}
    </article>
    <div class="prevnext">
      ${prev ? `<a href="#/a/${prev.id}">‹ ${esc(prev.title)}</a>` : '<span></span>'}
      ${next ? `<a href="#/a/${next.id}">${esc(next.title)} ›</a>` : '<span></span>'}
    </div>
    <a class="big ghost" href="#/c/${c.id}">All ${esc(c.title)} guides</a>`;
  const star = document.getElementById('star');
  star.onclick = () => { star.textContent = toggleSaved(a.id) ? '★' : '☆'; };
  if (knots.length) document.getElementById('practice').onclick = () => practice(a, knots, 0);
}

function savedView() {
  const list = saved.map(id => byId[id]).filter(Boolean);
  $app.innerHTML = bar('Saved guides', '#1F3A24') + (list.length ? list.map(a => row(a, true)).join('')
    : '<p class="pad muted">Tap ☆ on any guide to save it here. Saved guides stay on this device.</p>');
}

// ---------- quiz ----------
let quiz = null;
const shuffle = xs => { xs = [...xs]; for (let i = xs.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [xs[i], xs[j]] = [xs[j], xs[i]]; } return xs; };
const newRound = () => { quiz = { qs: shuffle(questions).slice(0, 10), i: 0, score: 0, picked: null }; quiz.opts = shuffle([quiz.qs[0].a, ...quiz.qs[0].w]); };

function quizView() {
  if (!quiz) newRound();
  const head = bar('Skills Quiz', '#1F3A24');
  if (quiz.i >= quiz.qs.length) {
    const pct = quiz.score * 100 / quiz.qs.length;
    $app.innerHTML = head + `<div class="pad center"><div class="score">${quiz.score} / ${quiz.qs.length}</div>
      <p>${pct === 100 ? 'Perfect! You are prepared.' : pct >= 80 ? 'Great job, Scout!' : pct >= 50 ? 'Good effort. Review the guides and try again.' : 'Keep practicing. Every Eagle started as a Scout.'}</p>
      <button class="big" id="again">Play again</button></div>`;
    document.getElementById('again').onclick = () => { newRound(); quizView(); };
    return;
  }
  const q = quiz.qs[quiz.i], p = quiz.picked;
  $app.innerHTML = head + `<div class="pad">
    <p class="muted">Question ${quiz.i + 1} of ${quiz.qs.length} · Score ${quiz.score}</p>
    <h2 class="qprompt">${esc(q.q)}</h2>
    ${quiz.opts.map((o, k) => `<button class="opt ${p == null ? '' : o === q.a ? 'ok' : o === p ? 'bad' : 'dim'}" data-k="${k}" ${p == null ? '' : 'disabled'}>${esc(o)}</button>`).join('')}
    ${p == null ? '' : `<p class="tip" role="status">${p === q.a ? 'Correct!' : 'Not quite. Answer: ' + esc(q.a)}${q.e ? ' — ' + esc(q.e) : ''}</p>
      <button class="big" id="next">${quiz.i === quiz.qs.length - 1 ? 'See score' : 'Next question'}</button>`}
  </div>`;
  $app.querySelectorAll('.opt').forEach(btn => btn.onclick = () => { quiz.picked = quiz.opts[btn.dataset.k]; if (quiz.picked === q.a) quiz.score++; quizView(); });
  const nx = document.getElementById('next');
  if (nx) nx.onclick = () => { quiz.i++; quiz.picked = null; if (quiz.i < quiz.qs.length) { const n = quiz.qs[quiz.i]; quiz.opts = shuffle([n.a, ...n.w]); } quizView(); };
}

function about() {
  $app.innerHTML = bar('About', '#1F3A24') + `<div class="pad center">
    <img src="icon.svg" alt="" class="emblem big-emblem">
    <h1 class="forest">Troop 53 Scout Guide</h1>
    <p class="muted">Web edition · ${cats.length} sections · ${articles.length} guides · ${questions.length} quiz questions</p></div>
    <div class="pad">
    <p>A pocket field guide for the Scouts of Troop 53: first aid, the Scout Oath and Law, camping, cooking, knots, navigation, survival, and more. Once this page has loaded, it works offline. Add it to your home screen to keep it handy on the trail.</p>
    <p>This is an unofficial guide made for our troop. It is not published or endorsed by Scouting America (the Boy Scouts of America).</p>
    <p>First aid information is a quick reference and does not replace hands-on training. In an emergency, call 911. Get trained in first aid and CPR.</p>
    <p>Rank and award requirements change. Always check the current Scouts BSA Handbook and ask your leaders.</p>
    <p class="install">To install: on iPhone, tap Share → Add to Home Screen. On Android, tap ⋮ → Add to Home screen / Install app.</p>
    <h2 class="forest center">Be Prepared.</h2></div>`;
}

// ---------- full-screen viewers ----------
function openViewer(html, onKey) {
  $viewer.innerHTML = html; $viewer.hidden = false; document.body.classList.add('locked');
  const close = () => { $viewer.hidden = true; $viewer.innerHTML = ''; document.body.classList.remove('locked'); document.onkeydown = null; };
  $viewer.querySelector('.close').onclick = close;
  document.onkeydown = e => { if (e.key === 'Escape') close(); else onKey && onKey(e); };
  $viewer.querySelector('.close').focus();
  return close;
}

function zoom(src, cap) {
  let s = 1;
  openViewer(`<div class="vhead"><b>${esc(cap)}</b><button class="close">Close</button></div>
    <div class="vbody"><img src="${src}" alt="${esc(cap)}" id="zimg"></div>
    <div class="vctl"><button data-z="0.7" aria-label="Zoom out">−</button><button data-z="1.4" aria-label="Zoom in">+</button><button data-z="0">Reset</button></div>`);
  const img = document.getElementById('zimg');
  // ponytail: button zoom + native scroll; pinch handled by the browser where it allows it.
  const set = v => { s = Math.min(5, Math.max(1, v)); img.style.width = s * 100 + '%'; };
  $viewer.querySelectorAll('[data-z]').forEach(b => b.onclick = () => set(+b.dataset.z ? s * b.dataset.z : 1));
  img.ondblclick = () => set(s > 1 ? 1 : 2.5);
}

function practice(a, steps, i) {
  const close = openViewer(`<div class="vhead"><b>${esc(a.title)}</b><button class="close">Close</button></div>
    <p class="muted pad" aria-live="polite">Step ${i + 1} of ${steps.length}</p>
    <div class="vbody">${figure(steps[i])}</div>
    <div class="vctl"><button id="pb" ${i ? '' : 'disabled'}>Back</button><button id="pn">${i === steps.length - 1 ? 'Done' : 'Next'}</button></div>`,
    e => { if (e.key === 'ArrowRight') next(); if (e.key === 'ArrowLeft' && i) practice(a, steps, i - 1); });
  const next = () => i === steps.length - 1 ? close() : practice(a, steps, i + 1);
  document.getElementById('pb').onclick = () => practice(a, steps, i - 1);
  document.getElementById('pn').onclick = next;
}

document.addEventListener('click', e => {
  const z = e.target.closest('button.zoom');
  if (z && !$viewer.contains(z)) zoom(z.dataset.src, z.dataset.cap);
});

// ---------- routing ----------
const scrolls = {};
let current = '';
function route() {
  scrolls[current] = scrollY;
  current = location.hash || '#/';
  const [, kind, id] = current.split('/');
  const target = kind === 'a' ? byId[id] : kind === 'c' ? cats.find(c => c.id === id) : null;
  if (kind === 'a' && target) article(target);
  else if (kind === 'c' && target) category(target);
  else if (kind === 'saved') savedView();
  else if (kind === 'quiz') quizView();
  else if (kind === 'about') about();
  else home();
  document.title = (target ? target.title + ' · ' : '') + 'Troop 53 Scout Guide';
  scrollTo(0, scrolls[current] || 0);
}

Promise.all(['guide.txt', 'quiz.txt'].map(f => fetch(f).then(r => { if (!r.ok) throw Error(f + ' ' + r.status); return r.text(); })))
  .then(([g, q]) => {
    cats = parseGuide(g);
    articles = cats.flatMap(c => c.articles);
    articles.forEach(a => { byId[a.id] = a; a.text = [a.title, a.summary, ...a.blocks.map(b => b.t)].join(' ').toLowerCase(); });
    questions = parseQuiz(q);
    addEventListener('hashchange', route);
    route();
  })
  .catch(err => { $app.innerHTML = `<p class="pad">Could not load the guide (${esc(err.message)}). Connect to the internet once, then reload.</p>`; });

if ('serviceWorker' in navigator) navigator.serviceWorker.register('sw.js');
