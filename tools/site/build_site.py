"""
Builds the Hazel website (pages/hazel-pages) into pages/hazel-pages/_build.

Each page body in src/ is wrapped in the shared head, nav and footer. Facts that change with
the app are read from the repository at build time rather than written into the pages:
the version from app/build.gradle.kts, the languages from locales_config.xml, the
"What's new" line from CHANGELOG.md, and the screenshots from fastlane.

Then every page is checked: plain punctuation only, no unfilled placeholder, and every
local link, image and #anchor resolves. Any failure exits non-zero, which fails CI.

    python tools/site/build_site.py
"""
import html as _html, pathlib, re, shutil, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent.parent
SITE = ROOT / 'pages' / 'hazel-pages'
SRC = SITE / 'src'
STATIC = SITE / 'static'
OUT = SITE / '_build'
SCREENS = ROOT / 'fastlane' / 'metadata' / 'android' / 'en-US' / 'images' / 'phoneScreenshots'
html_escape = _html.escape

MARK = ('<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" aria-hidden="true">'
        '<path d="M15.268 18.229c-1.979 2.678-2.969 4.017-3.892 3.734-.923-.283-.923-1.925-.923-5.21v-.31c0-1.184 0-1.777-.379-2.148l-.02-.02'
        'c-.387-.363-1.003-.363-2.236-.363-2.219 0-3.329 0-3.703-.673l-.019-.034c-.354-.683.289-1.552 1.574-3.291l3.062-4.143c1.979-2.678 '
        '2.969-4.017 3.892-3.734.923.283.923 1.925.923 5.21v.31c0 1.185 0 1.777.379 2.148l.02.02c.387.363 1.003.363 2.236.363 2.219 0 '
        '3.329 0 3.703.673l.019.034c.354.683-.289 1.552-1.574 3.291"/></svg>')

GITHUB = ('<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M12 .5a11.5 11.5 0 0 0-3.64 22.41c.58.1.79-.25.79-.56v-2'
          'c-3.2.7-3.88-1.36-3.88-1.36-.52-1.33-1.28-1.69-1.28-1.69-1.05-.71.08-.7.08-.7 1.16.08 1.77 1.19 1.77 1.19 1.03 1.77 2.7 1.26 '
          '3.36.96.1-.75.4-1.26.73-1.55-2.55-.29-5.24-1.28-5.24-5.69 0-1.26.45-2.29 1.19-3.09-.12-.29-.52-1.46.11-3.05 0 0 .97-.31 3.17 '
          '1.18a11 11 0 0 1 5.77 0c2.2-1.49 3.17-1.18 3.17-1.18.63 1.59.23 2.76.11 3.05.74.8 1.19 1.83 1.19 3.09 0 4.42-2.69 5.39-5.26 '
          '5.68.41.36.78 1.06.78 2.14v3.17c0 .31.21.67.8.56A11.5 11.5 0 0 0 12 .5Z"/></svg>')

THEME = ('<button class="icon-btn" type="button" data-theme-toggle aria-label="Theme">'
         '<svg class="theme-icon auto" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">'
         '<circle cx="12" cy="12" r="8"/><path d="M12 4a8 8 0 0 1 0 16Z" fill="currentColor" stroke="none"/></svg>'
         '<svg class="theme-icon light" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true">'
         '<circle cx="12" cy="12" r="4"/><path d="M12 2.5v2M12 19.5v2M4.6 4.6l1.4 1.4M18 18l1.4 1.4M2.5 12h2M19.5 12h2M4.6 19.4 6 18M18 6l1.4-1.4"/></svg>'
         '<svg class="theme-icon dark" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round" aria-hidden="true">'
         '<path d="M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5Z"/></svg></button>')

MENU = ('<button class="icon-btn menu-btn" type="button" aria-label="Menu" aria-expanded="false">'
        '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true">'
        '<path d="M4 8h16M4 16h16"/></svg></button>')

LINKS = [('whats-new.html', "What's new", 'whats-new'), ('index.html#features', 'Features', 'home'), ('guide.html', 'Guide', 'guide'), ('faq.html', 'FAQ', 'faq'),
         ('changelog.html', 'Changelog', 'changelog'), ('support.html', 'Support', 'support')]

