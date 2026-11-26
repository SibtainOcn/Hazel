"""
Draws the repository's star history as star-history.svg.

    python tools/stars/star_chart.py <out-dir> [owner/repo]

Reads the dated stargazer list from the GitHub API, authenticated with GH_TOKEN or
GITHUB_TOKEN when set. Run daily by .github/workflows/star-history.yml.
"""
import datetime as dt, json, os, pathlib, sys, urllib.request

REPO = sys.argv[2] if len(sys.argv) > 2 else 'SibtainOcn/Hazel'
W, H = 800, 400
LEFT, RIGHT, TOP, BOTTOM = 56, 32, 72, 84

# GitHub octicon star, as used by the website's star count.
STAR = ('M8 .25a.75.75 0 0 1 .673.418l1.882 3.815 4.21.612a.75.75 0 0 1 .416 1.279l-3.046 2.97.719 4.192a.751.751 0 0 1-1.088.791'
        'L8 12.347l-3.766 1.98a.75.75 0 0 1-1.088-.79l.72-4.194L.818 6.374a.75.75 0 0 1 .416-1.28l4.21-.611L7.327.668A.75.75 0 0 1 8 .25Z'
        'm0 2.445L6.615 5.5a.75.75 0 0 1-.564.41l-3.097.45 2.24 2.184a.75.75 0 0 1 .216.664l-.528 3.084 2.769-1.456a.75.75 0 0 1 .698 0'
        'l2.77 1.456-.53-3.084a.75.75 0 0 1 .216-.664l2.24-2.183-3.096-.45a.75.75 0 0 1-.564-.41L8 2.694Z')

# Dark theme; the star and count match the website's .gh-stars colours.
COLORS = dict(bg='#0b1416', fg='#e6f4f6', muted='#8aa3a8', grid='#1c2a2d', line='#00bcd4', fill='#00bcd4',
              count='#ededed', star='#a6a6a6', axis='#c3d5d8')


def fetch_dates():
    token = os.environ.get('GH_TOKEN') or os.environ.get('GITHUB_TOKEN')
    dates, page = [], 1
    while True:
        req = urllib.request.Request(
            f'https://api.github.com/repos/{REPO}/stargazers?per_page=100&page={page}',
            headers={'Accept': 'application/vnd.github.star+json', 'User-Agent': 'star-chart'})
        if token:
            req.add_header('Authorization', f'Bearer {token}')
        with urllib.request.urlopen(req, timeout=30) as r:
            batch = json.load(r)
        dates += [dt.datetime.fromisoformat(s['starred_at'].replace('Z', '+00:00')) for s in batch]
        if len(batch) < 100:
            return sorted(dates)
        page += 1


