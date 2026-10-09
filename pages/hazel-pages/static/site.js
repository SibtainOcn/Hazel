// Hazel site: behaviour shared by every page. The theme itself is applied by a one-line
// script in each page's head, before anything paints, so there is no flash of the wrong one.
(function () {
  var root = document.documentElement;

  // ── Theme: auto, light, dark. Remembered on this device when storage allows. ──
  var order = ['light', 'dark', 'auto'];
  var labels = { auto: 'Theme: match system', light: 'Theme: light', dark: 'Theme: dark' };
  function current() { return root.getAttribute('data-theme') || 'auto'; }
  // Light unless the visitor picked something else.
  if (!root.hasAttribute('data-theme') && !root.hasAttribute('data-theme-auto')) root.setAttribute('data-theme', 'light');
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
    if (el.classList.contains('reveal')) el.classList.add('in');
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