RELEASES = 'https://github.com/SibtainOcn/Hazel/releases/latest'


def nav(active):
    links = ''.join(
        f'<a href="{h}"{" aria-current=\"page\"" if key == active and key != "home" else ""}>{t}</a>' for h, t, key in LINKS)
    return f'''<header class="nav">
  <div class="wrap">
    <a class="brand" href="index.html" aria-label="Hazel home">{MARK}Hazel</a>
    <nav class="nav-links" aria-label="Primary">{links}</nav>
    <div class="nav-end">
      {THEME}
      <a class="icon-btn gh-link" href="https://github.com/SibtainOcn/Hazel" aria-label="Hazel on GitHub">{GITHUB}</a>
      <a class="btn btn-primary" href="{RELEASES}">Download</a>
      {MENU}
    </div>
  </div>
  <nav class="mobile-menu" aria-label="Menu">{''.join(f'<a href="{h}">{t}</a>' for h, t, _ in LINKS)}<a href="https://github.com/SibtainOcn/Hazel">GitHub</a></nav>
</header>'''


FOOTER = f'''<footer>
  <div class="wrap">
    <div class="grid">
      <div class="about">
        <a class="brand" href="index.html">{MARK}Hazel</a>
        <p>A free, open source app for saving video and music to your Android phone.</p>
      </div>
      <div><h4>Product</h4><ul>
        <li><a href="index.html#features">Features</a></li>
        <li><a href="index.html#screens">Screenshots</a></li>
        <li><a href="changelog.html">Changelog</a></li>
      </ul></div>
      <div><h4>Help</h4><ul>
        <li><a href="guide.html">Guide</a></li>
        <li><a href="faq.html">FAQ</a></li>
        <li><a href="https://github.com/SibtainOcn/Hazel/issues">Report a problem</a></li>
      </ul></div>
      <div><h4>Get it</h4><ul>
        <li><a href="{RELEASES}">GitHub Releases</a></li>
        <li><a href="https://f-droid.org/packages/com.hazel.android/">F-Droid</a></li>
        <li><a href="index.html#install">Check your download</a></li>
      </ul></div>
      <div><h4>Project</h4><ul>
        <li><a href="https://github.com/SibtainOcn/Hazel">Source code</a></li>
        <li><a href="https://github.com/SibtainOcn/Hazel/blob/main/CONTRIBUTING.md">Contribute</a></li>
        <li><a href="support.html">Support Hazel</a></li>
      </ul></div>
    </div>
    <div class="legal mono"><span>Free software under GPL-3.0-or-later</span><span>Made by <a class="link" href="https://github.com/SibtainOcn" rel="author">SibtainOcn</a></span></div>
  </div>
</footer>'''




# ── Facts read from the repository, so the site never states an old version ──

NATIVE = {'en': 'English', 'es': 'Español', 'hi': 'हिन्दी', 'zh-rCN': '简体中文', 'pt-rBR': 'Português (Brasil)',
          'fr': 'Français', 'de': 'Deutsch', 'ru': 'Русский', 'ja': '日本語', 'in': 'Bahasa Indonesia',
          'it': 'Italiano', 'ar': 'العربية', 'uk': 'Українська', 'tr': 'Türkçe', 'ko': '한국어', 'pl': 'Polski'}
WORDS = ['zero', 'one', 'two', 'three', 'four', 'five', 'six', 'seven', 'eight', 'nine', 'ten', 'eleven',
         'twelve', 'thirteen', 'fourteen', 'fifteen', 'sixteen', 'seventeen', 'eighteen', 'nineteen', 'twenty']


def version():
    text = (ROOT / 'app' / 'build.gradle.kts').read_text(encoding='utf-8')
    found = re.search(r'versionName\s*=\s*"([^"]+)"', text)
    if not found:
        sys.exit('build_site: no versionName in app/build.gradle.kts')
    return found.group(1)


