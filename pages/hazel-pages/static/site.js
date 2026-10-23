// Hazel site: behaviour shared by every page. The theme itself is applied by a one-line
// script in each page's head, before anything paints, so there is no flash of the wrong one.
(function () {
  var root = document.documentElement;

  // ── Theme: auto, light, dark. Remembered on this device when storage allows. ──
  var order = ['light', 'dark', 'auto'];
  var labels = { auto: 'Theme: match system', light: 'Theme: light', dark: 'Theme: dark' };
  function current() { return root.getAttribute('data-theme') || 'auto'; }
  // Dark unless the visitor picked something else.
  if (!root.hasAttribute('data-theme') && !root.hasAttribute('data-theme-auto')) root.setAttribute('data-theme', 'dark');
  function apply(t) {
    if (t === 'auto') { root.removeAttribute('data-theme'); root.setAttribute('data-theme-auto', ''); } else { root.removeAttribute('data-theme-auto'); root.setAttribute('data-theme', t); }
    document.querySelectorAll('[data-theme-toggle]').forEach(function (b) {
      b.setAttribute('aria-label', labels[t]); b.title = labels[t];
    });
  }
  apply(current());
  document.querySelectorAll('[data-theme-toggle]').forEach(function (b) {
    b.addEventListener('click', function () {
      var next = order[(order.indexOf(current()) + 1) % order.length];
      apply(next);
      try { localStorage.setItem('hazel-theme', next); } catch (e) {}
    });
  });

  // ── Hairline under the nav once the page moves. ──
  var nav = document.querySelector('.nav');
  function onScroll() { if (nav) nav.classList.toggle('scrolled', window.scrollY > 8); }
  window.addEventListener('scroll', onScroll, { passive: true }); onScroll();

  // ── Small-screen menu: opens and closes, and closes again after a tap on a link. ──
  var menuBtn = document.querySelector('.menu-btn');
  var menu = document.querySelector('.mobile-menu');
  function setMenu(open) {
    if (!menu || !menuBtn) return;
    menu.classList.toggle('open', open);
    menuBtn.setAttribute('aria-expanded', open ? 'true' : 'false');
  }
  if (menuBtn && menu) {
    menuBtn.addEventListener('click', function () { setMenu(!menu.classList.contains('open')); });
    menu.querySelectorAll('a').forEach(function (a) { a.addEventListener('click', function () { setMenu(false); }); });
    window.addEventListener('resize', function () { if (window.innerWidth > 1080) setMenu(false); });
  }

  // ── Fade content in as it arrives. ──
  var items = document.querySelectorAll('.reveal');
  if ('IntersectionObserver' in window) {
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (e) {
        if (e.isIntersecting) { e.target.classList.add('in'); io.unobserve(e.target); }
      });
    }, { rootMargin: '0px 0px -6% 0px' });
    items.forEach(function (el) { io.observe(el); });
  } else {
    items.forEach(function (el) { el.classList.add('in'); });
  }
  // Anything opened from a link (a FAQ answer, a guide section) is shown straight away.
  function revealTarget() {
    var id = decodeURIComponent(location.hash.slice(1));
    var el = id && document.getElementById(id);
    if (!el) return;
    var holder = el.closest('.reveal');
    if (holder) holder.classList.add('in');
    if (el.tagName === 'DETAILS') el.open = true;
    el.querySelectorAll('.reveal').forEach(function (r) { r.classList.add('in'); });
  }
  revealTarget(); window.addEventListener('hashchange', revealTarget);

  // ── Home ticker: flip the release-note lines upward every few seconds. A copy of the
  // first line sits at the end so the loop wraps without a jump; hovering holds it still. ──
  var reel = document.querySelector('.pill .reel');
  var lines = reel ? reel.querySelectorAll('.line') : [];
  var still = window.matchMedia && matchMedia('(prefers-reduced-motion: reduce)').matches;
  if (lines.length > 1 && !still) {
    reel.appendChild(lines[0].cloneNode(true)).setAttribute('aria-hidden', 'true');
    var at = 0, held = false, pill = reel.closest('.pill');
    pill.addEventListener('mouseenter', function () { held = true; });
    pill.addEventListener('mouseleave', function () { held = false; });
    setInterval(function () {
      if (held || document.hidden) return;
      at++;
      reel.style.transform = 'translateY(' + (-at * lines[0].offsetHeight) + 'px)';
      if (at === lines.length) setTimeout(function () {
        reel.classList.add('snap'); reel.style.transform = ''; at = 0;
        reel.offsetHeight; reel.classList.remove('snap');
      }, 650);
    }, 3200);
  }

  // ── Copy buttons: data-copy holds the id of the element whose text is copied. ──
  document.querySelectorAll('[data-copy]').forEach(function (b) {
    b.addEventListener('click', function () {
      var src = document.getElementById(b.getAttribute('data-copy'));
      var text = src ? src.textContent.trim() : b.getAttribute('data-copy');
      var was = b.textContent;
      var done = function (ok) {
        b.textContent = ok ? 'Copied' : 'Select and copy';
        setTimeout(function () { b.textContent = was; }, 1600);
      };
      if (navigator.clipboard && window.isSecureContext) {
        navigator.clipboard.writeText(text).then(function () { done(true); }, function () { done(false); });
      } else if (src && window.getSelection) {
        var range = document.createRange(); range.selectNodeContents(src);
        var sel = window.getSelection(); sel.removeAllRanges(); sel.addRange(range);
        var ok = false; try { ok = document.execCommand('copy'); } catch (e) {}
        done(ok);
      }
    });
  });

  // ── Horizontal galleries: the arrows scroll one card at a time. ──
  document.querySelectorAll('[data-gallery]').forEach(function (g) {
    var track = g.querySelector('.track');
    var buttons = g.querySelectorAll('[data-dir]');
    if (!buttons.length) buttons = document.querySelectorAll('[data-gallery-nav] [data-dir]');
    // Arrows only when there is something to scroll to, and dimmed at either end.
    function update() {
      var max = track.scrollWidth - track.clientWidth;
      g.classList.toggle('scrolls', max > 4);
      buttons.forEach(function (b) {
        var dir = Number(b.getAttribute('data-dir'));
        b.disabled = dir < 0 ? track.scrollLeft <= 4 : track.scrollLeft >= max - 4;
      });
    }
    track.addEventListener('scroll', update, { passive: true });
    window.addEventListener('resize', update);
    window.addEventListener('load', update);
    update();
    buttons.forEach(function (b) {
      b.addEventListener('click', function () {
        var card = track.querySelector('figure');
        var step = card ? card.getBoundingClientRect().width + 20 : 320;
        track.scrollBy({ left: step * Number(b.getAttribute('data-dir')), behavior: 'smooth' });
      });
    });
  });

  // ── Guide: highlight the section being read, and the jump menu on small screens. ──
  var tocLinks = document.querySelectorAll('.toc a');
  var jump = document.querySelector('[data-jump]');
  var sections = Array.prototype.map.call(tocLinks.length ? tocLinks : [], function (a) {
    return document.getElementById(a.getAttribute('href').slice(1));
  }).filter(Boolean);
  if (!sections.length && jump) {
    sections = Array.prototype.map.call(jump.options, function (o) { return document.getElementById(o.value); }).filter(Boolean);
  }
  function spy() {
    if (!sections.length) return;
    var line = Math.max(window.innerHeight * 0.35, 280), active = sections[0];
    sections.forEach(function (s) { if (s.getBoundingClientRect().top <= line) active = s; });
    tocLinks.forEach(function (a) { a.classList.toggle('active', a.getAttribute('href') === '#' + active.id); });
    if (jump && !jumping && jump.value !== active.id) jump.value = active.id;
  }
  // While a jump is scrolling the page, the menu keeps the chosen section rather than
  // flicking through every one it passes; it follows the page again once scrolling stops.
  var jumping = false, settle;
  function settled() { jumping = false; spy(); }
  if (sections.length) {
    window.addEventListener('scroll', function () {
      spy();
      clearTimeout(settle); settle = setTimeout(settled, 160);
    }, { passive: true });
    window.addEventListener('scrollend', settled);
    spy();
  }
  if (jump) {
    jump.addEventListener('change', function () {
      var el = document.getElementById(jump.value);
      if (!el) return;
      el.classList.add('in');
      jumping = true;
      history.replaceState(null, '', '#' + jump.value);
      // Scrolled to explicitly, clear of the sticky header and this jump bar.
      var bar = jump.closest('.toc-mobile');
      var offset = (nav ? nav.offsetHeight : 0) + (bar ? bar.offsetHeight : 0) + 24;
      var top = el.getBoundingClientRect().top + window.scrollY - offset;
      var reduce = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      window.scrollTo({ top: Math.max(0, top), behavior: reduce ? 'auto' : 'smooth' });
    });
  }

  // ── FAQ: live search across questions and answers, with topic counts. ──
  var q = document.getElementById('faq-q');
  if (q) {
    var groups = document.querySelectorAll('.faq-group');
    var empty = document.getElementById('faq-empty');
    function count() {
      groups.forEach(function (g) {
        var n = g.querySelectorAll('details.q:not([hidden])').length;
        var c = document.querySelector('[data-count="' + g.id + '"]');
        if (c) c.textContent = n;
      });
    }
    function filter() {
      var term = q.value.trim().toLowerCase();
      var shown = 0;
      groups.forEach(function (g) {
        var inGroup = 0;
        g.querySelectorAll('details.q').forEach(function (d) {
          var hit = !term || d.textContent.toLowerCase().indexOf(term) !== -1;
          d.hidden = !hit;
          if (term && hit && d.querySelector('.a').textContent.toLowerCase().indexOf(term) !== -1) d.open = true;
          if (!term) d.open = false;
          if (hit) inGroup++;
        });
        g.hidden = inGroup === 0;
        shown += inGroup;
      });
      if (empty) empty.style.display = shown ? 'none' : 'block';
      count();
    }
    q.addEventListener('input', filter);
    count();
    // Opening a single question by link: #q-... opens it.
    document.querySelectorAll('details.q').forEach(function (d) {
      d.addEventListener('toggle', function () {
        if (d.open && !q.value) document.querySelectorAll('details.q[open]').forEach(function (o) { if (o !== d) o.open = false; });
      });
    });
  }

  // ── Support: share the site with the phone's own share sheet, or copy the link. ──
  document.querySelectorAll('[data-share]').forEach(function (b) {
    var label = b.querySelector('[data-share-label]');
    var url = location.origin && location.origin !== 'null' ? location.origin + location.pathname.replace(/[^/]*$/, '') : 'https://github.com/SibtainOcn/Hazel';
    var original = label ? label.textContent : '';
    function say(text) {
      if (!label) return;
      label.textContent = text;
      clearTimeout(b._t); b._t = setTimeout(function () { label.textContent = original; }, 2600);
    }
    // Copies the link by whatever the browser allows; the last resort shows it to copy by hand.
    function copy() {
      var done = function () { say('Link copied. Paste it anywhere.'); };
      var manual = function () {
        var area = document.createElement('textarea');
        area.value = url; area.setAttribute('readonly', ''); area.style.cssText = 'position:fixed;opacity:0';
        document.body.appendChild(area); area.select();
        var ok = false; try { ok = document.execCommand('copy'); } catch (e) {}
        area.remove();
        if (ok) done(); else say(url);
      };
      if (navigator.clipboard && window.isSecureContext) navigator.clipboard.writeText(url).then(done, manual);
      else manual();
    }
    b.addEventListener('click', function () {
      var data = { title: 'Hazel', text: 'Hazel saves video and music to your Android phone. Free, no ads.', url: url };
      // Phones open their own share sheet. A desktop without one, or one that refuses,
      // copies the link instead; closing the sheet on purpose does nothing.
      if (navigator.share && (!navigator.canShare || navigator.canShare(data))) {
        navigator.share(data).catch(function (e) { if (!e || e.name !== 'AbortError') copy(); });
      } else {
        copy();
      }
    });
  });
})();