def nice_max(n):
    for step in (5, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10000):
        if n / step <= 5:
            return step * max(1, -(-n // step)), step
    return n, n


def smooth(points):
    """A Catmull-Rom curve through the points, as cubic Bezier segments."""
    d = f'M{points[0][0]:.1f},{points[0][1]:.1f}'
    for i in range(len(points) - 1):
        p0, p1, p2 = points[max(i - 1, 0)], points[i], points[i + 1]
        p3 = points[min(i + 2, len(points) - 1)]
        c1 = (p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6)
        c2 = (p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6)
        d += f' C{c1[0]:.1f},{c1[1]:.1f} {c2[0]:.1f},{c2[1]:.1f} {p2[0]:.1f},{p2[1]:.1f}'
    return d


def draw(dates):
    c = COLORS
    now = dt.datetime.now(dt.timezone.utc)
    start = dates[0].replace(hour=0, minute=0, second=0, microsecond=0)
    span = max((now - start).total_seconds(), 1)
    total = len(dates)
    top, step = nice_max(total)
    pw, ph = W - LEFT - RIGHT, H - TOP - BOTTOM
    x = lambda when: LEFT + (when - start).total_seconds() / span * pw
    y = lambda n: TOP + ph - n / top * ph

    # One point per day.
    points, count, day = [], 0, start
    while day <= now:
        nxt = day + dt.timedelta(days=1)
        while count < total and dates[count] < nxt:
            count += 1
        points.append((x(min(nxt, now)), y(count)))
        day = nxt
    points.insert(0, (x(start), y(0)))
    curve = smooth(points)
    base = y(0)
    last_x, last_y = points[-1]

    font = "font-family=\"Inter,system-ui,-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif\""
    total_label = f'{total:,}'
    total_w = len(total_label) * 14.5
    out = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W}" height="{H}" {font}>',
           f'<defs><linearGradient id="a" x1="0" y1="0" x2="0" y2="1">'
           f'<stop offset="0" stop-color="{c["fill"]}" stop-opacity=".28"/>'
           f'<stop offset="1" stop-color="{c["fill"]}" stop-opacity="0"/></linearGradient></defs>',
           f'<rect width="{W}" height="{H}" rx="20" fill="{c["bg"]}"/>',
           f'<text x="{LEFT}" y="45" font-size="20" font-weight="700" fill="{c["fg"]}">{REPO}</text>',
           f'<svg x="{W - RIGHT - total_w - 30:.1f}" y="27" width="22" height="22" viewBox="0 0 16 16"><path d="{STAR}" fill="{c["star"]}"/></svg>',
           f'<text x="{W - RIGHT}" y="45" text-anchor="end" font-size="24" font-weight="600" fill="{c["count"]}" style="font-variant-numeric:tabular-nums">{total_label}</text>']
    for n in range(0, top + 1, step):
        out.append(f'<line x1="{LEFT}" x2="{W - RIGHT}" y1="{y(n):.1f}" y2="{y(n):.1f}" stroke="{c["grid"]}" stroke-width="1"/>')
        out.append(f'<text x="{LEFT - 10}" y="{y(n) + 4:.1f}" text-anchor="end" font-size="12" fill="{c["muted"]}">{n:,}</text>')
    month = dt.datetime(start.year, start.month, 1, tzinfo=dt.timezone.utc)
    months = []
    while month <= now:
        months.append(month)
        month = dt.datetime(month.year + month.month // 12, month.month % 12 + 1, 1, tzinfo=dt.timezone.utc)
    # Full month names on the x axis, at most six; overlapping labels are skipped.
    every = max(1, -(-len(months) // 6))
    last_at = -1e9
    for i, m in enumerate(months):
        if i % every:
            continue
        at = max(x(m), LEFT)
        anchor = 'start' if at - LEFT < 40 else 'end' if W - RIGHT - at < 40 else 'middle'
        if at - last_at < 110:
            continue
        label = m.strftime('%B') if m.month != 1 or i == 0 else m.strftime('%B %Y')
        out.append(f'<text x="{at:.1f}" y="{base + 28:.1f}" text-anchor="{anchor}" font-size="14" font-weight="500" fill="{c["axis"]}">{label}</text>')
        last_at = at
    out.append(f'<path d="{curve} L{last_x:.1f},{base:.1f} L{x(start):.1f},{base:.1f} Z" fill="url(#a)"/>')
    out.append(f'<path d="{curve}" fill="none" stroke="{c["line"]}" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>')
    out.append(f'<circle cx="{last_x:.1f}" cy="{last_y:.1f}" r="5" fill="{c["line"]}"/>')
    out.append(f'<text x="{LEFT}" y="{H - 18}" font-size="13" font-weight="600" fill="{c["muted"]}">Star history</text>')
    out.append(f'<text x="{W - RIGHT}" y="{H - 18}" text-anchor="end" font-size="13" fill="{c["muted"]}">Updated {now:%d %b %Y}</text>')
    out.append('</svg>')
    return '\n'.join(out)


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    out = pathlib.Path(sys.argv[1])
    out.mkdir(parents=True, exist_ok=True)
    dates = fetch_dates()
    if not dates:
        sys.exit('star_chart: no stars listed')
    (out / 'star-history.svg').write_text(draw(dates), encoding='utf-8')
    print(f'star_chart: {len(dates)} stars, {dates[0]:%Y-%m-%d} to now, written to {out}')


if __name__ == '__main__':
    main()