def languages():
    """
    English plus the languages the app offers, read from locales_config.xml. Not the
    values-* folders: a new language can be merged long before it is listed (at 80%),
    and the site should only promise what people can actually pick.
    """
    config = (ROOT / 'app' / 'src' / 'main' / 'res' / 'xml' / 'locales_config.xml').read_text(encoding='utf-8')
    # zh-CN in the config is the zh-rCN folder, which is how NATIVE names it.
    tags = [re.sub(r'-([A-Z]{2})$', r'-r\1', t)
            for t in re.findall(r'<locale\s+android:name="([^"]+)"', config)]
    codes = ['en'] + sorted(t for t in tags if t != 'en')
    return [NATIVE.get(c, c) for c in codes]


def whats_new(limit=6):
    """
    The lines the home page ticker flips through: RELEASE-NOTES.md bullets that open with a
    bold lead of three words or more, that lead alone. Shorter leads ("New setting") say
    nothing on their own, and a lead with a character the copy check bans is skipped.
    """
    text = (ROOT / 'RELEASE-NOTES.md').read_text(encoding='utf-8')
    items = []
    for lead in re.findall(r'^- \*\*(.+?)\*\*', text, re.M):
        lead = re.sub(r'[*`]', '', lead).strip().rstrip('.:')
        if len(lead.split()) >= 3 and not BANNED.search(lead) and lead not in items:
            items.append(lead)
        if len(items) == limit:
            break
    return items or ['See the latest changes']


def _md_inline(text):
    # Code first, so nothing inside backticks is read as bold or a link.
    parts = text.split('`')
    out = []
    for i, part in enumerate(parts):
        if i % 2 == 1 and i < len(parts) - 1:
            out.append(f'<code>{html_escape(part)}</code>')
            continue
        s = html_escape(('`' + part) if i % 2 == 1 else part)
        s = re.sub(r'\*\*(.+?)\*\*', r'<strong>\1</strong>', s)
        s = re.sub(r'\[([^\]]+)]\((https?://[^)\s"]+)\)', r'<a href="\2" rel="noopener">\1</a>', s)
        out.append(s)
    return ''.join(out)


def md_html(name):
    """
    A Markdown file at the repository root as HTML, by the same rules as the app's MarkdownLite: headings, bullet
    lists nested by indent, paragraphs, bold, inline code and links. Anything else is
    shown escaped as its text.
    """
    out, indents, para = [], [], []

    def flush():
        if ''.join(para).strip():
            out.append(f'<p>{_md_inline(" ".join(para).strip())}</p>')
        para.clear()

    def close(to=-1):
        while indents and indents[-1] > to:
            out.append('</li></ul>')
            indents.pop()

    # Start at the first version: a title and notes above it are for readers of the file,
    # and the page head already says what this is. A file with no version heading is
    # read from the top.
    text = (ROOT / name).read_text(encoding='utf-8')
    for raw in text[text.find('\n## ') + 1:].splitlines():
        line = raw.rstrip()
        trimmed = line.lstrip()
        indent = len(line) - len(trimmed)
        heading = re.match(r'(#{1,6})\s+(.*)', trimmed)
        bullet = re.match(r'[-*]\s+(.*)', trimmed)
        if not line:
            flush()
        elif heading and indent == 0:
            flush(); close()
            n = len(heading.group(1))
            out.append(f'<h{n}>{_md_inline(heading.group(2))}</h{n}>')
        elif bullet:
            flush()
            if not indents or indent > indents[-1]:
                out.append('<ul><li>')
                indents.append(indent)
            else:
                close(indent)
                out.append('</li><li>')
            out.append(_md_inline(bullet.group(1)))
        elif indents and indent > 0:
            out.append(' ' + _md_inline(trimmed))
        else:
            close()
            para.append(trimmed)
    flush(); close()
    return ''.join(out)


def page(name, title, description, active, extra_css, facts):
    body = (SRC / f'{name}.html').read_text(encoding='utf-8')
    for key, value in facts.items():
        body = body.replace('{{' + key + '}}', value)
    css = '<link rel="stylesheet" href="styles.css">' + (f'\n<link rel="stylesheet" href="{extra_css}">' if extra_css else '')
    return f'''<!doctype html>
<html lang="en" data-theme="light">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Hazel</title>
<meta name="description" content="{description}">
<meta name="theme-color" content="#f7f5f1">
<link rel="icon" href="assets/Haze.svg">
<script>try{{var t=localStorage.getItem('hazel-theme'),r=document.documentElement;if(t==='dark')r.setAttribute('data-theme','dark');else if(t==='auto'){{r.removeAttribute('data-theme');r.setAttribute('data-theme-auto','')}}}}catch(e){{}}</script>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Inter+Tight:wght@500;600;700&family=Inter:wght@400;500;600&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet">
{css}
</head>
<body>
{nav(active)}
<main>
{body}
</main>
{FOOTER}
<script src="site.js"></script>
</body>
</html>
'''