// ── Screenshot viewer: any screenshot opens large, with the others on the page a swipe,
//    an arrow or an arrow key away. Esc, the close button or a tap outside closes it. ──
(function () {
  var shots = Array.prototype.slice.call(document.querySelectorAll('.device img'));
  if (!shots.length) return;
  // The same screenshot can appear twice on a page; the viewer steps through each once.
  var seen = {}, list = [];
  shots.forEach(function (img) {
    var key = img.getAttribute('src');
    if (!seen[key]) { seen[key] = list.length; list.push(img); }
  });

  var box = document.createElement('div');
  box.className = 'viewer';
  box.setAttribute('role', 'dialog');
  box.setAttribute('aria-modal', 'true');
  box.setAttribute('aria-label', 'Screenshot');
  box.hidden = true;
  var arrow = function (d) {
    return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="' + d + '"/></svg>';
  };
  box.innerHTML =
    '<button class="v-close" type="button" aria-label="Close">' + arrow('M6 6l12 12M18 6 6 18') + '</button>' +
    '<button class="v-prev" type="button" aria-label="Previous">' + arrow('m15 6-6 6 6 6') + '</button>' +
    '<figure class="v-stage"><img alt=""><figcaption></figcaption></figure>' +
    '<button class="v-next" type="button" aria-label="Next">' + arrow('m9 6 6 6-6 6') + '</button>' +
    '<div class="v-count mono"></div>';
  document.body.appendChild(box);

  var img = box.querySelector('.v-stage img');
  var cap = box.querySelector('figcaption');
  var count = box.querySelector('.v-count');
  var index = 0, lastFocus = null;

  // A screenshot shown in several places takes its caption from whichever copy has one.
  var captions = {};
  shots.forEach(function (el) {
    var fig = el.closest('figure');
    var fc = fig && fig.querySelector('figcaption');
    var text = el.getAttribute('alt') || '';
    if (fc) {
      var b = fc.querySelector('b');
      var rest = fc.textContent.replace(b ? b.textContent : '', '').replace(/\s+/g, ' ').trim();
      text = b ? b.textContent.trim() + (rest ? '. ' + rest : '') : rest;
    }
    var key = el.getAttribute('src');
    if (text && (!captions[key] || fc)) captions[key] = text;
  });
  function captionFor(el) { return captions[el.getAttribute('src')] || ''; }
  function show(i) {
    index = (i + list.length) % list.length;
    var src = list[index];
    img.src = src.currentSrc || src.src;
    img.alt = captionFor(src) || 'Screenshot';
    cap.textContent = captionFor(src);
    count.textContent = (index + 1) + ' / ' + list.length;
    box.classList.toggle('single', list.length < 2);
  }
  function open(i) {
    lastFocus = document.activeElement;
    show(i);
    box.hidden = false;
    document.documentElement.classList.add('viewer-open');
    requestAnimationFrame(function () { box.classList.add('on'); });
    box.querySelector('.v-close').focus({ preventScroll: true });
  }
  function close() {
    box.classList.remove('on');
    document.documentElement.classList.remove('viewer-open');
    setTimeout(function () { box.hidden = true; }, 180);
    if (lastFocus && lastFocus.focus) lastFocus.focus({ preventScroll: true });
  }

  shots.forEach(function (el) {
    var wrap = el.closest('.device');
    wrap.classList.add('zoomable');
    wrap.setAttribute('tabindex', '0');
    wrap.setAttribute('role', 'button');
    wrap.setAttribute('aria-label', 'View larger' + (captionFor(el) ? ': ' + captionFor(el) : ''));
    wrap.removeAttribute('aria-hidden');
    var go = function () { open(seen[el.getAttribute('src')]); };
    wrap.addEventListener('click', go);
    wrap.addEventListener('keydown', function (e) {
      if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); go(); }
    });
  });
  // The hero's phones are decoration to screen readers until now; they open the viewer too.
  document.querySelectorAll('.stage[aria-hidden]').forEach(function (s) { s.removeAttribute('aria-hidden'); });

  box.querySelector('.v-close').addEventListener('click', close);
  box.querySelector('.v-prev').addEventListener('click', function () { show(index - 1); });
  box.querySelector('.v-next').addEventListener('click', function () { show(index + 1); });
  box.addEventListener('click', function (e) { if (e.target === box) close(); });
  document.addEventListener('keydown', function (e) {
    if (box.hidden) return;
    if (e.key === 'Escape') close();
    else if (e.key === 'ArrowLeft') show(index - 1);
    else if (e.key === 'ArrowRight') show(index + 1);
    else if (e.key === 'Tab') {
      // Keep focus inside the viewer while it is open.
      var f = Array.prototype.filter.call(box.querySelectorAll('button'), function (b) { return b.offsetParent; });
      var first = f[0], last = f[f.length - 1];
      if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
      else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
    }
  });
  // Swipe left or right on a phone.
  var sx = null, sy = null;
  box.addEventListener('touchstart', function (e) { sx = e.touches[0].clientX; sy = e.touches[0].clientY; }, { passive: true });
  box.addEventListener('touchend', function (e) {
    if (sx === null) return;
    var dx = e.changedTouches[0].clientX - sx, dy = e.changedTouches[0].clientY - sy;
    if (Math.abs(dx) > 50 && Math.abs(dx) > Math.abs(dy)) show(index + (dx < 0 ? 1 : -1));
    else if (dy > 90 && Math.abs(dy) > Math.abs(dx)) close();
    sx = sy = null;
  });
})();

