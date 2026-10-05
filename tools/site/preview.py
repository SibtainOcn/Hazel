"""Builds the website and serves it locally: python tools/site/preview.py [port]"""
import functools, http.server, subprocess, sys, pathlib, webbrowser

HERE = pathlib.Path(__file__).resolve().parent
OUT = HERE.parents[1] / 'pages' / 'hazel-pages' / '_build'


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8000
    if subprocess.run([sys.executable, str(HERE / 'build_site.py')]).returncode:
        sys.exit('preview: build failed')
    handler = functools.partial(http.server.SimpleHTTPRequestHandler, directory=str(OUT))
    with http.server.ThreadingHTTPServer(('localhost', port), handler) as server:
        url = f'http://localhost:{port}/index.html'
        print(f'Serving {url}  (Ctrl+C to stop)')
        webbrowser.open(url)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass


if __name__ == '__main__':
    main()