# ── Checks: anything that fails here fails the pull request ──

BANNED = re.compile('[—–→←…·›•]')


def check(pages):
    problems = []
    for name, html in pages.items():
        # These pages carry CHANGELOG.md and RELEASE-NOTES.md word for word, so their text
        # is not policed here.
        for ch in set() if name in ('changelog', 'whats-new') else set(BANNED.findall(html)):
            problems.append(f'{name}: plain copy only, found {ch!r} (U+{ord(ch):04X})')
        for left in set(re.findall(r'\{\{[^}]*\}\}', html)):
            problems.append(f'{name}: placeholder never filled: {left}')
        ids = set(re.findall(r'\sid="([^"]+)"', html))
        for ref in re.findall(r'(?:href|src)="([^"]+)"', html):
            if re.match(r'(https?:|mailto:|data:)', ref):
                continue
            path, _, anchor = ref.partition('#')
            target = OUT / path if path else OUT / f'{name}.html'
            if path and not target.exists():
                problems.append(f'{name}: broken link {ref}')
            elif anchor:
                other = ids if not path or path == f'{name}.html' else set(
                    re.findall(r'\sid="([^"]+)"', target.read_text(encoding='utf-8')))
                if anchor not in other:
                    problems.append(f'{name}: no #{anchor} in {path or name + ".html"}')
    return problems


def main():
    if OUT.exists():
        shutil.rmtree(OUT)
    (OUT / 'assets' / 'screens').mkdir(parents=True)
    for f in STATIC.iterdir():
        shutil.copy2(f, OUT / f.name)
    shutil.copy2(ROOT / 'assets' / 'Haze.svg', OUT / 'assets' / 'Haze.svg')
    for shot in SCREENS.iterdir():
        shutil.copy2(shot, OUT / 'assets' / 'screens' / shot.name)

    langs = languages()
    count = len(langs)
    word = WORDS[count] if count < len(WORDS) else str(count)
    facts = {
        'version': version(),
        'language_count': str(count),
        'language_words': word,
        'language_words_cap': word.capitalize(),
        'language_chips': ''.join(f'<span>{l}</span>' for l in langs),
        'whats_new': ''.join(f'<span class="line">{html_escape(t)}</span>' for t in whats_new()),
        'changelog': md_html('CHANGELOG.md'),
        'release_notes': md_html('RELEASE-NOTES.md'),
    }

    pages = {
        'index': page('index', 'Hazel: save video and music on Android',
                      'Hazel saves video and music from YouTube and more than 1000 other sites to your Android phone. Free, open source, no account, no ads.',
                      'home', 'home.css', facts),
        'guide': page('guide', 'Guide | Hazel', 'How to use Hazel: your first download, playlists, sharing from other apps, where files go and more.', 'guide', 'pages.css', facts),
        'faq': page('faq', 'FAQ | Hazel', 'Answers to common questions about Hazel.', 'faq', 'pages.css', facts),
        'whats-new': page('whats-new', "What's new | Hazel", 'The highlights of the latest Hazel release.', 'whats-new', 'pages.css', facts),
        'changelog': page('changelog', 'Changelog | Hazel', 'Every change to Hazel, newest first.', 'changelog', 'pages.css', facts),
        'support': page('support', 'Support | Hazel', 'Help keep Hazel free: sponsor, buy a coffee, translate, or spread the word.', 'support', 'pages.css', facts),
    }
    for name, html in pages.items():
        (OUT / f'{name}.html').write_text(html, encoding='utf-8')

    problems = check(pages)
    for p in problems:
        print('FAIL:', p)
    print(f'Built {len(pages)} pages into {OUT.relative_to(ROOT)} (version {facts["version"]}, '
          f'{count} languages, {len(whats_new())} what\'s new lines)')
    if problems:
        sys.exit(1)


if __name__ == '__main__':
    main()