// ── Site search: the box in the header, or "/" and Ctrl+K anywhere. It looks through the
//    guide, the FAQ, the home page and the release notes, in search.json, which the build
//    writes from the pages. The file is fetched the first time search opens. ──
(function () {
  var openers = document.querySelectorAll('[data-search]');
  if (!openers.length) return;

  var icon = function (d) {
    return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true">' + d + '</svg>';
  };
  var box = document.createElement('div');
  box.className = 'finder';
  box.setAttribute('role', 'dialog');
  box.setAttribute('aria-modal', 'true');
  box.setAttribute('aria-label', 'Search the site');
  box.hidden = true;
  box.innerHTML =
    '<div class="f-panel">' +
      '<div class="f-field">' + icon('<circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/>') +
        '<input type="search" placeholder="Search the guide, FAQ and more" autocomplete="off" spellcheck="false" enterkeyhint="go"' +
        ' role="combobox" aria-label="Search the site" aria-expanded="false" aria-controls="f-results" aria-autocomplete="list">' +
        '<button class="f-close icon-btn" type="button" aria-label="Close">' + icon('<path d="M6 6l12 12M18 6 6 18"/>') + '</button>' +
      '</div>' +
      '<ul class="f-results" id="f-results" role="listbox" aria-label="Results"></ul>' +
      '<p class="f-note" aria-live="polite"></p>' +
    '</div>';
  document.body.appendChild(box);

  var input = box.querySelector('input');
  var closeBtn = box.querySelector('.f-close');
  var list = box.querySelector('.f-results');
  var note = box.querySelector('.f-note');
  var entries = null, loading = null, active = -1, lastFocus = null;

  // Fetched once. A failed fetch is not kept, so opening search again tries again.
  function load() {
    if (entries) return Promise.resolve(entries);
    if (!loading) {
      loading = fetch('search.json').then(function (r) {
        if (!r.ok) throw new Error('search.json ' + r.status);
        return r.json();
      }).then(function (data) {
        entries = data.map(function (e) { return { e: e, t: e.t.toLowerCase(), x: e.x.toLowerCase() }; });
        return entries;
      }, function (err) { loading = null; throw err; });
    }
    return loading;
  }

  function wordStart(text, at) { return at === 0 || /[^a-z0-9]/.test(text.charAt(at - 1)); }

  // Every word typed must appear. Words in the title, and the start of words, count for more.
  function find(terms, phrase) {
    var hits = [];
    entries.forEach(function (item, order) {
      var score = 0;
      for (var i = 0; i < terms.length; i++) {
        var term = terms[i], at = item.t.indexOf(term);
        if (at !== -1) { score += wordStart(item.t, at) ? 12 : 6; continue; }
        at = item.x.indexOf(term);
        if (at === -1) return;
        score += wordStart(item.x, at) ? 3 : 1;
      }
      if (terms.length > 1 && item.t.indexOf(phrase) !== -1) score += 20;
      hits.push({ item: item, score: score, order: order });
    });
    hits.sort(function (a, b) { return b.score - a.score || a.order - b.order; });
    return hits.slice(0, 8);
  }

  // Text with each typed word marked, built as nodes so nothing in it is read as markup.
  function marked(className, text, terms) {
    var el = document.createElement('span');
    el.className = className;
    var pattern = new RegExp('(' + terms.map(function (t) { return t.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'); }).join('|') + ')', 'gi');
    text.split(pattern).forEach(function (part, i) {
      if (!part) return;
      if (i % 2) { var m = document.createElement('mark'); m.textContent = part; el.appendChild(m); }
      else el.appendChild(document.createTextNode(part));
    });
    return el;
  }

  // The text around the first word found in it.
  function snippet(item, terms) {
    var at = -1;
    terms.forEach(function (t) { var i = item.x.indexOf(t); if (i !== -1 && (at === -1 || i < at)) at = i; });
    var text = item.e.x;
    if (at < 70) return text.slice(0, 170);
    var start = text.lastIndexOf(' ', at - 50);
    return '...' + text.slice(start + 1, start + 171);
  }

  function select(i) {
    var items = list.children;
    if (!items.length) { active = -1; input.removeAttribute('aria-activedescendant'); return; }
    active = (i + items.length) % items.length;
    Array.prototype.forEach.call(items, function (li, n) { li.setAttribute('aria-selected', n === active ? 'true' : 'false'); });
    input.setAttribute('aria-activedescendant', items[active].id);
    items[active].scrollIntoView({ block: 'nearest' });
  }

  function render() {
    var phrase = input.value.trim().toLowerCase().replace(/\s+/g, ' ');
    list.textContent = '';
    if (!entries) return;
    var terms = phrase ? phrase.split(' ').filter(function (t, i, all) { return all.indexOf(t) === i; }) : [];
    var hits = terms.length ? find(terms, phrase) : [];
    hits.forEach(function (hit, n) {
      var e = hit.item.e;
      var li = document.createElement('li');
      li.id = 'f-opt-' + n;
      li.setAttribute('role', 'option');
      var a = document.createElement('a');
      a.href = e.u;
      a.tabIndex = -1;
      var where = document.createElement('span');
      where.className = 'f-where';
      where.textContent = e.s;
      a.appendChild(where);
      a.appendChild(marked('f-title', e.t, terms));
      if (e.x) a.appendChild(marked('f-snip', snippet(hit.item, terms), terms));
      li.appendChild(a);
      li.addEventListener('mousemove', function () { if (active !== n) select(n); });
      list.appendChild(li);
    });
    input.setAttribute('aria-expanded', hits.length ? 'true' : 'false');
    note.textContent = !terms.length ? 'Search the guide, the FAQ and the home page.'
      : hits.length ? '' : 'Nothing found. Try another word, or ask on GitHub.';
    select(0);
  }

  function open() {
    if (!box.hidden) return;
    lastFocus = document.activeElement;
    box.hidden = false;
    document.documentElement.classList.add('finder-open');
    requestAnimationFrame(function () { box.classList.add('on'); });
    input.focus();
    input.select();
    if (!entries) note.textContent = 'Loading...';
    load().then(render, function () {
      note.textContent = 'Search could not load. Check your connection and try again.';
    });
  }
  function close() {
    if (box.hidden) return;
    box.classList.remove('on');
    document.documentElement.classList.remove('finder-open');
    box.hidden = true;
    if (lastFocus && lastFocus.focus) lastFocus.focus({ preventScroll: true });
  }

  // A part of the page already open: only the hash changes, and the same hash again
  // still opens and shows its part.
  function page(path) { return path.replace(/index\.html$/, ''); }
  function go(a) {
    close();
    if (!a.hash || page(a.pathname) !== page(location.pathname)) { location.href = a.href; return; }
    if (location.hash !== a.hash) { location.hash = a.hash; return; }
    window.dispatchEvent(new HashChangeEvent('hashchange'));
    var el = document.getElementById(decodeURIComponent(a.hash.slice(1)));
    if (el) el.scrollIntoView();
  }

  openers.forEach(function (b) { b.addEventListener('click', open); });
  closeBtn.addEventListener('click', close);
  box.addEventListener('click', function (e) { if (e.target === box) close(); });
  input.addEventListener('input', render);
  list.addEventListener('click', function (e) {
    var a = e.target.closest('a');
    // A new tab or window is the browser's to open.
    if (!a || e.ctrlKey || e.metaKey || e.shiftKey || e.button) return;
    e.preventDefault();
    go(a);
  });
  input.addEventListener('keydown', function (e) {
    if (e.key === 'ArrowDown') { e.preventDefault(); select(active + 1); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); select(active - 1); }
    else if (e.key === 'Enter' && active >= 0) { e.preventDefault(); go(list.children[active].querySelector('a')); }
  });
  document.addEventListener('keydown', function (e) {
    if (!box.hidden) {
      if (e.key === 'Escape') { e.preventDefault(); close(); }
      // Focus moves between the field and the close button while search is open.
      else if (e.key === 'Tab') { e.preventDefault(); (document.activeElement === input ? closeBtn : input).focus(); }
      return;
    }
    var t = e.target, typing = t.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(t.tagName);
    if ((e.key === 'k' || e.key === 'K') && (e.ctrlKey || e.metaKey) && !e.altKey) { e.preventDefault(); open(); }
    else if (e.key === '/' && !typing && !e.ctrlKey && !e.metaKey && !e.altKey) { e.preventDefault(); open(); }
  });
})();

// Pause the aurora while scrolling. The class goes on the aurora alone, so only its four glows
// are restyled, never the page. Phones and reduced motion never animate it, so they skip this.
(function () {
  var aurora = document.querySelector('.aurora'), timer = 0;
  if (!aurora) return;
  var still = window.matchMedia('(max-width: 640px), (prefers-reduced-motion: reduce)');
  window.addEventListener('scroll', function () {
    if (still.matches) return;
    if (!timer) aurora.classList.add('paused');
    clearTimeout(timer);
    timer = setTimeout(function () { aurora.classList.remove('paused'); timer = 0; }, 180);
  }, { passive: true });
})();
